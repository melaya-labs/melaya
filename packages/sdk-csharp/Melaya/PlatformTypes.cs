using System.Text.Json;
using System.Text.Json.Serialization;

namespace Melaya;

// ── Common ────────────────────────────────────────────────────────────────────

/// <summary>Pipeline / eval run status.</summary>
public enum RunStatus
{
    Pending, Queued, Running, Done, Error, Killed, Cancelled, Unknown
}

// ── Auth ──────────────────────────────────────────────────────────────────────

/// <summary>Login request body.</summary>
public sealed class LoginRequest
{
    [JsonPropertyName("username")] public required string Username { get; init; }
    [JsonPropertyName("password")] public required string Password { get; init; }
}

/// <summary>Login response — includes JWT and optional MFA challenge.</summary>
public sealed class LoginResult
{
    [JsonPropertyName("ok")]             public bool?   Ok             { get; set; }
    [JsonPropertyName("token")]          public string? Token          { get; set; }
    [JsonPropertyName("mfaRequired")]    public bool?   MfaRequired    { get; set; }
    [JsonPropertyName("challengeToken")] public string? ChallengeToken { get; set; }
    [JsonExtensionData]                  public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>User profile as returned by /auth/me.</summary>
public sealed class UserProfile
{
    [JsonPropertyName("id")]           public string? Id           { get; set; }
    [JsonPropertyName("username")]     public string? Username     { get; set; }
    [JsonPropertyName("email")]        public string? Email        { get; set; }
    [JsonPropertyName("tier")]         public string? Tier         { get; set; }
    [JsonPropertyName("role")]         public string? Role         { get; set; }
    [JsonPropertyName("capabilities")] public List<string>? Capabilities { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>MFA setup response — contains QR code / TOTP secret.</summary>
public sealed class MfaSetupResult
{
    [JsonPropertyName("ok")]     public bool?   Ok     { get; set; }
    [JsonPropertyName("qr")]     public string? Qr     { get; set; }
    [JsonPropertyName("secret")] public string? Secret { get; set; }
    [JsonExtensionData]          public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>MFA status.</summary>
public sealed class MfaStatus
{
    [JsonPropertyName("enabled")]  public bool?   Enabled  { get; set; }
    [JsonPropertyName("verified")] public bool?   Verified { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Projects ──────────────────────────────────────────────────────────────────

/// <summary>An agent project.</summary>
public sealed class Project
{
    [JsonPropertyName("id")]          public string? Id          { get; set; }
    [JsonPropertyName("name")]        public string? Name        { get; set; }
    [JsonPropertyName("description")] public string? Description { get; set; }
    [JsonPropertyName("ownerId")]     public string? OwnerId     { get; set; }
    [JsonPropertyName("role")]        public string? Role        { get; set; }
    [JsonPropertyName("createdAt")]   public string? CreatedAt   { get; set; }
    [JsonPropertyName("updatedAt")]   public string? UpdatedAt   { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for creating a project.</summary>
public sealed class ProjectCreateRequest
{
    [JsonPropertyName("name")]        public required string Name        { get; init; }
    [JsonPropertyName("description")] public string? Description { get; init; }
}

// ── Pipeline runs ─────────────────────────────────────────────────────────────

/// <summary>A pipeline run record.</summary>
public sealed class PipelineRun
{
    [JsonPropertyName("id")]           public string? Id           { get; set; }
    [JsonPropertyName("runId")]        public string? RunId        { get; set; }
    [JsonPropertyName("pipelineName")] public string? PipelineName { get; set; }
    [JsonPropertyName("project")]      public string? Project      { get; set; }
    [JsonPropertyName("status")]       public string? Status       { get; set; }
    [JsonPropertyName("startedAt")]    public string? StartedAt    { get; set; }
    [JsonPropertyName("finishedAt")]   public string? FinishedAt   { get; set; }
    [JsonPropertyName("durationMs")]   public long?   DurationMs   { get; set; }
    [JsonPropertyName("triggeredBy")]  public string? TriggeredBy  { get; set; }
    [JsonPropertyName("error")]        public string? Error        { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Overview summary.</summary>
public sealed class OverviewSummary
{
    [JsonPropertyName("totalRuns")]   public int?    TotalRuns   { get; set; }
    [JsonPropertyName("activeRuns")]  public int?    ActiveRuns  { get; set; }
    [JsonPropertyName("failedRuns")]  public int?    FailedRuns  { get; set; }
    [JsonPropertyName("costUsd")]     public double? CostUsd     { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Pipeline count by status.</summary>
public sealed class PipelineCountByStatus
{
    [JsonPropertyName("pending")] public int? Pending { get; set; }
    [JsonPropertyName("running")] public int? Running { get; set; }
    [JsonPropertyName("done")]    public int? Done    { get; set; }
    [JsonPropertyName("error")]   public int? Error   { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Pipeline schedule ─────────────────────────────────────────────────────────

/// <summary>A pipeline schedule record.</summary>
public sealed class PipelineSchedule
{
    [JsonPropertyName("project")]      public string? Project      { get; set; }
    [JsonPropertyName("pipelineName")] public string? PipelineName { get; set; }
    [JsonPropertyName("cron")]         public string? Cron         { get; set; }
    [JsonPropertyName("paused")]       public bool?   Paused       { get; set; }
    [JsonPropertyName("lastRunAt")]    public string? LastRunAt    { get; set; }
    [JsonPropertyName("nextRunAt")]    public string? NextRunAt    { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for upserting a pipeline schedule.</summary>
public sealed class PipelineScheduleUpsertRequest
{
    [JsonPropertyName("cron")]   public required string Cron   { get; init; }
    [JsonPropertyName("config")] public Dictionary<string, JsonElement>? Config { get; init; }
}

// ── Traces ────────────────────────────────────────────────────────────────────

/// <summary>A pipeline trace.</summary>
public sealed class Trace
{
    [JsonPropertyName("id")]         public string? Id         { get; set; }
    [JsonPropertyName("runId")]      public string? RunId      { get; set; }
    [JsonPropertyName("name")]       public string? Name       { get; set; }
    [JsonPropertyName("startedAt")]  public string? StartedAt  { get; set; }
    [JsonPropertyName("finishedAt")] public string? FinishedAt { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Paginated trace listing — matches the <c>{ data: { list, total, page, pageSize } }</c>
/// envelope returned by <c>getTraces</c> (agentStudio.ts).
/// </summary>
public sealed class TraceListPage
{
    [JsonPropertyName("list")]     public List<Trace>? List     { get; set; }
    [JsonPropertyName("total")]    public int?         Total    { get; set; }
    [JsonPropertyName("page")]     public int?         Page     { get; set; }
    [JsonPropertyName("pageSize")] public int?         PageSize { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Outer envelope for <c>getTraces</c>: <c>{ data: TraceListPage }</c>.</summary>
internal sealed class TraceListEnvelope
{
    [JsonPropertyName("data")] public TraceListPage? Data { get; set; }
}

/// <summary>Result from deleting traces for a run.</summary>
public sealed class DeleteTracesResult
{
    [JsonPropertyName("deletedSpans")]       public int?  DeletedSpans      { get; set; }
    [JsonPropertyName("requestedTraces")]    public int?  RequestedTraces   { get; set; }
    [JsonExtensionData]                      public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Trace statistics.</summary>
public sealed class TraceStats
{
    [JsonPropertyName("traceId")]    public string? TraceId    { get; set; }
    [JsonPropertyName("totalSpans")] public int?    TotalSpans { get; set; }
    [JsonPropertyName("durationMs")] public long?   DurationMs { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Tool-call audit ───────────────────────────────────────────────────────────

/// <summary>Pagination cursor for <c>PipelinesApi.ProjectToolCallsAsync</c>.</summary>
public sealed class ToolCallCursor
{
    [JsonPropertyName("beforeCreatedAt")] public string? BeforeCreatedAt { get; set; }
    [JsonPropertyName("beforeId")]        public string? BeforeId        { get; set; }
}

/// <summary>Paginated tool-call audit listing for a project.</summary>
public sealed class ToolCallListResult
{
    [JsonPropertyName("items")]      public List<RunToolCall>? Items      { get; set; }
    [JsonPropertyName("nextCursor")] public ToolCallCursor?    NextCursor { get; set; }
    [JsonPropertyName("capped")]     public bool?              Capped     { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>One faceted count (tool name or agent name → occurrences).</summary>
public sealed class ToolCallFacetCount
{
    [JsonPropertyName("name")]  public string? Name  { get; set; }
    [JsonPropertyName("count")] public int?    Count { get; set; }
    [JsonExtensionData]         public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Facet counts for filtering the tool-call audit log.</summary>
public sealed class ToolCallFacets
{
    [JsonPropertyName("tools")]  public List<ToolCallFacetCount>? Tools  { get; set; }
    [JsonPropertyName("agents")] public List<string>?             Agents { get; set; }
    [JsonExtensionData]          public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── HITL ──────────────────────────────────────────────────────────────────────

/// <summary>A pending HITL approval request.</summary>
public class HitlApproval
{
    [JsonPropertyName("requestId")]    public string? RequestId    { get; set; }
    [JsonPropertyName("runId")]        public string? RunId        { get; set; }
    [JsonPropertyName("pipelineName")] public string? PipelineName { get; set; }
    [JsonPropertyName("project")]      public string? Project      { get; set; }
    [JsonPropertyName("agentId")]      public string? AgentId      { get; set; }
    [JsonPropertyName("toolName")]     public string? ToolName     { get; set; }
    [JsonPropertyName("toolInput")]    public Dictionary<string, JsonElement>? ToolInput { get; set; }
    [JsonPropertyName("requestedAt")]  public string? RequestedAt  { get; set; }
    [JsonPropertyName("expiresAt")]    public string? ExpiresAt    { get; set; }
    [JsonPropertyName("status")]       public string? Status       { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>HITL approval history record — extends HitlApproval with decision info.</summary>
public sealed class HitlApprovalHistory : HitlApproval
{
    [JsonPropertyName("decision")]   public string? Decision   { get; set; }
    [JsonPropertyName("decidedAt")]  public string? DecidedAt  { get; set; }
    [JsonPropertyName("decidedBy")]  public string? DecidedBy  { get; set; }
}

/// <summary>Body for a single HITL decision.</summary>
public sealed class HitlDecideRequest
{
    [JsonPropertyName("comment")] public string? Comment { get; init; }
}

/// <summary>Bulk HITL decision body.</summary>
public sealed class HitlBulkDecideRequest
{
    [JsonPropertyName("requestIds")] public required List<string> RequestIds { get; init; }
    [JsonPropertyName("decision")]   public required string Decision         { get; init; }
    [JsonPropertyName("comment")]    public string? Comment                  { get; init; }
}

/// <summary>Bulk decide result.</summary>
public sealed class HitlBulkDecideResult
{
    [JsonPropertyName("ok")]       public bool?         Ok       { get; set; }
    [JsonPropertyName("approved")] public List<string>? Approved { get; set; }
    [JsonPropertyName("rejected")] public List<string>? Rejected { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Tool-call statistics for a run.</summary>
public sealed class RunToolStats
{
    [JsonPropertyName("runId")]      public string? RunId      { get; set; }
    [JsonPropertyName("totalCalls")] public int?    TotalCalls { get; set; }
    [JsonPropertyName("approved")]   public int?    Approved   { get; set; }
    [JsonPropertyName("rejected")]   public int?    Rejected   { get; set; }
    [JsonPropertyName("pending")]    public int?    Pending    { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>A message within a pipeline run.</summary>
public sealed class RunMessage
{
    [JsonPropertyName("id")]        public string? Id        { get; set; }
    [JsonPropertyName("runId")]     public string? RunId     { get; set; }
    [JsonPropertyName("agentId")]   public string? AgentId   { get; set; }
    [JsonPropertyName("role")]      public string? Role      { get; set; }
    [JsonPropertyName("content")]   public string? Content   { get; set; }
    [JsonPropertyName("timestamp")] public string? Timestamp { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>A tool call within a pipeline run.</summary>
public sealed class RunToolCall
{
    [JsonPropertyName("id")]         public string? Id         { get; set; }
    [JsonPropertyName("runId")]      public string? RunId      { get; set; }
    [JsonPropertyName("agentId")]    public string? AgentId    { get; set; }
    [JsonPropertyName("toolName")]   public string? ToolName   { get; set; }
    [JsonPropertyName("input")]      public Dictionary<string, JsonElement>? Input { get; set; }
    [JsonPropertyName("output")]     public JsonElement? Output    { get; set; }
    [JsonPropertyName("status")]     public string? Status     { get; set; }
    [JsonPropertyName("durationMs")] public long?   DurationMs { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Credentials ───────────────────────────────────────────────────────────────

/// <summary>A stored credential entry.</summary>
public sealed class Credential
{
    [JsonPropertyName("service")] public string? Service { get; set; }
    [JsonPropertyName("label")]   public string? Label   { get; set; }
    [JsonPropertyName("keyName")] public string? KeyName { get; set; }
    [JsonPropertyName("masked")]  public string? Masked  { get; set; }
    [JsonPropertyName("scope")]   public string? Scope   { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>A connected third-party service.</summary>
public sealed class ConnectedService
{
    [JsonPropertyName("service")]   public string? Service   { get; set; }
    [JsonPropertyName("connected")] public bool?   Connected { get; set; }
    [JsonPropertyName("label")]     public string? Label     { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Operator profile for agent context injection.</summary>
public sealed class OperatorProfile
{
    [JsonPropertyName("name")]    public string? Name    { get; set; }
    [JsonPropertyName("persona")] public string? Persona { get; set; }
    [JsonPropertyName("context")] public string? Context { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for storing a credential.</summary>
public sealed class CredentialSetRequest
{
    [JsonPropertyName("value")] public required string Value { get; init; }
    [JsonPropertyName("key")]   public string? Key           { get; init; }
    [JsonPropertyName("label")] public string? Label         { get; init; }
}

/// <summary>Result of a credential test.</summary>
public sealed class CredentialTestResult
{
    [JsonPropertyName("ok")]      public bool?   Ok      { get; set; }
    [JsonPropertyName("message")] public string? Message { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Result of starting Telegram QR-code login (<c>CredentialsApi.TelegramQrStartAsync</c>).</summary>
public sealed class TelegramQrStartResult
{
    /// <summary>Opaque poll handle, prefixed <c>tgauth_</c>. Pass to <c>TelegramQrPollAsync</c>.</summary>
    [JsonPropertyName("handle")] public string? Handle { get; set; }
    [JsonExtensionData]          public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for <c>CredentialsApi.WhatsappSignupExchangeAsync</c>.</summary>
public sealed class WhatsappSignupExchangeRequest
{
    [JsonPropertyName("code")]          public required string Code          { get; init; }
    [JsonPropertyName("phoneNumberId")] public required string PhoneNumberId { get; init; }
    [JsonPropertyName("wabaId")]        public required string WabaId        { get; init; }
    [JsonPropertyName("project")]       public string?         Project       { get; init; }
}

// ── AI models ─────────────────────────────────────────────────────────────────

/// <summary>An available AI model across configured providers.</summary>
public sealed class AiModel
{
    [JsonPropertyName("id")]                   public string?      Id                   { get; set; }
    [JsonPropertyName("name")]                 public string?      Name                 { get; set; }
    [JsonPropertyName("provider")]             public string?      Provider             { get; set; }
    [JsonPropertyName("contextWindow")]        public int?         ContextWindow        { get; set; }
    [JsonPropertyName("inputPricePerMToken")]  public double?      InputPricePerMToken  { get; set; }
    [JsonPropertyName("outputPricePerMToken")] public double?      OutputPricePerMToken { get; set; }
    [JsonPropertyName("capabilities")]         public List<string>? Capabilities        { get; set; }
    [JsonExtensionData]                        public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Runner tokens ─────────────────────────────────────────────────────────────

/// <summary>A runner token record (masked).</summary>
public sealed class RunnerToken
{
    [JsonPropertyName("id")]          public string? Id          { get; set; }
    [JsonPropertyName("maskedToken")] public string? MaskedToken { get; set; }
    [JsonPropertyName("createdAt")]   public string? CreatedAt   { get; set; }
    [JsonPropertyName("lastSeen")]    public string? LastSeen    { get; set; }
    [JsonPropertyName("label")]       public string? Label       { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for creating a runner token.</summary>
public sealed class RunnerTokenCreateRequest
{
    [JsonPropertyName("label")] public string? Label { get; init; }
}

/// <summary>Result of minting a runner token — plaintext shown once.</summary>
public sealed class RunnerTokenCreateResult
{
    [JsonPropertyName("id")]    public string? Id    { get; set; }
    [JsonPropertyName("token")] public string? Token { get; set; }
    [JsonExtensionData]         public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Billing ───────────────────────────────────────────────────────────────────

/// <summary>Stripe subscription status.</summary>
public sealed class BillingSubscription
{
    [JsonPropertyName("tier")]                public string? Tier               { get; set; }
    [JsonPropertyName("status")]              public string? Status             { get; set; }
    [JsonPropertyName("currentPeriodEnd")]    public string? CurrentPeriodEnd   { get; set; }
    [JsonPropertyName("cancelAtPeriodEnd")]   public bool?   CancelAtPeriodEnd  { get; set; }
    [JsonExtensionData]                       public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>A pricing plan.</summary>
public sealed class BillingPlan
{
    [JsonPropertyName("id")]         public string?      Id         { get; set; }
    [JsonPropertyName("name")]       public string?      Name       { get; set; }
    [JsonPropertyName("priceId")]    public string?      PriceId    { get; set; }
    [JsonPropertyName("monthlyUsd")] public double?      MonthlyUsd { get; set; }
    [JsonPropertyName("features")]   public List<string>? Features  { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Stripe Checkout session URL.</summary>
public sealed class BillingCheckoutResult
{
    [JsonPropertyName("url")] public string? Url { get; set; }
    [JsonExtensionData]       public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Stripe Customer Portal URL.</summary>
public sealed class BillingPortalResult
{
    [JsonPropertyName("url")] public string? Url { get; set; }
    [JsonExtensionData]       public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Phone ─────────────────────────────────────────────────────────────────────

/// <summary>A paired phone device.</summary>
public sealed class PhoneDevice
{
    [JsonPropertyName("id")]         public string? Id        { get; set; }
    [JsonPropertyName("label")]      public string? Label     { get; set; }
    [JsonPropertyName("platform")]   public string? Platform  { get; set; }
    [JsonPropertyName("created_at")] public string? CreatedAt { get; set; }
    [JsonPropertyName("last_seen")]  public string? LastSeen  { get; set; }
    [JsonPropertyName("expires_at")] public string? ExpiresAt { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Phone pairing initiation result.</summary>
public sealed class PhonePairResult
{
    [JsonPropertyName("code")]             public string? Code             { get; set; }
    [JsonPropertyName("expiresInSeconds")] public int?    ExpiresInSeconds { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>An installed app on a paired phone.</summary>
public sealed class PhoneApp
{
    [JsonPropertyName("package")]     public string? Package { get; set; }
    [JsonPropertyName("label")]       public string? Label   { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

internal sealed class PhoneDevicesResult
{
    [JsonPropertyName("devices")] public List<PhoneDevice>? Devices { get; set; }
}

internal sealed class PhoneAppsPayload
{
    [JsonPropertyName("apps")] public List<PhoneApp>? Apps { get; set; }
}

internal sealed class PhoneAppsResult
{
    [JsonPropertyName("result")] public PhoneAppsPayload? Result { get; set; }
}

// ── Team ──────────────────────────────────────────────────────────────────────

/// <summary>A project team member.</summary>
public sealed class TeamMember
{
    [JsonPropertyName("userId")]   public string? UserId   { get; set; }
    [JsonPropertyName("username")] public string? Username { get; set; }
    [JsonPropertyName("email")]    public string? Email    { get; set; }
    [JsonPropertyName("role")]     public string? Role     { get; set; }
    [JsonPropertyName("joinedAt")] public string? JoinedAt { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Invite link result.</summary>
public sealed class TeamInviteResult
{
    [JsonPropertyName("ok")]         public bool?   Ok         { get; set; }
    [JsonPropertyName("inviteLink")] public string? InviteLink { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Templates ─────────────────────────────────────────────────────────────────

/// <summary>A user pipeline template.</summary>
public sealed class UserTemplate
{
    [JsonPropertyName("id")]          public string? Id          { get; set; }
    [JsonPropertyName("name")]        public string? Name        { get; set; }
    [JsonPropertyName("description")] public string? Description { get; set; }
    [JsonPropertyName("category")]    public string? Category    { get; set; }
    [JsonPropertyName("visibility")]  public string? Visibility  { get; set; }
    [JsonPropertyName("validated")]   public bool?   Validated   { get; set; }
    [JsonPropertyName("payload")]     public Dictionary<string, JsonElement>? Payload { get; set; }
    [JsonPropertyName("createdAt")]   public string? CreatedAt   { get; set; }
    [JsonPropertyName("updatedAt")]   public string? UpdatedAt   { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for saving a new template.</summary>
public sealed class TemplateSaveRequest
{
    [JsonPropertyName("name")]        public required string Name        { get; init; }
    [JsonPropertyName("description")] public string? Description { get; init; }
    [JsonPropertyName("category")]    public string? Category    { get; init; }
    [JsonPropertyName("payload")]     public required Dictionary<string, JsonElement> Payload { get; init; }
}

/// <summary>Body for updating a template.</summary>
public sealed class TemplateUpdateRequest
{
    [JsonPropertyName("name")]        public string? Name        { get; init; }
    [JsonPropertyName("description")] public string? Description { get; init; }
    [JsonPropertyName("category")]    public string? Category    { get; init; }
    [JsonPropertyName("payload")]     public Dictionary<string, JsonElement>? Payload { get; init; }
}

/// <summary>Template assignment — links a template to a user or project.</summary>
public sealed class TemplateAssignment
{
    [JsonPropertyName("templateId")]  public string? TemplateId  { get; set; }
    [JsonPropertyName("targetType")]  public string? TargetType  { get; set; }
    [JsonPropertyName("targetId")]    public string? TargetId    { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Assignment target for <c>AssignAsync</c> / <c>UnassignAsync</c>.
/// Set exactly one of <see cref="UserId"/> or <see cref="ProjectId"/> (both UUIDs).
/// </summary>
public sealed class TemplateAssignRequest
{
    [JsonPropertyName("userId")]    public string? UserId    { get; init; }
    [JsonPropertyName("projectId")] public string? ProjectId { get; init; }
}

// ── Assistant profile ─────────────────────────────────────────────────────────

/// <summary>Assistant onboarding profile.</summary>
public sealed class AssistantProfile
{
    [JsonPropertyName("name")]        public string?      Name        { get; set; }
    [JsonPropertyName("goals")]       public List<string>? Goals      { get; set; }
    [JsonPropertyName("context")]     public string?      Context     { get; set; }
    [JsonPropertyName("preferences")] public Dictionary<string, JsonElement>? Preferences { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Evals ─────────────────────────────────────────────────────────────────────

/// <summary>An eval run record.</summary>
public sealed class EvalRun
{
    [JsonPropertyName("id")]        public string? Id        { get; set; }
    [JsonPropertyName("status")]    public string? Status    { get; set; }
    [JsonPropertyName("summary")]   public Dictionary<string, JsonElement>? Summary { get; set; }
    [JsonPropertyName("startedAt")] public string? StartedAt { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Aggregate eval summary.</summary>
public sealed class EvalSummary
{
    [JsonPropertyName("totalRuns")] public int?    TotalRuns { get; set; }
    [JsonPropertyName("passRate")]  public double? PassRate  { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Field-level patch applied by <c>EvalsApi.EditCrewMemoryEntryAsync</c>. Omitted fields are left unchanged.</summary>
public sealed class CrewMemoryPatch
{
    [JsonPropertyName("topic")]   public string?       Topic   { get; init; }
    [JsonPropertyName("content")] public string?       Content { get; init; }
    [JsonPropertyName("tags")]    public List<string>? Tags    { get; init; }
}

/// <summary>Body for <c>EvalsApi.EditCrewMemoryEntryAsync</c>.</summary>
public sealed class CrewMemoryEditRequest
{
    [JsonPropertyName("pipeline")] public required string Pipeline { get; init; }
    [JsonPropertyName("entryId")]  public required string EntryId  { get; init; }
    [JsonPropertyName("project")]  public string?         Project  { get; init; }
    [JsonPropertyName("patch")]    public required CrewMemoryPatch Patch { get; init; }
}

// ── Bug reports ───────────────────────────────────────────────────────────────

/// <summary>A user bug report.</summary>
public sealed class BugReport
{
    [JsonPropertyName("id")]          public string? Id          { get; set; }
    [JsonPropertyName("title")]       public string? Title       { get; set; }
    [JsonPropertyName("description")] public string? Description { get; set; }
    [JsonPropertyName("status")]      public string? Status      { get; set; }
    [JsonPropertyName("createdAt")]   public string? CreatedAt   { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for submitting a bug report.</summary>
public sealed class BugReportCreateRequest
{
    [JsonPropertyName("title")]       public required string Title       { get; init; }
    [JsonPropertyName("description")] public required string Description { get; init; }
    [JsonPropertyName("metadata")]    public Dictionary<string, JsonElement>? Metadata { get; init; }
}

// ── Project connectors ────────────────────────────────────────────────────────

/// <summary>Body for setting a project-scoped connector credential.</summary>
public sealed class ProjectConnectorSetRequest
{
    [JsonPropertyName("value")] public required string Value { get; init; }
    [JsonPropertyName("key")]   public string? Key           { get; init; }
    [JsonPropertyName("label")] public string? Label         { get; init; }
}

/// <summary>
/// Result of starting a database connectivity probe (<c>ConnectorsApi.DbTestStartAsync</c> /
/// <c>CredentialsApi.DbTestStartAsync</c>) — the probe runs on the user's own runner.
/// </summary>
public sealed class DbTestStartResult
{
    [JsonPropertyName("sessionId")] public string? SessionId { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Generic results ───────────────────────────────────────────────────────────

/// <summary>Generic <c>{ ok: true }</c> response.</summary>
public sealed class BoolResult
{
    [JsonPropertyName("ok")]      public bool? Ok      { get; set; }
    [JsonPropertyName("success")] public bool? Success { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Simple URL redirect result (checkout / portal).</summary>
public sealed class UrlResult
{
    [JsonPropertyName("url")] public string? Url { get; set; }
    [JsonExtensionData]       public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Named project for share targets.</summary>
public sealed class ProjectShareTarget
{
    [JsonPropertyName("id")]   public string? Id   { get; set; }
    [JsonPropertyName("name")] public string? Name { get; set; }
    [JsonExtensionData]        public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Backtest optimization (platform plane) ────────────────────────────────────

/// <summary>Start a parameter sweep optimization job.</summary>
public sealed class OptimizeStartRequest
{
    [JsonPropertyName("strategyId")]    public string? StrategyId    { get; init; }
    [JsonPropertyName("strategyType")]  public string? StrategyType  { get; init; }
    [JsonPropertyName("exchange")]      public string? Exchange      { get; init; }
    [JsonPropertyName("symbol")]        public string? Symbol        { get; init; }
    [JsonPropertyName("timeframe")]     public string? Timeframe     { get; init; }
    [JsonPropertyName("paramRanges")]   public Dictionary<string, JsonElement>? ParamRanges { get; init; }
    [JsonPropertyName("method")]        public string? Method        { get; init; }
    [JsonExtensionData]                 public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>An optimization sweep run.</summary>
public sealed class OptimizeRun
{
    [JsonPropertyName("optRunId")] public string? OptRunId { get; set; }
    [JsonPropertyName("status")]   public string? Status   { get; set; }
    [JsonPropertyName("progress")] public double? Progress { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Credits ───────────────────────────────────────────────────────────────────

/// <summary>Credit balance and transaction history.</summary>
public sealed class CreditBalance
{
    [JsonPropertyName("balance")]      public double?              Balance      { get; set; }
    [JsonPropertyName("transactions")] public List<JsonElement>?   Transactions { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Strategies team/bulk ──────────────────────────────────────────────────────

/// <summary>Lightweight strategy summary.</summary>
public sealed class StrategySummary
{
    [JsonPropertyName("strategyId")]   public string? StrategyId   { get; set; }
    [JsonPropertyName("name")]         public string? Name         { get; set; }
    [JsonPropertyName("strategyType")] public string? StrategyType { get; set; }
    [JsonPropertyName("status")]       public string? Status       { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Pipeline lifecycle ────────────────────────────────────────────────────────

/// <summary>Response from <c>ListPipelinesAsync</c> — wraps the <c>pipelines</c> array.</summary>
public sealed class PipelineListResult
{
    [JsonPropertyName("pipelines")] public List<JsonElement>? Pipelines { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for creating a pipeline definition.</summary>
public sealed class PipelineCreateRequest
{
    [JsonPropertyName("name")]        public required string Name        { get; init; }
    [JsonPropertyName("project")]     public required string Project     { get; init; }
    [JsonPropertyName("description")] public string?         Description { get; init; }
    /// <summary>Additional config fields (agents, tools, env, etc.) are passed through via extension data.</summary>
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Config { get; init; }
}

/// <summary>Body for updating a pipeline definition.</summary>
public sealed class PipelineUpdateRequest
{
    [JsonPropertyName("config")]  public required JsonElement Config  { get; init; }
    [JsonPropertyName("project")] public required string      Project { get; init; }
}

/// <summary>Body for enqueuing a pipeline run.</summary>
public sealed class PipelineRunRequest
{
    [JsonPropertyName("project")] public string? Project { get; init; }

    /// <summary>
    /// Used ONLY for the tier/capacity check at enqueue time — it does NOT decide where the
    /// run actually executes. The execution target is decided by the pipeline's stored config
    /// (local model providers, <c>force_local_runner</c>).
    /// </summary>
    [JsonPropertyName("executionTarget")] public string? ExecutionTarget { get; init; }

    [JsonPropertyName("studio_url")] public string? StudioUrl { get; init; }

    /// <summary>
    /// Per-run env var overrides layered over the caller's stored credentials.
    /// <c>MEL_*</c> / <c>MELAYA_*</c> keys are stripped server-side and can never be overridden.
    /// </summary>
    [JsonPropertyName("env_overrides")] public Dictionary<string, string>? EnvOverrides { get; init; }

    /// <summary>Run-time brief and/or keyed values delivered to the pipeline's first agent.</summary>
    [JsonPropertyName("run_inputs")] public PipelineRunInputs? RunInputs { get; init; }
}

/// <summary>
/// Run-time inputs for one pipeline run (<see cref="PipelineRunRequest.RunInputs"/>).
/// A value in <see cref="Values"/> may be a plain JSON value, or reference a file via
/// <c>{ file_id }</c> (see <c>PipelinesApi.UploadRunFileAsync</c>), a remote URL via
/// <c>{ url }</c> (≤ 25 MB), or inline bytes via <c>{ base64, name }</c> (≤ 7 MB).
/// </summary>
public sealed class PipelineRunInputs
{
    [JsonPropertyName("brief")]  public string? Brief  { get; init; }
    [JsonPropertyName("values")] public Dictionary<string, object>? Values { get; init; }
}

/// <summary>Response from <c>RunAsync</c> — run ID, queued flag, and an optional <c>run_inputs</c> echo.</summary>
public sealed class PipelineRunResult
{
    [JsonPropertyName("run_id")]     public string?      RunId      { get; set; }
    [JsonPropertyName("queued")]     public bool?        Queued     { get; set; }
    [JsonPropertyName("run_inputs")] public JsonElement? RunInputs  { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Inputs recorded for a run (<c>PipelinesApi.RunInputsAsync</c>): the brief, the keyed
/// values, and metadata for any uploaded files (download the bytes with
/// <c>PipelinesApi.RunInputFileAsync</c> by index).
/// </summary>
public sealed class PipelineRunInputsResult
{
    [JsonPropertyName("brief")]  public string? Brief  { get; set; }
    [JsonPropertyName("values")] public Dictionary<string, JsonElement>? Values { get; set; }
    [JsonPropertyName("files")]  public List<JsonElement>? Files { get; set; }
    [JsonExtensionData]          public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Response envelope for <c>PipelinesApi.RunActiveAsync</c>.</summary>
internal sealed class PipelineRunActiveResult
{
    [JsonPropertyName("active")] public bool? Active { get; set; }
}

/// <summary>Result of uploading a run-input file (<c>PipelinesApi.UploadRunFileAsync</c>).</summary>
public sealed class RunFileUploadResult
{
    /// <summary>Single-use file ID, valid for 24 hours. Reference it from a <c>run_inputs</c> value as <c>{ file_id }</c>.</summary>
    [JsonPropertyName("file_id")] public string? FileId { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Response from <c>RunIdsAsync</c> — list of run IDs for a pipeline.</summary>
public sealed class PipelineRunIdsResult
{
    [JsonPropertyName("run_ids")] public List<string>? RunIds { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Structured usage and spend totals for a pipeline run.</summary>
public sealed class PipelineRunCost
{
    [JsonPropertyName("run_id")]           public string? RunId          { get; set; }
    [JsonPropertyName("total_usd")]        public double? TotalUsd       { get; set; }
    [JsonPropertyName("total_in_tokens")]  public long?   TotalInTokens  { get; set; }
    [JsonPropertyName("total_out_tokens")] public long?   TotalOutTokens { get; set; }
    [JsonPropertyName("limit_usd")]        public double? LimitUsd       { get; set; }
    [JsonPropertyName("summary")]          public JsonElement? Summary   { get; set; }
    [JsonExtensionData]                    public Dictionary<string, JsonElement>? Extra { get; set; }
}
/// <summary>Status of a specific pipeline run.</summary>
public sealed class PipelineRunStatus
{
    [JsonPropertyName("runId")]           public string? RunId           { get; set; }
    [JsonPropertyName("status")]          public string? Status          { get; set; }
    [JsonPropertyName("createdAt")]       public string? CreatedAt       { get; set; }
    [JsonPropertyName("executionTarget")] public string? ExecutionTarget { get; set; }
    [JsonPropertyName("cost")]            public PipelineRunCost? Cost   { get; set; }
    [JsonExtensionData]                   public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Body for instantiating a pipeline template.</summary>
public sealed class InstantiateTemplateRequest
{
    [JsonPropertyName("name")]      public required string Name      { get; init; }
    [JsonPropertyName("project")]   public required string Project   { get; init; }
    [JsonPropertyName("overrides")] public Dictionary<string, JsonElement>? Overrides { get; init; }
}

/// <summary>Response from <c>InstantiateTemplateAsync</c> — the new pipeline definition.</summary>
public sealed class InstantiateTemplateResult
{
    [JsonPropertyName("pipeline")] public JsonElement? Pipeline { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Connector tools (ConnectorToolsApi) ─────────────────────────────────────────
//
// The REST tool-call surface at /api/v1/private/connector-tools/* — the same
// discover/call surface the Melaya MCP server exposes. Not to be confused with
// ConnectorsApi / CredentialsApi, which store project/user credentials.

/// <summary>Read/write tool counts for one connected service (<c>ConnectorToolsApi.ServicesAsync</c>).</summary>
public sealed class ConnectorToolCounts
{
    [JsonPropertyName("readTools")]  public int ReadTools  { get; set; }
    [JsonPropertyName("writeTools")] public int WriteTools { get; set; }
}

/// <summary>Connected services and their tool counts (<c>ConnectorToolsApi.ServicesAsync</c>).</summary>
public sealed class ConnectorToolServicesResult
{
    [JsonPropertyName("services")]  public List<string>? Services { get; set; }
    [JsonPropertyName("builtIn")]   public string? BuiltIn        { get; set; }
    [JsonPropertyName("toolCounts")] public Dictionary<string, ConnectorToolCounts>? ToolCounts { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>One parameter of a connector tool, as described by <see cref="ConnectorToolInfo"/>.</summary>
public sealed class ConnectorToolParamInfo
{
    [JsonPropertyName("type")]        public string?      Type        { get; set; }
    [JsonPropertyName("required")]    public bool?        Required    { get; set; }
    [JsonPropertyName("default")]     public JsonElement? Default     { get; set; }
    [JsonPropertyName("description")] public string?       Description { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// One connector tool's shape (<c>ConnectorToolsApi.SearchAsync</c> / <c>DescribeAsync</c>).
/// <c>MovesMoney</c> tools are always refused by <c>CallAsync</c>, under every approval mode.
/// </summary>
public sealed class ConnectorToolInfo
{
    [JsonPropertyName("name")]        public string? Name        { get; set; }
    [JsonPropertyName("service")]     public string? Service     { get; set; }
    [JsonPropertyName("description")] public string? Description { get; set; }
    [JsonPropertyName("readOnly")]    public bool?   ReadOnly    { get; set; }
    [JsonPropertyName("movesMoney")]  public bool?   MovesMoney  { get; set; }
    [JsonPropertyName("params")]      public Dictionary<string, ConnectorToolParamInfo>? Params { get; set; }
    [JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Result of <c>ConnectorToolsApi.SearchAsync</c> — tools ranked by relevance to <c>Query</c>.</summary>
public sealed class ConnectorToolSearchResult
{
    [JsonPropertyName("query")]    public string? Query    { get; set; }
    [JsonPropertyName("services")] public List<string>? Services { get; set; }
    [JsonPropertyName("tools")]    public List<ConnectorToolInfo>? Tools { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Result of testing a stored connector credential (<c>ConnectorToolsApi.TestAsync</c>).</summary>
public sealed class ConnectorToolTestResult
{
    [JsonPropertyName("service")] public string? Service { get; set; }
    [JsonPropertyName("success")] public bool?   Success { get; set; }
    [JsonPropertyName("message")] public string? Message { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Result of starting to connect a service (<c>ConnectorToolsApi.ConnectAsync</c>). <c>Kind</c> is
/// one of <c>oauth</c>, <c>oauth_unavailable</c>, <c>interactive_login</c>, or <c>api_key</c>.
/// Never carries a secret.
/// </summary>
public sealed class ConnectorToolConnectResult
{
    [JsonPropertyName("service")]          public string? Service          { get; set; }
    [JsonPropertyName("kind")]             public string? Kind             { get; set; }
    [JsonPropertyName("authorizationUrl")] public string? AuthorizationUrl { get; set; }
    [JsonPropertyName("connectUrl")]       public string? ConnectUrl       { get; set; }
    [JsonPropertyName("message")]          public string? Message          { get; set; }
    [JsonExtensionData]                    public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Result of <c>ConnectorToolsApi.CallAsync</c>. <c>Status</c> is <c>done</c> (reads, and writes
/// run with <c>approval: "none"</c> — 200) or <c>pending_approval</c> (a staged write — 202;
/// this is success, not an error). For a staged write, poll <c>CallStatusAsync(RequestId)</c>.
/// </summary>
public sealed class ConnectorToolCallResult
{
    [JsonPropertyName("status")]    public string? Status    { get; set; }
    [JsonPropertyName("tool")]      public string? Tool      { get; set; }
    [JsonPropertyName("readOnly")]  public bool?   ReadOnly  { get; set; }
    [JsonPropertyName("result")]    public string? Result    { get; set; }
    [JsonPropertyName("requestId")] public string? RequestId { get; set; }
    [JsonPropertyName("message")]   public string? Message   { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Outcome of a staged write, from <c>ConnectorToolsApi.CallStatusAsync</c> or
/// <c>CallAndWaitAsync</c>. <c>Status</c> is <c>pending</c> / <c>running</c> (not resolved yet),
/// <c>expired</c> (the approval card timed out), <c>done</c> (<c>Ok</c> tells success/failure), or
/// <c>rejected</c> (the user declined it in the Melaya app — see <c>Reason</c>).
/// </summary>
public sealed class ConnectorToolCallStatusResult
{
    [JsonPropertyName("requestId")] public string? RequestId { get; set; }
    [JsonPropertyName("tool")]      public string? Tool      { get; set; }
    [JsonPropertyName("status")]    public string? Status    { get; set; }
    [JsonPropertyName("ok")]        public bool?   Ok        { get; set; }
    [JsonPropertyName("result")]    public string? Result    { get; set; }
    [JsonPropertyName("error")]     public string? Error     { get; set; }
    [JsonPropertyName("reason")]    public string? Reason    { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── Event triggers ────────────────────────────────────────────────────────────

/// <summary>
/// One event trigger (<c>TriggersApi.ListAsync</c> / <c>GetAsync</c>). <c>Kind</c> is
/// <c>webhook</c>, <c>wss</c>, <c>engine</c>, <c>poll</c> or <c>push</c>. <c>Config</c> is the
/// saved trigger config, returned as-is. Signing secrets are never included.
/// </summary>
public sealed class TriggerRecord
{
    [JsonPropertyName("id")]                  public string?      Id                  { get; set; }
    [JsonPropertyName("publicId")]            public string?      PublicId            { get; set; }
    [JsonPropertyName("name")]                public string?      Name                { get; set; }
    [JsonPropertyName("kind")]                public string?      Kind                { get; set; }
    [JsonPropertyName("project")]             public string?      Project             { get; set; }
    [JsonPropertyName("pipelineName")]        public string?      PipelineName        { get; set; }
    [JsonPropertyName("enabled")]             public bool?        Enabled             { get; set; }
    [JsonPropertyName("pausedReason")]        public string?      PausedReason        { get; set; }
    [JsonPropertyName("signingScheme")]       public string?      SigningScheme       { get; set; }
    [JsonPropertyName("sourceId")]            public string?      SourceId            { get; set; }
    [JsonPropertyName("config")]              public JsonElement? Config              { get; set; }
    [JsonPropertyName("maxEventsPerMin")]     public int?         MaxEventsPerMin     { get; set; }
    [JsonPropertyName("maxRunsPerDay")]       public int?         MaxRunsPerDay       { get; set; }
    [JsonPropertyName("maxConcurrentRuns")]   public int?         MaxConcurrentRuns   { get; set; }
    [JsonPropertyName("consecutiveFailures")] public int?         ConsecutiveFailures { get; set; }
    [JsonPropertyName("lastEventAt")]         public string?      LastEventAt         { get; set; }
    [JsonPropertyName("createdAt")]           public string?      CreatedAt           { get; set; }
    [JsonPropertyName("updatedAt")]           public string?      UpdatedAt           { get; set; }
    [JsonPropertyName("webhookUrl")]          public string?      WebhookUrl          { get; set; }
    [JsonPropertyName("projectAccess")]       public bool?        ProjectAccess       { get; set; }
    [JsonExtensionData]                       public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Count and latency percentiles for one verdict in <see cref="TriggerStats"/>.</summary>
public sealed class TriggerVerdictStats
{
    [JsonPropertyName("n")]   public int?    N   { get; set; }
    [JsonPropertyName("p50")] public double? P50 { get; set; }
    [JsonPropertyName("p95")] public double? P95 { get; set; }
    [JsonExtensionData]       public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Delivery counts by verdict over a window (<c>TriggersApi.StatsAsync</c>). <c>Filtered</c> counts
/// events the prefilter dropped (null when the counter is unavailable). <c>Sampled</c> is true when
/// the window held more receipts than the stats sample.
/// </summary>
public sealed class TriggerStats
{
    [JsonPropertyName("hours")]     public int?  Hours     { get; set; }
    [JsonPropertyName("byVerdict")] public Dictionary<string, TriggerVerdictStats>? ByVerdict { get; set; }
    [JsonPropertyName("filtered")]  public int?  Filtered  { get; set; }
    [JsonPropertyName("sampled")]   public bool? Sampled   { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Result of a dry-run test event (<c>TriggersApi.TestAsync</c>). The action never executes.</summary>
public sealed class TriggerTestResult
{
    [JsonPropertyName("accepted")] public bool?   Accepted { get; set; }
    [JsonPropertyName("eventId")]  public string? EventId  { get; set; }
    [JsonPropertyName("reason")]   public string? Reason   { get; set; }
    [JsonExtensionData]            public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// One live trigger event (<c>TriggersApi.EventsAsync</c>). <c>Verdict</c> is one of
/// <c>accepted</c>, <c>duplicate</c>, <c>rejected</c>, <c>rate_limited</c>, <c>filtered</c>,
/// <c>decided</c>, <c>dispatched</c>, <c>skipped</c>, <c>failed</c>. <c>DeliveryId</c> is null for
/// events that wrote no receipt. <c>At</c> is epoch milliseconds.
/// </summary>
public sealed class TriggerLiveEvent
{
    [JsonPropertyName("triggerId")]     public string? TriggerId     { get; set; }
    [JsonPropertyName("deliveryId")]    public string? DeliveryId    { get; set; }
    [JsonPropertyName("eventId")]       public string? EventId       { get; set; }
    [JsonPropertyName("source")]        public string? Source        { get; set; }
    [JsonPropertyName("verdict")]       public string? Verdict       { get; set; }
    [JsonPropertyName("action")]        public string? Action        { get; set; }
    [JsonPropertyName("runId")]         public string? RunId         { get; set; }
    [JsonPropertyName("detail")]        public string? Detail        { get; set; }
    [JsonPropertyName("latencyMs")]     public double? LatencyMs     { get; set; }
    [JsonPropertyName("resultExcerpt")] public string? ResultExcerpt { get; set; }
    [JsonPropertyName("at")]            public long?   At            { get; set; }
    [JsonExtensionData]                 public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>How long live trigger events are kept.</summary>
public sealed class TriggerLiveEventsRetention
{
    [JsonPropertyName("maxEvents")] public int? MaxEvents { get; set; }
    [JsonPropertyName("ttlSec")]    public int? TtlSec    { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Recent live trigger events, newest first (<c>TriggersApi.EventsAsync</c>).</summary>
public sealed class TriggerLiveEventsResult
{
    [JsonPropertyName("events")]    public List<TriggerLiveEvent>?     Events    { get; set; }
    [JsonPropertyName("scanned")]   public int?                        Scanned   { get; set; }
    [JsonPropertyName("retention")] public TriggerLiveEventsRetention? Retention { get; set; }
    [JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Runtime state of a poll trigger (<c>TriggersApi.PollStatusAsync</c>). <c>Synced</c> is false when
/// the trigger has no runtime row yet (see <c>PollSyncAsync</c>). Times are ISO 8601 strings.
/// </summary>
public sealed class TriggerPollState
{
    [JsonPropertyName("synced")]               public bool?   Synced               { get; set; }
    [JsonPropertyName("status")]               public string? Status               { get; set; }
    [JsonPropertyName("lastError")]            public string? LastError            { get; set; }
    [JsonPropertyName("lastPolledAt")]         public string? LastPolledAt         { get; set; }
    [JsonPropertyName("nextPollAt")]           public string? NextPollAt           { get; set; }
    [JsonPropertyName("armed")]                public bool?   Armed                { get; set; }
    [JsonPropertyName("baselinePending")]      public bool?   BaselinePending      { get; set; }
    [JsonPropertyName("seenCount")]            public int?    SeenCount            { get; set; }
    [JsonPropertyName("itemsPublished")]       public int?    ItemsPublished       { get; set; }
    [JsonPropertyName("consecutiveErrors")]    public int?    ConsecutiveErrors    { get; set; }
    [JsonPropertyName("requestedIntervalSec")] public int?    RequestedIntervalSec { get; set; }
    [JsonPropertyName("effectiveIntervalSec")] public int?    EffectiveIntervalSec { get; set; }
    [JsonPropertyName("tierFloorSec")]         public int?    TierFloorSec         { get; set; }
    [JsonExtensionData]                        public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>One item a dry poll found, with a short redacted preview.</summary>
public sealed class TriggerPollItem
{
    [JsonPropertyName("id")]      public string? Id      { get; set; }
    [JsonPropertyName("preview")] public string? Preview { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Result of <c>TriggersApi.PollTestAsync</c> (<c>Dry == true</c>) or <c>PollNowAsync</c>
/// (<c>Dry == false</c>, only <c>Queued</c> is set). A dry poll either succeeds
/// (<c>Ok == true</c>: <c>Found</c>, <c>Baseline</c>, <c>WouldPublish</c>, <c>Items</c>,
/// <c>SamplePayload</c>) or fails (<c>Ok == false</c>, <c>Error</c> code). On the first poll after
/// arming, <c>Baseline</c> is true and nothing would be published.
/// </summary>
public sealed class TriggerPollTestResult
{
    [JsonPropertyName("dry")]           public bool?                  Dry           { get; set; }
    [JsonPropertyName("ok")]            public bool?                  Ok            { get; set; }
    [JsonPropertyName("error")]         public string?                Error         { get; set; }
    [JsonPropertyName("found")]         public int?                   Found         { get; set; }
    [JsonPropertyName("baseline")]      public bool?                  Baseline      { get; set; }
    [JsonPropertyName("wouldPublish")]  public int?                   WouldPublish  { get; set; }
    [JsonPropertyName("items")]         public List<TriggerPollItem>? Items         { get; set; }
    [JsonPropertyName("samplePayload")] public JsonElement?           SamplePayload { get; set; }
    [JsonPropertyName("queued")]        public bool?                  Queued        { get; set; }
    [JsonExtensionData]                 public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>
/// Trigger presets available to the caller (<c>TriggersApi.PresetsAsync</c>). Each preset and the
/// <c>Beta</c> block are returned as raw JSON.
/// </summary>
public sealed class TriggerPresetsResult
{
    [JsonPropertyName("tier")]         public string?            Tier         { get; set; }
    [JsonPropertyName("tierFloorSec")] public int?               TierFloorSec { get; set; }
    [JsonPropertyName("presets")]      public List<JsonElement>? Presets      { get; set; }
    [JsonPropertyName("beta")]         public JsonElement?       Beta         { get; set; }
    [JsonExtensionData]                public Dictionary<string, JsonElement>? Extra { get; set; }
}

