package com.miniexchange.config;

/** Per-symbol reference data and risk limits. Prices and notional are in ticks. */
public record SymbolConfig(String symbol, long refPrice, int collarPct, long maxQty, long maxNotional) {}
