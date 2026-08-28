"""Official Python SDK for the Melaya platform API.

Covers the full surface: platform/agents (projects, pipelines, HITL,
credentials, connectors, billing, templates, team, phone, assistant, evals,
bugs, runner) + real-time events (Socket.IO /api/v1/events) + trading preview
(market data, streaming, strategies, backtests, live trading, paper trading).

The client exposes three grouped namespaces as the primary API surface:

  melaya.trading   — market, account, sim, strategies, backtest, trade, stream
                     (preview — not for real funds)
  melaya.agents    — pipelines, hitl, assistant, phone, evals
  melaya.platform  — projects, credentials, connectors, billing, team,
                     templates, runner, auth, mfa, accounts, bugs, events

>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> # Namespaced (primary)
>>> m.trading.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
>>> m.agents.pipelines.list(project="my-project")
>>> m.agents.hitl.pending()
>>> m.platform.projects.list()
>>> # Flat aliases (backwards compatible)
>>> m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
>>> m.hitl.pending()
>>> # Real-time (async)
>>> import asyncio
>>> async def main():
...     await m.events.connect()
...     m.events.on_run_update("run-123", lambda e: print(e["event_type"]))
>>> asyncio.run(main())

See https://melaya.org/documentation
"""
from .client import Melaya, DEFAULT_BASE_URL, DEFAULT_WS_URL
from .client import TradingNamespace, AgentsNamespace, PlatformNamespace
from .errors import MelayaError

# Trading plane
from .market import MarketAPI
from .account import AccountAPI
from .sim import SimAPI
from .strategies import StrategiesAPI
from .backtest import BacktestAPI
from .trade import TradeAPI
from .stream import StreamAPI

# Platform / agents plane
from .auth import AuthAPI
from .mfa import MfaAPI
from .accounts import AccountsAPI
from .billing import BillingAPI
from .runner import RunnerAPI
from .projects import ProjectsAPI
from .pipelines import PipelinesAPI
from .hitl import HitlAPI
from .credentials import CredentialsAPI
from .connectors import ConnectorsAPI
from .phone import PhoneAPI
from .team import TeamAPI
from .templates import TemplatesAPI
from .assistant import AssistantAPI
from .bugs import BugsAPI
from .evals import EvalsAPI
from .events import MelayaEvents

__all__ = [
    # Core
    "Melaya",
    "MelayaError",
    "DEFAULT_BASE_URL",
    "DEFAULT_WS_URL",
    # Namespace classes (primary grouped API)
    "TradingNamespace",
    "AgentsNamespace",
    "PlatformNamespace",
    # Trading plane
    "MarketAPI",
    "AccountAPI",
    "SimAPI",
    "StrategiesAPI",
    "BacktestAPI",
    "TradeAPI",
    "StreamAPI",
    # Platform / agents plane
    "AuthAPI",
    "MfaAPI",
    "AccountsAPI",
    "BillingAPI",
    "RunnerAPI",
    "ProjectsAPI",
    "PipelinesAPI",
    "HitlAPI",
    "CredentialsAPI",
    "ConnectorsAPI",
    "PhoneAPI",
    "TeamAPI",
    "TemplatesAPI",
    "AssistantAPI",
    "BugsAPI",
    "EvalsAPI",
    "MelayaEvents",
]

__version__ = "0.2.0"
