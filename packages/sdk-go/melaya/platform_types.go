// Platform and agent plane types — mirrors platform-types.ts from the TS SDK.
package melaya

// ── Common ───────────────────────────────────────────────────────────────────

// RunStatus is the lifecycle state of a pipeline run.
type RunStatus = string

const (
	RunStatusPending   RunStatus = "pending"
	RunStatusQueued    RunStatus = "queued"
	RunStatusRunning   RunStatus = "running"
	RunStatusDone      RunStatus = "done"
	RunStatusError     RunStatus = "error"
	RunStatusKilled    RunStatus = "killed"
	RunStatusCancelled RunStatus = "cancelled"
)

// ── Auth / User ───────────────────────────────────────────────────────────────

// UserProfile is returned by /auth/me.
type UserProfile struct {
	ID           string                 `json:"id"`
	Username     string                 `json:"username,omitempty"`
	Email        string                 `json:"email,omitempty"`
	Tier         string                 `json:"tier,omitempty"`
	Role         string                 `json:"role,omitempty"`
	Capabilities []string               `json:"capabilities,omitempty"`
	Extra        map[string]interface{} `json:"-"`
}

// AuthLoginBody is the request body for /auth/login.
type AuthLoginBody struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// AuthLoginResult is returned by /auth/login.
type AuthLoginResult struct {
	Ok             bool   `json:"ok"`
	Token          string `json:"token,omitempty"`
	MFARequired    bool   `json:"mfaRequired,omitempty"`
	ChallengeToken string `json:"challengeToken,omitempty"`
}

// AuthRegisterBody is the request body for /auth/register.
type AuthRegisterBody struct {
	Username string `json:"username"`
	Email    string `json:"email"`
	Password string `json:"password"`
}

// MFAStatus is returned by /mfa/status.
type MFAStatus struct {
	Enabled   bool   `json:"enabled"`
	Verified  bool   `json:"verified,omitempty"`
	CreatedAt string `json:"createdAt,omitempty"`
}

// MFASetupResult is returned by /mfa/setup.
type MFASetupResult struct {
	Secret    string `json:"secret,omitempty"`
	QRCodeURL string `json:"qrCodeUrl,omitempty"`
}

// ── Projects ─────────────────────────────────────────────────────────────────

// Project is a top-level namespace for pipelines, connectors, and team members.
type Project struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	OwnerID     string `json:"ownerId,omitempty"`
	Role        string `json:"role,omitempty"`
	CreatedAt   string `json:"createdAt,omitempty"`
	UpdatedAt   string `json:"updatedAt,omitempty"`
}

// ProjectCreate is the body for creating a project.
type ProjectCreate struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
}

// ── Pipeline runs (overview plane) ───────────────────────────────────────────

// PipelineRun is a single pipeline execution record.
type PipelineRun struct {
	ID           string    `json:"id"`
	RunID        string    `json:"runId,omitempty"`
	PipelineName string    `json:"pipelineName,omitempty"`
	Project      string    `json:"project,omitempty"`
	Status       RunStatus `json:"status"`
	StartedAt    string    `json:"startedAt,omitempty"`
	FinishedAt   string    `json:"finishedAt,omitempty"`
	DurationMs   *int64    `json:"durationMs,omitempty"`
	TriggeredBy  string    `json:"triggeredBy,omitempty"`
	Error        *string   `json:"error,omitempty"`
}

// PipelineListParams parameterizes the paginated pipeline list.
type PipelineListParams struct {
	Project      string    `json:"project,omitempty"`
	PipelineName string    `json:"pipelineName,omitempty"`
	Status       RunStatus `json:"status,omitempty"`
	Limit        *int      `json:"limit,omitempty"`
	Offset       *int      `json:"offset,omitempty"`
	Page         *int      `json:"page,omitempty"`
}

// OverviewSummary is the dashboard overview payload.
type OverviewSummary struct {
	TotalRuns  *int     `json:"totalRuns,omitempty"`
	ActiveRuns *int     `json:"activeRuns,omitempty"`
	FailedRuns *int     `json:"failedRuns,omitempty"`
	CostUSD    *float64 `json:"costUsd,omitempty"`
}

// PipelineCountByStatus groups run counts by status.
type PipelineCountByStatus struct {
	Pending *int `json:"pending,omitempty"`
	Running *int `json:"running,omitempty"`
	Done    *int `json:"done,omitempty"`
	Error   *int `json:"error,omitempty"`
}

// ModelPrice is one row in the model-prices table.
type ModelPrice struct {
	ID                   string   `json:"id"`
	Provider             string   `json:"provider,omitempty"`
	Name                 string   `json:"name,omitempty"`
	InputPricePerMToken  *float64 `json:"inputPricePerMToken,omitempty"`
	OutputPricePerMToken *float64 `json:"outputPricePerMToken,omitempty"`
}

// ── HITL ─────────────────────────────────────────────────────────────────────

// HitlDecision is an approval decision.
type HitlDecision = string

const (
	HitlDecisionApproved HitlDecision = "approved"
	HitlDecisionRejected HitlDecision = "rejected"
)

// HitlApproval is a pending HITL tool-call approval request.
type HitlApproval struct {
	RequestID    string                 `json:"requestId"`
	RunID        string                 `json:"runId,omitempty"`
	PipelineName string                 `json:"pipelineName,omitempty"`
	Project      string                 `json:"project,omitempty"`
	AgentID      string                 `json:"agentId,omitempty"`
	ToolName     string                 `json:"toolName,omitempty"`
	ToolInput    map[string]interface{} `json:"toolInput,omitempty"`
	RequestedAt  string                 `json:"requestedAt,omitempty"`
	ExpiresAt    string                 `json:"expiresAt,omitempty"`
	Status       string                 `json:"status,omitempty"`
}

// HitlApprovalHistory extends HitlApproval with the decision.
type HitlApprovalHistory struct {
	HitlApproval
	Decision  string `json:"decision,omitempty"`
	DecidedAt string `json:"decidedAt,omitempty"`
	DecidedBy string `json:"decidedBy,omitempty"`
}

// HitlDecideParams are the optional parameters for an approval/rejection.
type HitlDecideParams struct {
	Comment string `json:"comment,omitempty"`
}

// HitlBulkDecideBody is the request body for bulk decisions.
type HitlBulkDecideBody struct {
	RequestIDs []string     `json:"requestIds"`
	Decision   HitlDecision `json:"decision"`
	Comment    string       `json:"comment,omitempty"`
}

// HitlBulkDecideResult is the result of a bulk decision.
type HitlBulkDecideResult struct {
	Ok       bool     `json:"ok"`
	Approved []string `json:"approved,omitempty"`
	Rejected []string `json:"rejected,omitempty"`
}

// RunToolStats aggregates tool call stats for a pipeline run.
type RunToolStats struct {
	RunID      string                 `json:"runId"`
	TotalCalls *int                   `json:"totalCalls,omitempty"`
	Approved   *int                   `json:"approved,omitempty"`
	Rejected   *int                   `json:"rejected,omitempty"`
	Pending    *int                   `json:"pending,omitempty"`
	ByTool     map[string]interface{} `json:"byTool,omitempty"`
}

// RunMessage is a single message in a pipeline run's conversation.
type RunMessage struct {
	ID        string `json:"id,omitempty"`
	RunID     string `json:"runId,omitempty"`
	AgentID   string `json:"agentId,omitempty"`
	Role      string `json:"role,omitempty"`
	Content   string `json:"content,omitempty"`
	Timestamp string `json:"timestamp,omitempty"`
}

// RunToolCall is a single tool call record.
type RunToolCall struct {
	ID         string                 `json:"id,omitempty"`
	RunID      string                 `json:"runId,omitempty"`
	AgentID    string                 `json:"agentId,omitempty"`
	ToolName   string                 `json:"toolName,omitempty"`
	Input      map[string]interface{} `json:"input,omitempty"`
	Output     interface{}            `json:"output,omitempty"`
	Status     string                 `json:"status,omitempty"`
	DurationMs *int64                 `json:"durationMs,omitempty"`
}

// ── Credentials / Connectors ─────────────────────────────────────────────────

// Credential is a stored secret reference.
type Credential struct {
	Service string `json:"service"`
	Label   string `json:"label,omitempty"`
	KeyName string `json:"keyName,omitempty"`
	Masked  string `json:"masked,omitempty"`
	Scope   string `json:"scope,omitempty"`
}

// ConnectedService is a third-party service connection status.
type ConnectedService struct {
	Service   string `json:"service"`
	Connected bool   `json:"connected"`
	Label     string `json:"label,omitempty"`
}

// ConnectorAccount is one account of a field-based connector that holds
// several accounts (two mailboxes, two shops). Only the label and id are ever
// returned, never credential values. ID "current" is a single connection made
// before accounts existed (adopted on the first write).
type ConnectorAccount struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	// IsDefault marks the account agents use unless a tool call names another one.
	IsDefault bool `json:"isDefault"`
	// CreatedAt is nil for the legacy "current" connection.
	CreatedAt *string `json:"createdAt"`
}

// AddConnectorAccountInput is the body of CredentialsAPI.AddAccount and
// ConnectorsAPI.AddAccount.
type AddConnectorAccountInput struct {
	// Label names the new account (optional).
	Label string `json:"label,omitempty"`
	// Fields are the connector's credential fields (same keys as Set). The
	// connection is tested before it is stored.
	Fields map[string]string `json:"fields"`
	// CurrentLabel names the existing single connection when it is adopted as
	// the first account (optional).
	CurrentLabel string `json:"currentLabel,omitempty"`
	// MakeDefault makes the new account the default one (optional).
	MakeDefault *bool `json:"makeDefault,omitempty"`
}

// APIKeyRotation is the result of AccountAPI.RotateAPIKey. APIKey is the new
// platform key, returned ONCE.
type APIKeyRotation struct {
	APIKey string `json:"apiKey"`
}

// DocsPreviewOptions holds optional parameters for PipelinesAPI.DocsPreview.
type DocsPreviewOptions struct {
	// ModelName is the model the agents use (query model_name).
	ModelName string
	// ModelProvider is that model's provider (query model_provider).
	ModelProvider string
}

// OperatorProfile is the persona config injected into agent context.
type OperatorProfile struct {
	Name    string `json:"name,omitempty"`
	Persona string `json:"persona,omitempty"`
	Context string `json:"context,omitempty"`
}

// CredentialSetBody is the request body for storing a credential.
type CredentialSetBody struct {
	Key   string `json:"key,omitempty"`
	Value string `json:"value"`
	Label string `json:"label,omitempty"`
}

// CredentialTestResult is the result of testing a credential.
type CredentialTestResult struct {
	Ok      bool   `json:"ok"`
	Message string `json:"message,omitempty"`
}

// RagIngestStartBody is the body for starting a RAG ingestion job.
type RagIngestStartBody struct {
	Source  string                 `json:"source"`
	Options map[string]interface{} `json:"options,omitempty"`
}

// RagJobStatus is the status of a RAG ingestion or retrieval job.
type RagJobStatus struct {
	SessionID string `json:"sessionId"`
	Status    string `json:"status"`
	Progress  *int   `json:"progress,omitempty"`
	Error     string `json:"error,omitempty"`
}

// RagRetrieveBody is the body for starting a RAG retrieval query.
type RagRetrieveBody struct {
	Query   string                 `json:"query"`
	Options map[string]interface{} `json:"options,omitempty"`
}

// PickFolderStatus is the result of a folder picker session.
type PickFolderStatus struct {
	SessionID string `json:"sessionId"`
	Status    string `json:"status"`
	Path      string `json:"path,omitempty"`
}

// DBTestStartResult is returned by DBTestStart (personal or project-scoped).
// Ok is false when the service is unsupported or the caller's runner is
// offline — Error then carries a machine-readable reason (e.g.
// "unsupported_service", "runner_offline") and Hint a human-readable next
// step. Poll DBTestStatus with SessionID until it reports "complete".
type DBTestStartResult struct {
	Ok        bool   `json:"ok"`
	SessionID string `json:"sessionId,omitempty"`
	Error     string `json:"error,omitempty"`
	Hint      string `json:"hint,omitempty"`
}

// DBTestStatusResult is the poll result for a database connection test.
// State is "pending", "complete", or "expired"; Ok/Message/Error are only
// meaningful once State is "complete".
type DBTestStatusResult struct {
	State   string `json:"state"`
	Ok      bool   `json:"ok,omitempty"`
	Message string `json:"message,omitempty"`
	Error   string `json:"error,omitempty"`
}

// WhatsappSignupExchangeBody is the request body for
// CredentialsAPI.WhatsappSignupExchange.
type WhatsappSignupExchangeBody struct {
	Code          string `json:"code"`
	PhoneNumberID string `json:"phoneNumberId"`
	WabaID        string `json:"wabaId"`
	// Project scopes the stored credential to a project (editor/owner
	// required); omit to store it at personal scope.
	Project string `json:"project,omitempty"`
}

// ── AI Models ─────────────────────────────────────────────────────────────────

// AIModel describes one available AI model.
type AIModel struct {
	ID                   string   `json:"id"`
	Name                 string   `json:"name,omitempty"`
	Provider             string   `json:"provider,omitempty"`
	ContextWindow        *int     `json:"contextWindow,omitempty"`
	InputPricePerMToken  *float64 `json:"inputPricePerMToken,omitempty"`
	OutputPricePerMToken *float64 `json:"outputPricePerMToken,omitempty"`
	Capabilities         []string `json:"capabilities,omitempty"`
}

// ── Runner tokens ─────────────────────────────────────────────────────────────

// RunnerToken is a masked runner token record.
type RunnerToken struct {
	ID          string `json:"id"`
	MaskedToken string `json:"maskedToken,omitempty"`
	CreatedAt   string `json:"createdAt,omitempty"`
	LastSeen    string `json:"lastSeen,omitempty"`
	Label       string `json:"label,omitempty"`
}

// RunnerTokenCreate is the optional body for minting a runner token.
type RunnerTokenCreate struct {
	Label string `json:"label,omitempty"`
}

// RunnerTokenCreateResult contains the new token (shown only once).
type RunnerTokenCreateResult struct {
	ID    string `json:"id"`
	Token string `json:"token"` // plaintext — store it securely; never log
}

// ── Billing ──────────────────────────────────────────────────────────────────

// BillingSubscription is the Stripe subscription status.
type BillingSubscription struct {
	Tier              string `json:"tier,omitempty"`
	Status            string `json:"status,omitempty"`
	CurrentPeriodEnd  string `json:"currentPeriodEnd,omitempty"`
	CancelAtPeriodEnd bool   `json:"cancelAtPeriodEnd,omitempty"`
}

// BillingPlan is a public pricing plan.
type BillingPlan struct {
	ID         string   `json:"id"`
	Name       string   `json:"name,omitempty"`
	PriceID    string   `json:"priceId,omitempty"`
	MonthlyUSD *float64 `json:"monthlyUsd,omitempty"`
	Features   []string `json:"features,omitempty"`
}

// BillingCheckoutResult holds the Stripe Checkout URL.
type BillingCheckoutResult struct {
	URL string `json:"url"`
}

// BillingPortalResult holds the Stripe Customer Portal URL.
type BillingPortalResult struct {
	URL string `json:"url"`
}

// ── Phone / device control ────────────────────────────────────────────────────

// PhoneDevice is a paired phone device.
type PhoneDevice struct {
	ID        string `json:"id"`
	Label     string `json:"label,omitempty"`
	Platform  string `json:"platform,omitempty"`
	CreatedAt string `json:"created_at,omitempty"`
	LastSeen  string `json:"last_seen,omitempty"`
	ExpiresAt string `json:"expires_at,omitempty"`
}

// PhonePairResult contains the pairing code for the Melaya APK.
type PhonePairResult struct {
	Code             string `json:"code"`
	ExpiresInSeconds int    `json:"expiresInSeconds"`
}

// PhoneApp is an installed app on the paired phone.
type PhoneApp struct {
	Package string `json:"package"`
	Label   string `json:"label,omitempty"`
}

// PhoneGrantAppResult is returned by PhoneAPI.GrantApp.
type PhoneGrantAppResult struct {
	Ok bool `json:"ok"`
	// Synced reports whether the allowlist reached the phone immediately
	// (false if it was offline — it picks up the cached policy on its next poll).
	Synced          bool     `json:"synced"`
	AllowedPackages []string `json:"allowedPackages"`
}

// PhoneRequestCastResult is returned by PhoneAPI.RequestCast.
type PhoneRequestCastResult struct {
	Ok bool `json:"ok"`
	// Online reports whether the phone was already online when the request
	// was enqueued (an offline phone is nudged with a best-effort wake).
	Online bool `json:"online"`
}

// ── Team ─────────────────────────────────────────────────────────────────────

// TeamRole is a project team role.
type TeamRole = string

const (
	TeamRoleOwner  TeamRole = "owner"
	TeamRoleEditor TeamRole = "editor"
	TeamRoleViewer TeamRole = "viewer"
)

// TeamMember is a member of a project team.
type TeamMember struct {
	UserID   string   `json:"userId"`
	Username string   `json:"username,omitempty"`
	Email    string   `json:"email,omitempty"`
	Role     TeamRole `json:"role"`
	JoinedAt string   `json:"joinedAt,omitempty"`
}

// TeamInviteResult is returned by invite-link creation.
type TeamInviteResult struct {
	Ok         bool   `json:"ok"`
	InviteLink string `json:"inviteLink,omitempty"`
}

// ── Templates ─────────────────────────────────────────────────────────────────

// TemplateVisibility is the sharing level for a template.
type TemplateVisibility = string

const (
	TemplateVisibilityPrivate   TemplateVisibility = "private"
	TemplateVisibilityTeam      TemplateVisibility = "team"
	TemplateVisibilityCommunity TemplateVisibility = "community"
	TemplateVisibilityAssigned  TemplateVisibility = "assigned"
)

// UserTemplate is a pipeline template.
type UserTemplate struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name,omitempty"`
	Description string                 `json:"description,omitempty"`
	Category    string                 `json:"category,omitempty"`
	Visibility  TemplateVisibility     `json:"visibility,omitempty"`
	Validated   bool                   `json:"validated,omitempty"`
	Payload     map[string]interface{} `json:"payload,omitempty"`
	CreatedAt   string                 `json:"createdAt,omitempty"`
	UpdatedAt   string                 `json:"updatedAt,omitempty"`
}

// TemplateSaveBody is the request body for creating a template.
type TemplateSaveBody struct {
	Name        string                 `json:"name"`
	Description string                 `json:"description,omitempty"`
	Category    string                 `json:"category,omitempty"`
	Payload     map[string]interface{} `json:"payload"`
}

// TemplateUpdateBody is the request body for updating a template.
type TemplateUpdateBody struct {
	Name        string                 `json:"name,omitempty"`
	Description string                 `json:"description,omitempty"`
	Category    string                 `json:"category,omitempty"`
	Payload     map[string]interface{} `json:"payload,omitempty"`
}

// TemplateAssignment is one assignment row of a template to a user or project.
type TemplateAssignment struct {
	TemplateID string `json:"templateId"`
	UserID     string `json:"userId,omitempty"`
	ProjectID  string `json:"projectId,omitempty"`
}

// TemplateAssignBody selects the assignment target for Assign/Unassign.
// Set exactly one of UserID or ProjectID (both are UUIDs) — never both.
type TemplateAssignBody struct {
	UserID    string `json:"userId,omitempty"`
	ProjectID string `json:"projectId,omitempty"`
}

// ── Pipeline schedule ─────────────────────────────────────────────────────────

// PipelineSchedule is a cron schedule for a pipeline.
type PipelineSchedule struct {
	Project      string `json:"project"`
	PipelineName string `json:"pipelineName"`
	Cron         string `json:"cron,omitempty"`
	Paused       bool   `json:"paused,omitempty"`
	LastRunAt    string `json:"lastRunAt,omitempty"`
	NextRunAt    string `json:"nextRunAt,omitempty"`
}

// PipelineScheduleUpsertBody is the body for creating/updating a schedule.
type PipelineScheduleUpsertBody struct {
	Cron   string                 `json:"cron"`
	Config map[string]interface{} `json:"config,omitempty"`
}

// ── Traces ───────────────────────────────────────────────────────────────────

// TraceSummary is a single trace summary row returned in the paginated list.
// Field names match the SQL projection in agentStudio.ts::getTraces.
type TraceSummary struct {
	TraceID     string `json:"traceId"`
	TraceName   string `json:"traceName,omitempty"`
	StartTime   string `json:"startTime,omitempty"`
	EndTime     string `json:"endTime,omitempty"`
	Status      int    `json:"status,omitempty"`
	SpanCount   int    `json:"spanCount,omitempty"`
	TotalTokens int    `json:"totalTokens,omitempty"`
}

// TracePage is the paginated envelope returned by GET /runs/:runId/traces.
// Shape: { data: { list: [...], total, page, pageSize } }
type TracePage struct {
	List     []TraceSummary `json:"list"`
	Total    int            `json:"total"`
	Page     int            `json:"page"`
	PageSize int            `json:"pageSize"`
}

// Trace is a single observability trace for a run (used by the single-trace endpoint).
type Trace struct {
	ID         string `json:"id"`
	RunID      string `json:"runId,omitempty"`
	Name       string `json:"name,omitempty"`
	StartedAt  string `json:"startedAt,omitempty"`
	FinishedAt string `json:"finishedAt,omitempty"`
}

// DeleteTracesResult is the response from DELETE /runs/:runId/traces.
type DeleteTracesResult struct {
	DeletedSpans    int  `json:"deletedSpans"`
	RequestedTraces *int `json:"requestedTraces,omitempty"`
}

// TraceStatusCount is one row of the per-status breakdown in TraceStats.
type TraceStatusCount struct {
	Status int `json:"status"`
	Count  int `json:"count"`
}

// TraceStats holds aggregate statistics returned by getTraceStatistic.
// Shape mirrors agentStudio.ts::getTraceStatistic on the server.
type TraceStats struct {
	TotalTraces int `json:"totalTraces"`
	TotalSpans  int `json:"totalSpans"`
	ErrorTraces int `json:"errorTraces"`
	// AvgDuration is the average trace duration in seconds (float).
	AvgDuration    float64            `json:"avgDuration"`
	TotalTokens    int64              `json:"totalTokens"`
	TracesByStatus []TraceStatusCount `json:"tracesByStatus"`
}

// ── Tool-call audit ───────────────────────────────────────────────────────────

// ToolCallApproval describes how a tool call was authorized.
type ToolCallApproval struct {
	// Mode is "auto" (no HITL gate) or "approved" (a human approved it).
	Mode       string  `json:"mode"`
	ApprovedBy *string `json:"approvedBy"`
	ApprovedAt *string `json:"approvedAt"`
	// Reason is only populated on ToolCallDetail.
	Reason *string `json:"reason,omitempty"`
}

// ToolCall is one row in a project's tool-call audit ledger, as returned by
// PipelinesAPI.ProjectToolCalls. Input/output are 4KB-truncated previews —
// call ToolCallDetail for the full values.
type ToolCall struct {
	ID              string           `json:"id"`
	TraceID         string           `json:"traceId"`
	RunID           string           `json:"runId"`
	ToolName        string           `json:"toolName"`
	ToolDescription *string          `json:"toolDescription"`
	ToolCallID      *string          `json:"toolCallId"`
	Service         *string          `json:"service"`
	Agent           *string          `json:"agent"`
	Pipeline        string           `json:"pipeline"`
	Project         string           `json:"project"`
	RanBy           *string          `json:"ranBy"`
	RanByEmail      *string          `json:"ranByEmail"`
	ConnectorSource *string          `json:"connectorSource"`
	Approval        ToolCallApproval `json:"approval"`
	Model           *string          `json:"model"`
	Provider        string           `json:"provider"`
	InputTokens     *int64           `json:"inputTokens"`
	OutputTokens    *int64           `json:"outputTokens"`
	CostUSD         *float64         `json:"costUsd"`
	// Status is "ok" or "error".
	Status          string   `json:"status"`
	LatencyMs       *float64 `json:"latencyMs"`
	Timestamp       string   `json:"timestamp"`
	InputPreview    string   `json:"inputPreview"`
	InputTruncated  bool     `json:"inputTruncated"`
	OutputPreview   string   `json:"outputPreview"`
	OutputTruncated bool     `json:"outputTruncated"`
}

// ToolCallCursor is the keyset-pagination cursor for ProjectToolCalls (only
// populated for the "recent"/"oldest" time sorts; "slowest"/"fastest" return a
// bounded top-N snapshot instead, with Capped=true and no cursor).
type ToolCallCursor struct {
	BeforeCreatedAt string `json:"beforeCreatedAt"`
	BeforeID        string `json:"beforeId"`
}

// ProjectToolCallsResult is the paginated envelope returned by ProjectToolCalls.
type ProjectToolCallsResult struct {
	Items      []ToolCall      `json:"items"`
	NextCursor *ToolCallCursor `json:"nextCursor"`
	// Capped is true for the latency sorts ("slowest"/"fastest"), which return
	// a bounded top-N snapshot rather than a paginated feed.
	Capped bool `json:"capped"`
}

// ProjectToolCallsParams filters and paginates ProjectToolCalls. All fields
// are optional.
type ProjectToolCallsParams struct {
	// BeforeCreatedAt + BeforeID form a descending keyset cursor from a
	// previous ProjectToolCallsResult.NextCursor (time sorts only).
	BeforeCreatedAt string
	BeforeID        string
	// Limit is 1-100 (default 30 server-side).
	Limit int
	Tool  string
	Agent string
	RunID string
	// Status is "ok" or "error".
	Status string
	Search string
	// ConnectorSource is "project" or "personal".
	ConnectorSource string
	// Approval is "auto", "approved" (any approver), or "by:<username>".
	Approval string
	Provider string
	// Sort is "recent" (default), "oldest", "slowest", or "fastest".
	Sort string
}

// ToolCallFacetCount is one entry in ProjectToolCallFacets.Tools: a tool name
// and how many times it was called.
type ToolCallFacetCount struct {
	Name  string `json:"name"`
	Count int    `json:"count"`
}

// ProjectToolCallFacets holds the distinct filter values seen in a project's
// tool-call ledger — powers the audit UI's filter dropdowns.
type ProjectToolCallFacets struct {
	Tools     []ToolCallFacetCount `json:"tools"`
	Agents    []string             `json:"agents"`
	Approvers []string             `json:"approvers,omitempty"`
	Providers []string             `json:"providers,omitempty"`
}

// ToolCallDetail is the full, untruncated input/output for one tool-call span
// within a run, as returned by PipelinesAPI.ToolCallDetail.
type ToolCallDetail struct {
	ToolName        string           `json:"toolName"`
	ToolDescription *string          `json:"toolDescription"`
	Input           string           `json:"input"`
	Output          string           `json:"output"`
	Status          string           `json:"status"`
	LatencyMs       *float64         `json:"latencyMs"`
	Model           *string          `json:"model"`
	Provider        string           `json:"provider"`
	InputTokens     *int64           `json:"inputTokens"`
	OutputTokens    *int64           `json:"outputTokens"`
	CostUSD         *float64         `json:"costUsd"`
	ConnectorSource *string          `json:"connectorSource"`
	Approval        ToolCallApproval `json:"approval"`
}

// ── Assistant profile ─────────────────────────────────────────────────────────

// AssistantProfile is the caller's onboarding / persona configuration.
type AssistantProfile struct {
	Name        string                 `json:"name,omitempty"`
	Goals       []string               `json:"goals,omitempty"`
	Context     string                 `json:"context,omitempty"`
	Preferences map[string]interface{} `json:"preferences,omitempty"`
}

// ── Evals ─────────────────────────────────────────────────────────────────────

// EvalRun is an evaluation run record.
type EvalRun struct {
	ID        string                 `json:"id"`
	Status    RunStatus              `json:"status,omitempty"`
	Summary   map[string]interface{} `json:"summary,omitempty"`
	StartedAt string                 `json:"startedAt,omitempty"`
}

// EvalSummary is the aggregate eval summary across runs.
type EvalSummary struct {
	TotalRuns *int     `json:"totalRuns,omitempty"`
	PassRate  *float64 `json:"passRate,omitempty"`
}

// ── Crew memory (edit / delete) ──────────────────────────────────────────────

// MemoryEntryPatch is the set of fields that can be changed on a persisted
// crew-memory entry. Unset (zero-value) fields are left unchanged server-side.
type MemoryEntryPatch struct {
	Topic        string   `json:"topic,omitempty"`
	Content      string   `json:"content,omitempty"`
	Tags         []string `json:"tags,omitempty"`
	Priority     *int     `json:"priority,omitempty"`
	AgentTargets []string `json:"agent_targets,omitempty"`
}

// MemoryEditEntryBody is the request body for EvalsAPI.EditMemoryEntry.
type MemoryEditEntryBody struct {
	Pipeline string           `json:"pipeline"`
	EntryID  string           `json:"entryId"`
	Project  string           `json:"project,omitempty"`
	Patch    MemoryEntryPatch `json:"patch"`
}

// MemoryDeleteEntryBody is the request body for EvalsAPI.DeleteMemoryEntry.
type MemoryDeleteEntryBody struct {
	Pipeline string `json:"pipeline"`
	EntryID  string `json:"entryId"`
	Project  string `json:"project,omitempty"`
}

// ── Project connectors ────────────────────────────────────────────────────────

// ProjectConnectorSetBody is the body for setting a project-scoped connector.
type ProjectConnectorSetBody struct {
	Value string `json:"value"`
	Key   string `json:"key,omitempty"`
	Label string `json:"label,omitempty"`
}

// ── Bugs ─────────────────────────────────────────────────────────────────────

// BugReport is a user-submitted bug report.
type BugReport struct {
	ID          string `json:"id"`
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
	Status      string `json:"status,omitempty"`
	CreatedAt   string `json:"createdAt,omitempty"`
}

// BugCreateBody is the request body for submitting a bug report.
type BugCreateBody struct {
	Title       string `json:"title"`
	Description string `json:"description,omitempty"`
}

// BugNotification is a notification about a bug report update.
type BugNotification struct {
	ID      string `json:"id"`
	BugID   string `json:"bugId,omitempty"`
	Message string `json:"message,omitempty"`
	Read    bool   `json:"read,omitempty"`
}

// ── Accounts (GDPR / credits) ─────────────────────────────────────────────────

// CreditBalance is the current credit balance.
type CreditBalance struct {
	Balance  float64 `json:"balance"`
	Currency string  `json:"currency,omitempty"`
}

// ── Embeddable pipeline lifecycle ────────────────────────────────────────────

// PipelineConfig is the full pipeline configuration. It is a free-form map;
// Name and Project are the only well-known top-level fields. A few fields are
// worth knowing about:
//
//   - The run is generated ONLY from steps[]. A top-level agents[] array alone
//     produces an EMPTY pipeline — every agent must be embedded in its own
//     step: steps: [{kind: "agent", agent: {name, role, instruction,
//     model: {provider, name}, agent_tools, human_approval_tools}}].
//   - There is no "prompt" field. The prompt-shaped fields on an agent are
//     "instruction" (the per-run task) and "system_prompt_override".
//   - hitl_mode is "safe" (default) | "autonomous" | "payments_only"; only
//     "safe" honors each agent's human_approval_tools.
//   - connector_source is "personal" | "project".
//   - force_local_runner routes execution to the caller's own runner.
//   - inputs[] declares the run_inputs.values keys a pipeline accepts.
//
// See Get and Update for how the server envelope wraps this config.
type PipelineConfig map[string]interface{}

// PipelineCreateBody is the request body for creating a pipeline.
// Name and Project are required; the remainder of the map is the agent config.
type PipelineCreateBody struct {
	Name        string                 `json:"name"`
	Project     string                 `json:"project"`
	Description string                 `json:"description,omitempty"`
	Extra       map[string]interface{} `json:"-"` // merged at marshal time — use PipelineConfig directly when richer control is needed
}

// PipelineUpdateBody is the request body for updating a pipeline.
type PipelineUpdateBody struct {
	Config  PipelineConfig `json:"config"`
	Project string         `json:"project"`
}

// PipelineRunOptions holds optional parameters for triggering a pipeline run.
type PipelineRunOptions struct {
	// Project narrows tenant scope.
	Project string `json:"project,omitempty"`
	// ExecutionTarget is used for the TIER CHECK only (e.g. gating cloud-spawn
	// to paid tiers). It does NOT decide where the run actually executes —
	// that is decided by the pipeline's stored config (its configured local
	// model providers / force_local_runner), never by this per-run field.
	ExecutionTarget string `json:"executionTarget,omitempty"`
	// StudioURL is an optional callback URL for run-event notifications.
	StudioURL string `json:"studio_url,omitempty"`
	// EnvOverrides are per-run env var overrides layered over stored
	// credentials. Any key matching MEL_* or MELAYA_* (case-insensitive) is
	// stripped server-side — those are trusted, server-stamped identifiers
	// (tier, owner, crew, pipeline id) that a caller can never override.
	EnvOverrides map[string]string `json:"env_overrides,omitempty"`
	// RunInputs threads a brief and/or named input values through to the run
	// (e.g. for a pipeline whose steps read run_inputs.brief / .values.<key>).
	RunInputs *RunInputs `json:"run_inputs,omitempty"`
}

// RunInputs are values threaded through to a pipeline run: an optional brief
// and a map of named input values. A file value inside Values may be one of:
//
//   - {"file_id": "..."}       — from a prior UploadRunFile call (single-use, 24h)
//   - {"url": "..."}           — fetched server-side, ≤25 MB
//   - {"base64": "...", "name": "..."} — inlined, ≤7 MB
type RunInputs struct {
	Brief  string                 `json:"brief,omitempty"`
	Values map[string]interface{} `json:"values,omitempty"`
}

// PipelineRunAccepted is the response envelope returned when a pipeline run is
// accepted by the server.
type PipelineRunAccepted struct {
	RunID  string `json:"run_id"`
	Queued bool   `json:"queued"`
	// RunInputs echoes back the resolved run_inputs (e.g. with {url}/{base64}
	// file references normalized to {file_id}) when the run was started with any.
	RunInputs *RunInputs `json:"run_inputs,omitempty"`
}

// PipelineRunInputs is the persisted run_inputs record for one run, as
// originally passed to Run (or resolved server-side from url/base64 file
// references into file_id references).
type PipelineRunInputs struct {
	Brief  string                 `json:"brief,omitempty"`
	Values map[string]interface{} `json:"values,omitempty"`
	Files  []interface{}          `json:"files,omitempty"`
}

// PipelineInputDeclaration is one declared run input (config.inputs[]), as
// SetInputs takes it. Key: lower snake case, starts with a letter, max 40,
// unique; "brief" is reserved. Type: text, long_text, number, boolean,
// choice, url, email, file, files. Options are required for choice (max 50);
// Accept applies to file types only (pdf, office, spreadsheet, image, text).
type PipelineInputDeclaration struct {
	Key         string      `json:"key"`
	Label       string      `json:"label,omitempty"`
	Type        string      `json:"type,omitempty"`
	Required    bool        `json:"required,omitempty"`
	Default     interface{} `json:"default,omitempty"`
	Options     []string    `json:"options,omitempty"`
	Accept      []string    `json:"accept,omitempty"`
	Description string      `json:"description,omitempty"`
}

// PipelineInputsUpdateResult is the normalized declaration SetInputs stored.
type PipelineInputsUpdateResult struct {
	Name   string                     `json:"name"`
	Inputs []PipelineInputDeclaration `json:"inputs"`
}

// UploadRunFileOptions holds optional parameters for UploadRunFile.
type UploadRunFileOptions struct {
	// Project narrows tenant scope.
	Project string
	// ContentType overrides the multipart part's Content-Type
	// (default: detected from the filename by the server).
	ContentType string
}

// RunFileUploadResult is returned by UploadRunFile: a single-use file
// reference, valid 24h, to pass as a Values entry in RunInputs.
type RunFileUploadResult struct {
	FileID string `json:"file_id"`
}

// UploadDocOptions holds optional parameters for UploadDoc and
// UploadRetrievalDoc.
type UploadDocOptions struct {
	// ContentType overrides the multipart part's Content-Type
	// (default: detected from the filename by the server).
	ContentType string
}

// PipelineRunIDsResult is the response for listing run IDs.
type PipelineRunIDsResult struct {
	RunIDs []string               `json:"run_ids"`
	Extra  map[string]interface{} `json:"-"`
}

// PipelineRunStatus holds the status and cost for a single pipeline run.
type PipelineRunStatus struct {
	RunID           string      `json:"runId"`
	Status          string      `json:"status"`
	CreatedAt       string      `json:"createdAt"`
	ExecutionTarget string      `json:"executionTarget,omitempty"`
	Cost            interface{} `json:"cost"`
}

// TemplateInstantiateBody is the request body for instantiating a pipeline from
// a template.
type TemplateInstantiateBody struct {
	Name      string                 `json:"name"`
	Project   string                 `json:"project"`
	Overrides map[string]interface{} `json:"overrides,omitempty"`
}

// TemplateInstantiateResult wraps the pipeline config produced by instantiation.
type TemplateInstantiateResult struct {
	Pipeline PipelineConfig `json:"pipeline"`
}

// ── Socket.IO real-time event shapes ─────────────────────────────────────────

// RunPushEvent is emitted as "pushEvent" on run:<runId> and project:<project> rooms.
type RunPushEvent struct {
	EventType    string    `json:"event_type,omitempty"`
	RunID        string    `json:"runId,omitempty"`
	Project      string    `json:"project,omitempty"`
	PipelineName string    `json:"pipelineName,omitempty"`
	AgentID      string    `json:"agentId,omitempty"`
	Message      string    `json:"message,omitempty"`
	Status       RunStatus `json:"status,omitempty"`
	Timestamp    string    `json:"timestamp,omitempty"`
}

// RunInitPhaseEvent is emitted as "pushInitPhase" during pipeline init.
type RunInitPhaseEvent struct {
	RunID   string `json:"runId"`
	Step    int    `json:"step"`
	Total   int    `json:"total"`
	Label   string `json:"label"`
	Status  string `json:"status"`
	Project string `json:"project,omitempty"`
}

// HitlApprovalEvent is emitted as "pushHitlApprovals" on the user's HITL room.
type HitlApprovalEvent struct {
	Type      string `json:"type"` // "requested" | "decided" | "killed"
	RunID     string `json:"runId,omitempty"`
	RequestID string `json:"requestId,omitempty"`
	Count     *int   `json:"count,omitempty"`
	At        string `json:"at"`
}

// PipelineCrudEvent is emitted for pipeline CRUD changes in a project room.
type PipelineCrudEvent struct {
	Name        string `json:"name"`
	Project     string `json:"project"`
	Description string `json:"description,omitempty"`
	UpdatedBy   string `json:"updatedBy,omitempty"`
}
