// Package melaya — shared types mirroring the TypeScript/Python SDKs.
package melaya

// ── REST inputs ──────────────────────────────────────────────────────────────

// SymbolQuery is the base query type for single-symbol endpoints.
type SymbolQuery struct {
	Exchange string `json:"exchange"`
	Symbol   string `json:"symbol"`
	Market   string `json:"market,omitempty"`
}

// OrderBookQuery extends SymbolQuery with an optional depth limit.
type OrderBookQuery struct {
	SymbolQuery
	Limit int `json:"limit,omitempty"`
}

// OhlcvQuery extends SymbolQuery with a required timeframe and optional limit.
type OhlcvQuery struct {
	SymbolQuery
	Timeframe string `json:"timeframe"`
	Limit     int    `json:"limit,omitempty"`
}

// ExchangeQuery scopes a request to one exchange.
type ExchangeQuery struct {
	Exchange string `json:"exchange"`
}

// ── Normalized REST responses ─────────────────────────────────────────────────

// Ticker is a normalized best-bid/ask + 24 h aggregate.
type Ticker struct {
	Exchange    string   `json:"exchange,omitempty"`
	Symbol      string   `json:"symbol,omitempty"`
	Bid         *float64 `json:"bid,omitempty"`
	Ask         *float64 `json:"ask,omitempty"`
	Last        *float64 `json:"last,omitempty"`
	High        *float64 `json:"high,omitempty"`
	Low         *float64 `json:"low,omitempty"`
	BaseVolume  *float64 `json:"baseVolume,omitempty"`
	QuoteVolume *float64 `json:"quoteVolume,omitempty"`
	Timestamp   *int64   `json:"timestamp,omitempty"`
}

// BookLevel is a single order-book level: [price, amount].
type BookLevel [2]float64

// OrderBook is a normalized order book snapshot.
type OrderBook struct {
	Exchange  string      `json:"exchange,omitempty"`
	Symbol    string      `json:"symbol,omitempty"`
	Bids      []BookLevel `json:"bids"`
	Asks      []BookLevel `json:"asks"`
	Timestamp *int64      `json:"timestamp,omitempty"`
}

// Candle is a single OHLCV candle. The API returns each candle as an object
// (keys: t, o, h, l, c, v plus venue-specific extras), so it is kept as a
// flexible map rather than a fixed array.
type Candle map[string]interface{}

// Trade is a normalized public trade.
type Trade struct {
	ID        string   `json:"id,omitempty"`
	Timestamp *int64   `json:"timestamp,omitempty"`
	Side      string   `json:"side,omitempty"`
	Price     *float64 `json:"price,omitempty"`
	Amount    *float64 `json:"amount,omitempty"`
}

// Liquidation is a normalized liquidation event.
type Liquidation struct {
	Exchange  string   `json:"exchange"`
	Symbol    string   `json:"symbol,omitempty"`
	Side      string   `json:"side,omitempty"`
	Price     *float64 `json:"price,omitempty"`
	Amount    *float64 `json:"amount,omitempty"`
	Notional  *float64 `json:"notional,omitempty"`
	Timestamp *int64   `json:"timestamp,omitempty"`
}

// ExchangeStatus is the operational status of one exchange.
type ExchangeStatus struct {
	Exchange string `json:"exchange"`
	Status   string `json:"status"`
}

// ExchangeInfo describes a Melaya-supported exchange.
type ExchangeInfo struct {
	ID                     string  `json:"id"`
	Display                string  `json:"display,omitempty"`
	Market                 string  `json:"market,omitempty"`
	Subtype                string  `json:"subtype,omitempty"`
	Parent                 *string `json:"parent,omitempty"`
	RequiresPassphrase     bool    `json:"requiresPassphrase,omitempty"`
	RequiresApplicationID  bool    `json:"requiresApplicationId,omitempty"`
}

// ── Trading plane (authenticated) ─────────────────────────────────────────────

// ConnectedKey is an exchange API key connected to the Melaya account.
type ConnectedKey struct {
	ID         string `json:"id"`
	APIKeyID   string `json:"apiKeyId"`
	APIKey     string `json:"apiKey,omitempty"`
	Exchange   string `json:"exchange"`
	Label      string `json:"label,omitempty"`
	Market     string `json:"market,omitempty"`
	Privileges []string `json:"privileges,omitempty"`
	IPMode     string `json:"ipMode,omitempty"`
}

// UsageMetric is one usage/limit metric.
type UsageMetric struct {
	Key     string   `json:"key"`
	Label   string   `json:"label"`
	Current float64  `json:"current"`
	Limit   *float64 `json:"limit"`
	Tracked bool     `json:"tracked,omitempty"`
	Unit    string   `json:"unit,omitempty"`
}

// UsageSummary is the tier + all usage metrics.
type UsageSummary struct {
	Tier                  string        `json:"tier"`
	MultiEntityWorkspaces bool          `json:"multiEntityWorkspaces,omitempty"`
	LogRetentionDays      *int          `json:"logRetentionDays,omitempty"`
	Metrics               []UsageMetric `json:"metrics"`
}

// SimBalance is a virtual paper-wallet balance for a strategy.
type SimBalance struct {
	Asset           string  `json:"asset"`
	StartingEquity  float64 `json:"starting_equity"`
	RealizedPnL     float64 `json:"realized_pnl"`
	UnrealizedPnL   float64 `json:"unrealized_pnl"`
	Used            float64 `json:"used"`
	Free            float64 `json:"free"`
	Total           float64 `json:"total"`
	StrategyID      string  `json:"strategy_id"`
	Sim             bool    `json:"sim"`
}

// SimOrderResult is the result of placing a paper order.
type SimOrderResult struct {
	Ok             bool    `json:"ok"`
	Sim            bool    `json:"sim"`
	OrderID        string  `json:"order_id"`
	ClientOrderID  string  `json:"client_order_id,omitempty"`
	Symbol         string  `json:"symbol"`
	Side           string  `json:"side"`
	Amount         float64 `json:"amount"`
	FillPrice      *float64 `json:"fill_price,omitempty"`
	NotionalUSD    *float64 `json:"notional_usd,omitempty"`
	StrategyID     string  `json:"strategy_id,omitempty"`
}

// SimCreateOrder is the body for placing a paper order.
type SimCreateOrder struct {
	StrategyID    string                 `json:"strategy_id"`
	Exchange      string                 `json:"exchange"`
	Symbol        string                 `json:"symbol"`
	Side          string                 `json:"side"`
	Amount        float64                `json:"amount"`
	Type          string                 `json:"order_type,omitempty"`
	Price         *float64               `json:"price,omitempty"`
	Market        string                 `json:"market,omitempty"`
	Leverage      *float64               `json:"leverage,omitempty"`
	ReduceOnly    bool                   `json:"reduceOnly,omitempty"`
	SLPrice       *float64               `json:"slPrice,omitempty"`
	TPPrice       *float64               `json:"tpPrice,omitempty"`
	ClientOrderID string                 `json:"client_order_id,omitempty"`
	Params        map[string]interface{} `json:"params,omitempty"`
}

// Strategy describes a Melaya trading strategy.
type Strategy struct {
	StrategyID   string `json:"strategyId"`
	Name         string `json:"name,omitempty"`
	StrategyType string `json:"strategyType,omitempty"`
	Status       string `json:"status,omitempty"`
	Exchange     string `json:"exchange,omitempty"`
	Symbol       string `json:"symbol,omitempty"`
	Market       string `json:"market,omitempty"`
	DryRun       bool   `json:"dryRun,omitempty"`
	RuntimeMode  string `json:"runtimeMode,omitempty"`
}

// StrategyCreate is the body for launching a strategy.
type StrategyCreate struct {
	Name         string                 `json:"name"`
	StrategyType string                 `json:"strategyType"`
	Exchange     string                 `json:"exchange"`
	Market       string                 `json:"market,omitempty"`
	Symbol       string                 `json:"symbol,omitempty"`
	APIKeyID     string                 `json:"apiKeyId,omitempty"`
	Params       map[string]interface{} `json:"params,omitempty"`
	RuntimeMode  string                 `json:"runtimeMode,omitempty"`
	DryRun       bool                   `json:"dryRun,omitempty"`
	KeyBindings  map[string]interface{} `json:"keyBindings,omitempty"`
	// Extra fields (e.g. language/definition for custom strategies).
	Extra        map[string]interface{} `json:"-"`
}

// StrategyCreateResult is returned by strategies.Create.
type StrategyCreateResult struct {
	Ok         bool   `json:"ok"`
	StrategyID string `json:"strategyId"`
	Status     string `json:"status"`
}

// BacktestStart is the body for starting a backtest.
type BacktestStart struct {
	StrategyType  string                 `json:"strategyType"`
	Exchange      string                 `json:"exchange"`
	Symbol        string                 `json:"symbol"`
	Timeframe     string                 `json:"timeframe"`
	SinceMS       *int64                 `json:"since_ms,omitempty"`
	UntilMS       *int64                 `json:"until_ms,omitempty"`
	InitialEquity *float64               `json:"initial_equity,omitempty"`
	Params        map[string]interface{} `json:"params,omitempty"`
	Mode          string                 `json:"mode,omitempty"`
	ParamRanges   map[string]interface{} `json:"paramRanges,omitempty"`
	RandomSamples *int                   `json:"randomSamples,omitempty"`
	Language      string                 `json:"language,omitempty"`
	Definition    string                 `json:"definition,omitempty"`
}

// BacktestJob describes the status of a backtest job.
type BacktestJob struct {
	Ok           bool    `json:"ok"`
	JobID        string  `json:"job_id"`
	Status       string  `json:"status"`
	ProgressPct  float64 `json:"progress_pct,omitempty"`
	ErrorMsg     string  `json:"error_msg,omitempty"`
	StrategyType string  `json:"strategy_type,omitempty"`
	Exchange     string  `json:"exchange,omitempty"`
	Symbol       string  `json:"symbol,omitempty"`
	Timeframe    string  `json:"timeframe,omitempty"`
}

// BacktestResult holds metrics and equity curve for a completed backtest.
type BacktestResult struct {
	Metrics          map[string]interface{} `json:"metrics,omitempty"`
	EquityCurve      []float64              `json:"equity_curve,omitempty"`
	EquityTimestamps []int64                `json:"equity_timestamps,omitempty"`
	OHLCV            interface{}            `json:"ohlcv,omitempty"`
}

// CatalogCounts is returned by market.CatalogCounts.
type CatalogCounts struct {
	Tools      int         `json:"tools"`
	Subagents  int         `json:"subagents"`
	ByCategory interface{} `json:"byCategory,omitempty"`
}
