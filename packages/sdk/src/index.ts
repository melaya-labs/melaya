/**
 * @melaya/sdk — Official TypeScript/JavaScript SDK for the Melaya unified
 * market-data, streaming, and agentic-platform API across 70+ venues.
 *
 * @example
 * ```ts
 * import { Melaya } from "@melaya/sdk";
 *
 * const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! });
 *
 * // REST — market data
 * const ticker = await melaya.market.ticker({ exchange: "binance", symbol: "BTC/USDT", market: "spot" });
 *
 * // WebSocket — live order book
 * for await (const book of melaya.stream.orderbook({ exchange: "bybit", symbol: "BTC/USDT", limit: 20 })) {
 *   console.log(book.bids[0], book.asks[0]);
 * }
 *
 * // Platform — agent pipelines
 * const projects = await melaya.projects.list();
 * const pending = await melaya.hitl.pending();
 * await melaya.hitl.approve(pending[0].requestId);
 *
 * // Real-time — run events
 * melaya.events.onRunUpdate("run-123", (e) => console.log(e.event_type, e.status));
 * melaya.events.onHitlApproval((e) => console.log("HITL:", e.type, e.count));
 * ```
 *
 * @see https://melaya.org/docs
 */

// ── Core ─────────────────────────────────────────────────────────────────────
export { Melaya, MelayaError, DEFAULT_BASE_URL, DEFAULT_WS_URL, ModelsAPI, OverviewAPI } from "./client.js";
export type { MelayaOptions, TradingNamespace, AgentsNamespace, PlatformNamespace } from "./client.js";

// ── Trading plane ─────────────────────────────────────────────────────────────
export { MarketAPI } from "./market.js";
export { AccountAPI } from "./account.js";
export { SimAPI } from "./sim.js";
export type { SimCreateOrder } from "./sim.js";
export { StrategiesAPI } from "./strategies.js";
export { BacktestAPI } from "./backtest.js";
export { TradeAPI } from "./trade.js";
export type { VenueRef, LiveOrder, LiveResult } from "./trade.js";
export { StreamAPI, MelayaStream } from "./stream.js";
export type { WebSocketLike, WebSocketCtor } from "./stream.js";
export * from "./types.js";

// ── Platform / agents plane ───────────────────────────────────────────────────
export { ProjectsAPI } from "./projects.js";
export { PipelinesAPI } from "./pipelines.js";
export type {
  PipelineConfig,
  PipelineCreateBody,
  PipelineRunOptions,
  PipelineRunAccepted,
  PipelineRunStatus,
  TemplateInstantiateBody,
} from "./pipelines.js";
export { EvalsAPI } from "./evals.js";
export type {
  EvalRange,
  EvalToolIssue,
  EvalSummary,
  EvalRunCost,
  EvalRunSummary,
  EvalPhaseVerdict,
  EvalSummaryAggregate,
  EvalRunDetail,
  EvalBenchmark,
  EvalListRunsParams,
  EvalSummaryParams,
} from "./evals.js";
export { MemoryAPI } from "./memory.js";
export type {
  MemoryRange,
  MemoryMsgPreview,
  RunMemory,
  MemoryGraphNode,
  MemoryGraphEdge,
  MemoryGraph,
  CrewMemoryEntry,
  CrewMemoryResult,
  MemoryGraphParams,
  CrewMemoryParams,
} from "./memory.js";
export { HitlAPI } from "./hitl.js";
export { CredentialsAPI } from "./credentials.js";
export { ConnectorsAPI } from "./connectors.js";
export { BillingAPI } from "./billing.js";
export { PhoneAPI } from "./phone.js";
export { TeamAPI } from "./team.js";
export { TemplatesAPI } from "./templates.js";
export { AssistantAPI } from "./assistant.js";
export { RunnerAPI } from "./runner.js";
export { AuthAPI } from "./auth.js";
export type { AuthResult } from "./auth.js";
export { MfaAPI } from "./mfa.js";
export { AccountsAPI } from "./accounts.js";
export type { AccountPlatformResult } from "./accounts.js";
export { BugsAPI } from "./bugs.js";
export type { BugResult } from "./bugs.js";
export { MelayaEvents } from "./events.js";
export * from "./platform-types.js";
