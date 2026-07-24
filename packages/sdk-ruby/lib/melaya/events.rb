# frozen_string_literal: true

require "uri"
require "net/http"
require "json"
require "openssl"
require "set"
require "thread"

require_relative "errors"

module Melaya
  # Platform real-time events via Socket.IO v4 at /api/v1/events.
  #
  # Implements the Engine.IO v4 handshake and long-poll transport (packets
  # batched with the 0x1e record separator; server pings answered with a
  # POSTed pong) and the Socket.IO v4 packet framing, mirroring the TS
  # events.ts client.
  #
  # Room semantics (matching the server):
  #   - run:<runId>          — pipeline run events
  #   - project:<project>    — all events in a project (runs + CRUD)
  #   - hitl:user:<userId>   — HITL approval events (joined automatically by server)
  #
  # Thread safety: all public methods are safe to call from any thread.
  # Callbacks are invoked on a dedicated background reader thread — do not
  # block in a callback.
  #
  # @example
  #   events = Melaya::Events.new(api_key: ENV["MELAYA_API_KEY"])
  #
  #   # Subscribe to a run
  #   unsub = events.on_run_update("run-123") { |e| puts e["event_type"] }
  #
  #   # Subscribe to HITL approvals
  #   events.on_hitl_approval { |e| puts "HITL: #{e["type"]}" }
  #
  #   # Clean up
  #   unsub.call          # remove one subscription
  #   events.leave_run("run-123")
  #   events.close
  #
  # NOTE: Requires Ruby standard library 'net/http' with persistent HTTP/1.1.
  # For production use the connection is kept alive between polls.
  class Events
    # Engine.IO v4 packet type characters
    EIO_OPEN    = "0"
    EIO_CLOSE   = "1"
    EIO_PING    = "2"
    EIO_PONG    = "3"
    EIO_MESSAGE = "4"
    EIO_UPGRADE = "5"
    EIO_NOOP    = "6"

    # Socket.IO v4 packet types (numeric, follows the EIO_MESSAGE prefix)
    SIO_CONNECT    = 0
    SIO_DISCONNECT = 1
    SIO_EVENT      = 2
    SIO_ACK        = 3
    SIO_ERROR      = 4

    RECONNECT_BASE_DELAY   = 2  # seconds — first reconnect wait
    RECONNECT_MAX_DELAY    = 30 # seconds — cap for exponential backoff

    # @param api_key [String] mk_* key
    # @param base_url [String]
    # @param verify_ssl [Boolean]
    def initialize(api_key:, base_url: HttpClient::DEFAULT_BASE_URL, verify_ssl: true)
      raise ArgumentError, "Melaya: TLS certificate verification cannot be disabled." unless verify_ssl
      @_tok       = api_key.freeze
      @base_url   = base_url.sub(/\/$/, "")
      @verify_ssl = true

      @sid            = nil
      @closed         = false
      @listeners      = Hash.new { |h, k| h[k] = [] }  # event → [callable]
      @joined_rooms   = Set.new                        # rejoin-on-reconnect list
      @room_listeners = Hash.new { |h, k| h[k] = [] }  # room  → [[event, callable]]
      @mutex          = Mutex.new

      _connect_async
    end

    # ── Subscription helpers ───────────────────────────────────────────────────

    # Subscribe to run events for a specific pipeline run.
    # Joins the run:<runId> Socket.IO room automatically. When the payload
    # carries a +runId+, events for other runs are filtered out so multiple
    # per-run subscriptions never receive cross-room events.
    # @param run_id [String]
    # @yield [Hash] run push event
    # @return [Proc] call to unsubscribe
    def on_run_update(run_id, &block)
      join_room("run:#{run_id}")
      on("pushEvent", room: "run:#{run_id}", &run_filter(run_id, block))
    end

    # Subscribe to init-phase progress events for a run.
    # @param run_id [String]
    # @yield [Hash]
    # @return [Proc]
    def on_init_phase(run_id, &block)
      join_room("run:#{run_id}")
      on("pushInitPhase", room: "run:#{run_id}", &run_filter(run_id, block))
    end

    # Subscribe to all events in a project (runs + pipeline CRUD). When the
    # payload carries a +projectId+/+project+, events for other projects are
    # filtered out.
    # @param project [String]
    # @yield [Hash]
    # @return [Proc]
    def on_project_event(project, &block)
      join_room("project:#{project}")
      on("pushEvent", room: "project:#{project}", &project_filter(project, block))
    end

    # Subscribe to HITL approval invalidation events.
    # The server joins authenticated sockets to the hitl:user:<userId> room
    # on connect — no explicit join needed.
    # @yield [Hash]
    # @return [Proc]
    def on_hitl_approval(&block)
      on("pushHitlApprovals", &block)
    end

    # Subscribe to pipeline-created events within a project room.
    # @param project [String]
    # @yield [Hash]
    # @return [Proc]
    def on_pipeline_created(project, &block)
      join_room("project:#{project}")
      on("pipelineCreated", room: "project:#{project}", &project_filter(project, block))
    end

    # Subscribe to pipeline-updated events within a project room.
    # @param project [String]
    # @yield [Hash]
    # @return [Proc]
    def on_pipeline_updated(project, &block)
      join_room("project:#{project}")
      on("pipelineUpdated", room: "project:#{project}", &project_filter(project, block))
    end

    # Subscribe to pipeline-deleted events within a project room.
    # @param project [String]
    # @yield [Hash]
    # @return [Proc]
    def on_pipeline_deleted(project, &block)
      join_room("project:#{project}")
      on("pipelineDeleted", room: "project:#{project}", &project_filter(project, block))
    end

    # Leave a run room, remove its listeners, and drop it from the
    # rejoin-on-reconnect list.
    # @param run_id [String]
    def leave_run(run_id)
      leave_room("run:#{run_id}")
    end

    # Leave a project room, remove its listeners, and drop it from the
    # rejoin-on-reconnect list.
    # @param project [String]
    def leave_project(project)
      leave_room("project:#{project}")
    end

    # Close the Socket.IO connection and release all listeners.
    def close
      @mutex.synchronize do
        @closed = true
        @joined_rooms.clear
        @room_listeners.clear
        @listeners.clear
      end
      @poll_thread&.kill
    end

    private

    # ── Internal event bus ─────────────────────────────────────────────────────

    # Register a listener. When +room:+ is given the listener is also indexed
    # by room so leave_room can remove it.
    def on(event, room: nil, &block)
      @mutex.synchronize do
        @listeners[event] << block
        @room_listeners[room] << [event, block] if room
      end
      lambda do
        @mutex.synchronize do
          @listeners[event].delete(block)
          @room_listeners[room].delete([event, block]) if room
        end
      end
    end

    # Wrap +block+ so it only fires for the given run. Payloads that carry a
    # runId (RunPushEvent) are matched exactly; payloads without one pass
    # through unchanged.
    def run_filter(run_id, block)
      lambda do |payload|
        rid = payload.is_a?(Hash) ? (payload["runId"] || payload["run_id"]) : nil
        block.call(payload) if rid.nil? || rid.to_s == run_id.to_s
      end
    end

    # Wrap +block+ so it only fires for the given project. Payloads that carry
    # a projectId/project are matched exactly; payloads without one pass
    # through unchanged.
    def project_filter(project, block)
      lambda do |payload|
        pid = payload.is_a?(Hash) ? (payload["projectId"] || payload["project"]) : nil
        block.call(payload) if pid.nil? || pid.to_s == project.to_s
      end
    end

    def emit(event, payload)
      listeners = @mutex.synchronize { @listeners[event].dup }
      listeners.each do |l|
        l.call(payload) rescue nil
      end
    end

    # ── Room management ────────────────────────────────────────────────────────

    # Map a logical room string to the correct Socket.IO emit call.
    #
    # Server contract (/api/v1/events, server/src/index.ts):
    #   "run:<id>"      → emit "joinRunRoom",     bare runId (without "run:" prefix)
    #   "project:<name>"→ emit "joinProjectRoom", bare project name
    #   anything else   → emit "joinRunRoom" with the full string (safe fallback)
    #
    # leave(room) always emits "leaveRoom" with the FULL room string.
    def join_room(room)
      already = @mutex.synchronize do
        next true if @joined_rooms.include?(room)
        @joined_rooms.add(room)
        false
      end
      return if already
      _emit_join(room) if @sid
    end

    # Remove the room from the rejoin-on-reconnect list AND detach every
    # listener that was registered for it, then tell the server to leave.
    def leave_room(room)
      @mutex.synchronize do
        @joined_rooms.delete(room)
        (@room_listeners.delete(room) || []).each do |(event, block)|
          @listeners[event].delete(block)
        end
      end
      sio_emit("leaveRoom", room) if @sid
    end

    def replay_joins
      rooms = @mutex.synchronize { @joined_rooms.to_a }
      rooms.each { |room| _emit_join(room) }
    end

    # Emit the correct server event for a join, mapping the room string to the
    # appropriate event name and argument per the server protocol.
    def _emit_join(room)
      if room.start_with?("run:")
        sio_emit("joinRunRoom", room[4..])
      elsif room.start_with?("project:")
        sio_emit("joinProjectRoom", room[8..])
      else
        # Safe fallback for unknown room prefixes (e.g. future room types).
        sio_emit("joinRunRoom", room)
      end
    end

    # ── Packet dispatch ────────────────────────────────────────────────────────

    def handle_packet(raw)
      return if raw.nil? || raw.empty?
      type = raw[0]
      case type
      when EIO_PING
        poll_post(EIO_PONG)
      when EIO_MESSAGE
        handle_sio_packet(raw[1..])
      end
    end

    def handle_sio_packet(sio_raw)
      return if sio_raw.nil? || sio_raw.empty?
      sio_type = sio_raw[0].to_i
      return unless sio_type == SIO_EVENT
      begin
        args = JSON.parse(sio_raw[1..])
        return unless args.is_a?(Array) && args.size >= 2
        event, payload = args[0], args[1]
        emit(event, payload)
      rescue JSON::ParserError
        # ignore malformed packets
      end
    end

    # Engine.IO v4 polling batches packets separated by the 0x1e record
    # separator. Split on it and dispatch each packet individually.
    def parse_poll_response(text)
      text.split("\x1e").each { |packet| handle_packet(packet) }
    end

    # ── Socket.IO framing ──────────────────────────────────────────────────────

    def sio_connect_packet
      auth = JSON.generate({ "token" => @_tok })
      "#{EIO_MESSAGE}#{SIO_CONNECT}#{auth}"
    end

    def sio_emit(event, *args)
      packet = "#{EIO_MESSAGE}#{SIO_EVENT}#{JSON.generate([event, *args])}"
      poll_post(packet)
    end

    # ── HTTP polling transport ─────────────────────────────────────────────────

    def poll_uri(extra = {})
      params = {
        "EIO"       => "4",
        "transport" => "polling"
      }
      params["sid"] = @sid if @sid
      params.merge!(extra)
      uri = URI.parse("#{@base_url}/api/v1/events/")
      uri.query = URI.encode_www_form(params)
      uri
    end

    def http_for(uri)
      h = Net::HTTP.new(uri.host, uri.port)
      h.use_ssl     = uri.scheme == "https"
      h.verify_mode = OpenSSL::SSL::VERIFY_PEER
      h.open_timeout = 15
      h.read_timeout = 60
      h
    end

    def poll_get
      uri = poll_uri
      req = Net::HTTP::Get.new(uri)
      req["Authorization"] = "Bearer #{@_tok}"
      resp = http_for(uri).request(req)
      code = resp.code.to_i
      if code >= 400
        # HTTP 400 = "Session ID unknown": the sid is dead. Raising here makes
        # _connect_loop perform a full re-handshake (and replay room joins)
        # instead of silently polling a dead session forever.
        raise MelayaError.new("MelayaEvents: poll failed (HTTP #{code})", status: code)
      end
      resp.body.to_s
    end

    def poll_post(body_str)
      uri = poll_uri
      req = Net::HTTP::Post.new(uri)
      req["Authorization"] = "Bearer #{@_tok}"
      req["Content-Type"]  = "text/plain;charset=UTF-8"
      req.body = body_str
      http_for(uri).request(req)
    end

    # ── Connection lifecycle ───────────────────────────────────────────────────

    def _connect_async
      @poll_thread = Thread.new do
        Thread.current.abort_on_exception = false
        _connect_loop
      end
      @poll_thread.name = "melaya-events-poll"
    end

    def _connect_loop
      reconnect_attempt = 0
      loop do
        break if @mutex.synchronize { @closed }
        begin
          _handshake
          reconnect_attempt = 0  # reset on successful connection
          _poll_loop
        rescue StandardError
          # reconnect after bounded exponential backoff with ±25 % jitter
        end
        break if @mutex.synchronize { @closed }
        reconnect_attempt += 1
        base   = [RECONNECT_BASE_DELAY * (2**(reconnect_attempt - 1)), RECONNECT_MAX_DELAY].min.to_f
        jitter = base * 0.25 * (rand - 0.5) * 2
        sleep([base + jitter, 0.5].max)
        @sid = nil
      end
    end

    def _handshake
      # Step 1: Engine.IO open handshake
      text = poll_get
      json_start = text.index("{")
      raise MelayaError.new("MelayaEvents: unexpected EIO handshake", status: 0) if json_start.nil?
      data = JSON.parse(text[json_start..])
      @sid = data["sid"]

      # Step 2: Socket.IO connect packet with auth
      poll_post(sio_connect_packet)

      # Replay room joins
      replay_joins
    end

    # Continuous long-poll: the GET hangs server-side until data (or a ping)
    # arrives, so we re-issue it immediately — never sleep between polls, or
    # events queue up (and pings go unanswered) for the whole interval.
    # Errors (including dead-session HTTP 400) propagate to _connect_loop,
    # which backs off and re-handshakes.
    def _poll_loop
      loop do
        break if @mutex.synchronize { @closed }
        text = poll_get
        parse_poll_response(text) unless text.empty?
      end
    end
  end
end
