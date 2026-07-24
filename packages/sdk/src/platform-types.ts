/**
 * Shared types for the Melaya Platform + Agents API surface.
 *
 * These complement the trading-plane types in types.ts and cover the
 * agentic/pipeline plane: projects, pipeline runs, HITL approvals,
 * credentials/connectors, AI models, assistant profile, billing, and
 * team management.
 */

// ── Common ───────────────────────────────────────────────────────────────────

export type RunStatus =
  | "pending"
  | "queued"
  | "running"
  | "done"
  | "error"
  | "killed"
  | "cancelled"
  | (string & {});

// ── Projects ─────────────────────────────────────────────────────────────────

export interface Project {
  id: string;
  name: string;
  description?: string;
  ownerId?: string;
  role?: string;
  createdAt?: string;
  updatedAt?: string;
  [k: string]: unknown;
}

export interface ProjectCreate {
  name: string;
  description?: string;
}

// ── Pipeline runs (overview plane) ───────────────────────────────────────────

export interface PipelineRun {
  id: string;
  runId?: string;
  pipelineName?: string;
  project?: string;
  status: RunStatus;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  triggeredBy?: string;
  error?: string | null;
  [k: string]: unknown;
}

export interface PipelineListParams {
  project?: string;
  pipelineName?: string;
  status?: RunStatus;
  limit?: number;
  offset?: number;
  page?: number;
}

export interface OverviewSummary {
  totalRuns?: number;
  activeRuns?: number;
  failedRuns?: number;
  costUsd?: number;
  [k: string]: unknown;
}

export interface PipelineCountByStatus {
  pending?: number;
  running?: number;
  done?: number;
  error?: number;
  [k: string]: unknown;
}

// ── HITL ─────────────────────────────────────────────────────────────────────

export type HitlDecision = "approved" | "rejected";

export interface HitlApproval {
  requestId: string;
  runId?: string;
  pipelineName?: string;
  project?: string;
  agentId?: string;
  toolName?: string;
  toolInput?: Record<string, unknown>;
  /** ISO-8601 UTC string */
  requestedAt?: string;
  expiresAt?: string;
  status?: "pending" | "approved" | "rejected" | "expired" | (string & {});
  [k: string]: unknown;
}

export interface HitlApprovalHistory extends HitlApproval {
  decision?: HitlDecision;
  decidedAt?: string;
  decidedBy?: string;
}

export interface HitlDecideParams {
  comment?: string;
}

export interface HitlBulkDecideBody {
  requestIds: string[];
  decision: HitlDecision;
  comment?: string;
}

export interface HitlBulkDecideResult {
  ok: boolean;
  approved?: string[];
  rejected?: string[];
  [k: string]: unknown;
}

export interface RunToolStats {
  runId: string;
  totalCalls?: number;
  approved?: number;
  rejected?: number;
  pending?: number;
  byTool?: Record<string, unknown>;
  [k: string]: unknown;
}

export interface RunMessage {
  id?: string;
  runId?: string;
  agentId?: string;
  role?: string;
  content?: string;
  timestamp?: string;
  [k: string]: unknown;
}

export interface RunToolCall {
  id?: string;
  runId?: string;
  agentId?: string;
  toolName?: string;
  input?: Record<string, unknown>;
  output?: unknown;
  status?: string;
  durationMs?: number;
  [k: string]: unknown;
}

// ── Credentials / Connectors ─────────────────────────────────────────────────

export interface Credential {
  service: string;
  label?: string;
  keyName?: string;
  masked?: string;
  scope?: "user" | "project" | (string & {});
  [k: string]: unknown;
}

export interface ConnectedService {
  service: string;
  connected: boolean;
  label?: string;
  [k: string]: unknown;
}

export interface OperatorProfile {
  name?: string;
  persona?: string;
  context?: string;
  [k: string]: unknown;
}

export interface CredentialSetBody {
  key?: string;
  value: string;
  label?: string;
}

export interface CredentialTestResult {
  ok: boolean;
  message?: string;
  [k: string]: unknown;
}

// ── AI Models ─────────────────────────────────────────────────────────────────

export interface AIModel {
  id: string;
  name?: string;
  provider?: string;
  contextWindow?: number;
  inputPricePerMToken?: number;
  outputPricePerMToken?: number;
  capabilities?: string[];
  [k: string]: unknown;
}

// ── Runner tokens ─────────────────────────────────────────────────────────────

export interface RunnerToken {
  id: string;
  maskedToken?: string;
  createdAt?: string;
  lastSeen?: string;
  label?: string;
  [k: string]: unknown;
}

export interface RunnerTokenCreate {
  label?: string;
}

export interface RunnerTokenCreateResult {
  id: string;
  /** Plaintext token shown only once — store it securely. */
  token: string;
  [k: string]: unknown;
}

// ── Billing ──────────────────────────────────────────────────────────────────

export interface BillingSubscription {
  tier?: string;
  status?: string;
  currentPeriodEnd?: string;
  cancelAtPeriodEnd?: boolean;
  [k: string]: unknown;
}

export interface BillingPlan {
  id: string;
  name?: string;
  priceId?: string;
  monthlyUsd?: number;
  features?: string[];
  [k: string]: unknown;
}

export interface BillingCheckoutResult {
  url: string;
  [k: string]: unknown;
}

export interface BillingPortalResult {
  url: string;
  [k: string]: unknown;
}

// ── Phone / device control ────────────────────────────────────────────────────

export interface PhoneDevice {
  id: string;
  label?: string;
  platform?: string;
  created_at?: string;
  last_seen?: string;
  expires_at?: string;
  [k: string]: unknown;
}

export interface PhonePairResult {
  code: string;
  expiresInSeconds: number;
  [k: string]: unknown;
}

export interface PhoneApp {
  package: string;
  label?: string;
  [k: string]: unknown;
}

// ── Team ─────────────────────────────────────────────────────────────────────

export type TeamRole = "owner" | "editor" | "viewer" | (string & {});

export interface TeamMember {
  userId: string;
  username?: string;
  email?: string;
  role: TeamRole;
  joinedAt?: string;
  [k: string]: unknown;
}

export interface TeamInviteResult {
  ok: boolean;
  inviteLink?: string;
  [k: string]: unknown;
}

// ── Templates ─────────────────────────────────────────────────────────────────

export type TemplateVisibility = "private" | "team" | "community" | "assigned" | (string & {});

export interface UserTemplate {
  id: string;
  name?: string;
  description?: string;
  category?: string;
  visibility?: TemplateVisibility;
  validated?: boolean;
  payload?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
  [k: string]: unknown;
}

export interface TemplateSaveBody {
  name: string;
  description?: string;
  category?: string;
  payload: Record<string, unknown>;
}

export interface TemplateUpdateBody {
  name?: string;
  description?: string;
  category?: string;
  payload?: Record<string, unknown>;
}

export interface TemplateAssignment {
  templateId: string;
  /** Present when the template is assigned to a user. */
  userId?: string;
  /** Present when the template is assigned to a project. */
  projectId?: string;
  [k: string]: unknown;
}

// ── Pipeline schedule ─────────────────────────────────────────────────────────

export interface PipelineSchedule {
  project: string;
  pipelineName: string;
  cron?: string;
  paused?: boolean;
  lastRunAt?: string;
  nextRunAt?: string;
  [k: string]: unknown;
}

export interface PipelineScheduleUpsertBody {
  cron: string;
  config?: Record<string, unknown>;
}

// ── Traces ───────────────────────────────────────────────────────────────────

export interface Trace {
  traceId: string;
  traceName?: string;
  startTime?: string;
  endTime?: string;
  /** Integer status code: 0 = ok, 1 = unset/warn, 2 = error (matches server OTel convention). */
  status?: number;
  spanCount?: number;
  totalTokens?: number;
  [k: string]: unknown;
}

/**
 * Paginated envelope returned by getTraces / pipelines.traces().
 * Shape: server returns { data: { list, total, page, pageSize } }.
 */
export interface TracePage {
  list: Trace[];
  total: number;
  page: number;
  pageSize: number;
}

/** Result of deleteTraces — spans removed by the DELETE operation. */
export interface DeleteTracesResult {
  deletedSpans: number;
  requestedTraces?: number;
}

/**
 * Aggregate statistics returned by getTraceStatistic.
 * Shape mirrors the server's agentStudio.ts::getTraceStatistic return value.
 */
export interface TraceStats {
  totalTraces: number;
  totalSpans: number;
  errorTraces: number;
  /** Average trace duration in seconds (float). */
  avgDuration: number;
  totalTokens: number;
  /** Per-status breakdown: [{ status: 0|1|2, count: number }, ...] */
  tracesByStatus: Array<{ status: number; count: number }>;
}

// ── Assistant profile ─────────────────────────────────────────────────────────

export interface AssistantProfile {
  name?: string;
  goals?: string[];
  context?: string;
  preferences?: Record<string, unknown>;
  [k: string]: unknown;
}

// ── Project connectors ────────────────────────────────────────────────────────

export interface ProjectConnectorSetBody {
  value: string;
  key?: string;
  label?: string;
}

// ── Socket.IO real-time event shapes ─────────────────────────────────────────

/** Emitted as "pushEvent" on rooms `run:<runId>` and `project:<project>`. */
export interface RunPushEvent {
  event_type?: string;
  runId?: string;
  project?: string;
  pipelineName?: string;
  agentId?: string;
  message?: string;
  status?: RunStatus;
  timestamp?: string;
  [k: string]: unknown;
}

/** Emitted as "pushInitPhase" on run and project rooms. */
export interface RunInitPhaseEvent {
  runId: string;
  step: number;
  total: number;
  label: string;
  status: string;
  project?: string;
  [k: string]: unknown;
}

/** Emitted as "pushHitlApprovals" on room `hitl:user:<userId>`. */
export interface HitlApprovalEvent {
  type: "requested" | "decided" | "killed";
  runId?: string;
  requestId?: string;
  count?: number;
  at: string;
  [k: string]: unknown;
}

/** Emitted as "pipelineCreated" / "pipelineUpdated" / "pipelineDeleted" on project rooms. */
export interface PipelineCrudEvent {
  name: string;
  project: string;
  description?: string;
  updatedBy?: string;
  [k: string]: unknown;
}

