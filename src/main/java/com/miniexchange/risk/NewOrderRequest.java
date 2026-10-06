package com.miniexchange.risk;

import com.miniexchange.model.Side;

/** A NewOrderSingle that passed field validation. Price in ticks. */
public record NewOrderRequest(String member, String clOrdId, String symbol, Side side, long qty, long price) {}
