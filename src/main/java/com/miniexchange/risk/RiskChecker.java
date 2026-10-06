package com.miniexchange.risk;

import com.miniexchange.config.SymbolConfig;

/** Pre-trade risk checks (AC-10..12). */
public final class RiskChecker {

    /** @return a reject reason, or null if the order passes. */
    public String check(NewOrderRequest req, SymbolConfig sc) {
        if (req.qty() > sc.maxQty()) {
            return "Max order qty exceeded";
        }
        if (notionalExceeds(req.qty(), req.price(), sc.maxNotional())) {
            return "Max notional exceeded";
        }
        // |price - ref| / ref > collar%  ⇔  |price - ref| * 100 > ref * collar%  (exact integer math)
        if (Math.abs(req.price() - sc.refPrice()) * 100 > sc.refPrice() * sc.collarPct()) {
            return "Price outside collar";
        }
        return null;
    }

    private static boolean notionalExceeds(long qty, long price, long max) {
        try {
            return Math.multiplyExact(qty, price) > max;
        } catch (ArithmeticException overflow) {
            return true;
        }
    }
}
