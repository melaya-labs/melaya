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
    [JsonPropertyName("project")]         public string?      Project         { get; init; }
    [JsonPropertyName("executionTarget")] public string?      ExecutionTarget { get; init; }
    [JsonPropertyName("studio_url")]      public string?      StudioUrl       { get; init; }
    [JsonPropertyName("env_overrides")]   public Dictionary<string, string>? EnvOverrides { get; init; }
}

/// <summary>Response from <c>RunAsync</c> — run ID and queued flag.</summary>
public sealed class PipelineRunResult
{
    [JsonPropertyName("run_id")] public string? RunId  { get; set; }
    [JsonPropertyName("queued")] public bool?   Queued { get; set; }
    [JsonExtensionData]          public Dictionary<string, JsonElement>? Extra { get; set; }
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

