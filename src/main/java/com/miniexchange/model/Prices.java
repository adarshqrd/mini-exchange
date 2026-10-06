package com.miniexchange.model;

import java.math.BigDecimal;

/** Prices are held as long ticks of 0.01 to avoid floating-point error. */
public final class Prices {

    private Prices() {}

    /** @return price in ticks, or -1 if not a valid decimal on the 0.01 tick grid. */
    public static long toTicks(String price) {
        try {
            return new BigDecimal(price).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    public static String format(long ticks) {
        return BigDecimal.valueOf(ticks, 2).toPlainString();
    }
}
