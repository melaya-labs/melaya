using System.Text.Json;
using System.Text.Json.Serialization;

namespace Melaya;

// ── Envelopes ─────────────────────────────────────────────────────────────────

/// <summary>Generic envelope for unwrapping a named key from the API response.</summary>
internal sealed class Envelope<T>
{
    [JsonPropertyName("ok")]    public bool Ok    { get; set; }
    [JsonPropertyName("error")] public string? Error { get; set; }
    // The actual data field is read by the typed sub-classes below.
}

internal sealed class ExchangesEnvelope    { [JsonPropertyName("exchanges")]    public List<ExchangeInfo>?  Exchanges    { get; set; } }
internal sealed class TickerEnvelope       { [JsonPropertyName("ticker")]       public JsonElement?         Ticker       { get; set; } }
internal sealed class OrderBookEnvelope    { [JsonPropertyName("orderbook")]    public JsonElement?         Orderbook    { get; set; } }
internal sealed class CandlesEnvelope      { [JsonPropertyName("candles")]      public List<JsonElement>?   Candles      { get; set; } }
internal sealed class TradesEnvelope       { [JsonPropertyName("trades")]       public List<JsonElement>?   Trades       { get; set; } }
internal sealed class MarketsEnvelope      { [JsonPropertyName("markets")]      public List<JsonElement>?   Markets      { get; set; } }
internal sealed class CurrenciesEnvelope   { [JsonPropertyName("currencies")]   public List<JsonElement>?   Currencies   { get; set; } }
internal sealed class StatusEnvelope       { [JsonPropertyName("status")]       public JsonElement?         Status       { get; set; } }
internal sealed class TimeEnvelope         { [JsonPropertyName("time")]         public JsonElement?         Time         { get; set; } }
internal sealed class TickersEnvelope      { [JsonPropertyName("tickers")]      public JsonElement?         Tickers      { get; set; } }
internal sealed class RatesEnvelope        { [JsonPropertyName("rates")]        public JsonElement?         Rates        { get; set; } }
internal sealed class HistoryEnvelope      { [JsonPropertyName("history")]      public List<JsonElement>?   History      { get; set; } }
internal sealed class OpenInterestEnvelope { [JsonPropertyName("openInterest")] public JsonElement?         OpenInterest { get; set; } }
internal sealed class EventsEnvelope       { [JsonPropertyName("events")]       public List<JsonElement>?   Events       { get; set; } }
internal sealed class PerSymbolEnvelope    { [JsonPropertyName("perSymbol")]    public JsonElement?         PerSymbol    { get; set; } }
internal sealed class ConstraintsEnvelope  { [JsonPropertyName("constraints")]  public JsonElement?         Constraints  { get; set; } }
internal sealed class PerExchangeEnvelope  { [JsonPropertyName("perExchange")]  public JsonElement?         PerExchange  { get; set; } }
internal sealed class PmMarketsEnvelope    { [JsonPropertyName("markets")]      public List<JsonElement>?   Markets      { get; set; } }
internal sealed class KeysEnvelope         { [JsonPropertyName("keys")]         public List<JsonElement>?   Keys         { get; set; } }
internal sealed class StrategiesEnvelope   { [JsonPropertyName("strategies")]   public List<JsonElement>?   Strategies   { get; set; } }
internal sealed class StrategyEnvelope     { [JsonPropertyName("strategy")]     public JsonElement?         Strategy     { get; set; } }
internal sealed class RowsEnvelope         { [JsonPropertyName("rows")]         public List<JsonElement>?   Rows         { get; set; } }
internal sealed class ResultEnvelope       { [JsonPropertyName("result")]       public JsonElement?         Result       { get; set; } }
internal sealed class BacktestListEnvelope
{
    [JsonPropertyName("data")] public BacktestListData? Data { get; set; }
}
internal sealed class BacktestListData
{
    [JsonPropertyName("jobs")] public List<JsonElement>? Jobs { get; set; }
}
internal sealed class EarliestMsEnvelope   { [JsonPropertyName("earliest_ms")] public long? EarliestMs { get; set; } }
internal sealed class WsTicketEnvelope     { [JsonPropertyName("wsTicket")]     public string? WsTicket { get; set; } }

// ── Public model types (JsonElement-based for flexibility) ───────────────────

/// <summary>Exchange info as returned by list-exchanges.</summary>
public sealed class ExchangeInfo
{
    [JsonPropertyName("id")]                     public string?  Id                     { get; set; }
    [JsonPropertyName("display")]                public string?  Display                { get; set; }
    [JsonPropertyName("market")]                 public string?  Market                 { get; set; }
    [JsonPropertyName("subtype")]                public string?  Subtype                { get; set; }
    [JsonPropertyName("parent")]                 public string?  Parent                 { get; set; }
    [JsonPropertyName("requiresPassphrase")]     public bool?    RequiresPassphrase     { get; set; }
    [JsonPropertyName("requiresApplicationId")]  public bool?    RequiresApplicationId  { get; set; }
    [JsonExtensionData]                          public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Backtest job start response.</summary>
public sealed class BacktestStartResult
{
    [JsonPropertyName("ok")]      public bool?   Ok     { get; set; }
    [JsonPropertyName("job_id")] public string? JobId  { get; set; }
    [JsonPropertyName("count")]  public int?    Count  { get; set; }
    [JsonExtensionData]          public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Strategy create response.</summary>
public sealed class StrategyCreateResult
{
    [JsonPropertyName("ok")]         public bool?   Ok         { get; set; }
    [JsonPropertyName("strategyId")] public string? StrategyId { get; set; }
    [JsonPropertyName("status")]     public string? Status     { get; set; }
    [JsonExtensionData]              public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Ok/success response for strategy lifecycle calls.</summary>
public sealed class OkResult
{
    [JsonPropertyName("ok")]      public bool? Ok      { get; set; }
    [JsonPropertyName("success")] public bool? Success { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Response from sim cancel-order.</summary>
public sealed class SimOrderResult
{
    [JsonPropertyName("ok")]               public bool?   Ok             { get; set; }
    [JsonPropertyName("sim")]              public bool?   Sim            { get; set; }
    [JsonPropertyName("order_id")]         public string? OrderId        { get; set; }
    [JsonPropertyName("client_order_id")]  public string? ClientOrderId  { get; set; }
    [JsonPropertyName("symbol")]           public string? Symbol         { get; set; }
    [JsonPropertyName("side")]             public string? Side           { get; set; }
    [JsonPropertyName("amount")]           public double? Amount         { get; set; }
    [JsonPropertyName("fill_price")]       public double? FillPrice      { get; set; }
    [JsonPropertyName("notional_usd")]     public double? NotionalUsd    { get; set; }
    [JsonPropertyName("strategy_id")]      public string? StrategyId     { get; set; }
    [JsonExtensionData]                    public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>AI opt start response.</summary>
public sealed class AiOptStartResult
{
    [JsonPropertyName("ok")]    public bool?   Ok    { get; set; }
    [JsonPropertyName("runId")] public string? RunId { get; set; }
    [JsonExtensionData]         public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Backtest delete-all response.</summary>
public sealed class DeleteAllResult
{
    [JsonPropertyName("ok")]      public bool? Ok      { get; set; }
    [JsonPropertyName("deleted")] public int?  Deleted { get; set; }
    [JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}
