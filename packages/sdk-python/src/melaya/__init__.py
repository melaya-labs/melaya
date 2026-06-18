"""Official Python SDK for the Melaya unified market-data & streaming API.

>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")

See https://melaya.org/docs
"""
from .client import Melaya, DEFAULT_BASE_URL, DEFAULT_WS_URL
from .errors import MelayaError

__all__ = ["Melaya", "MelayaError", "DEFAULT_BASE_URL", "DEFAULT_WS_URL"]
__version__ = "0.1.0"
