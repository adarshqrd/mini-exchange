package com.miniexchange.risk;

import com.miniexchange.config.ExchangeConfig;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.Tag;
import com.miniexchange.model.Prices;
import com.miniexchange.model.Side;

import java.util.Set;

/** Field-level validation of NewOrderSingle (AC-01..05). */
public final class Validator {

    /** Exactly one of {@code request} / {@code rejectReason} is non-null. */
    public record Result(NewOrderRequest request, String rejectReason) {
        static Result ok(NewOrderRequest r) { return new Result(r, null); }
        static Result reject(String reason) { return new Result(null, reason); }
        public boolean isOk() { return request != null; }
    }

    private static final int[] REQUIRED = {Tag.SENDER_COMP_ID, Tag.CL_ORD_ID, Tag.SYMBOL, Tag.SIDE, Tag.ORDER_QTY, Tag.ORD_TYPE};

    private final ExchangeConfig config;

    public Validator(ExchangeConfig config) {
        this.config = config;
    }

    /**
     * @param member            the member the message came from
     * @param acceptedClOrdIds  ClOrdIDs of this member's previously accepted orders (AC-04)
     */
    public Result validate(FixMessage msg, String member, Set<String> acceptedClOrdIds) {
        for (int tag : REQUIRED) {
            if (!msg.has(tag)) {
                return Result.reject("Missing required field: " + name(tag) + "(" + tag + ")");
            }
        }
        if (!"2".equals(msg.get(Tag.ORD_TYPE))) {
            return Result.reject("Unsupported order type");
        }
        if (!msg.has(Tag.PRICE)) {
            return Result.reject("Missing required field: Price(44)");
        }
        Side side = Side.fromFix(msg.get(Tag.SIDE));
        if (side == null) {
            return Result.reject("Invalid Side(54)");
        }
        long qty = parsePositiveLong(msg.get(Tag.ORDER_QTY));
        if (qty <= 0) {
            return Result.reject("Invalid OrderQty(38)");
        }
        long price = Prices.toTicks(msg.get(Tag.PRICE));
        if (price <= 0) {
            return Result.reject("Invalid Price(44)");
        }
        String clOrdId = msg.get(Tag.CL_ORD_ID);
        if (acceptedClOrdIds.contains(clOrdId)) {
            return Result.reject("Duplicate ClOrdID");
        }
        String symbol = msg.get(Tag.SYMBOL);
        if (config.symbol(symbol) == null) {
            return Result.reject("Unknown symbol");
        }
        return Result.ok(new NewOrderRequest(member, clOrdId, symbol, side, qty, price));
    }

    private static long parsePositiveLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String name(int tag) {
        return switch (tag) {
            case Tag.SENDER_COMP_ID -> "SenderCompID";
            case Tag.CL_ORD_ID -> "ClOrdID";
            case Tag.SYMBOL -> "Symbol";
            case Tag.SIDE -> "Side";
            case Tag.ORDER_QTY -> "OrderQty";
            case Tag.ORD_TYPE -> "OrdType";
            default -> "Tag";
        };
    }
}
