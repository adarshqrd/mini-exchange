package com.miniexchange.model;

/** An accepted order. Mutated only by the single engine thread. */
public final class Order {

    private final String orderId;
    private final String member;
    private final String clOrdId;
    private final String symbol;
    private final Side side;
    private final long price;
    private final long qty;
    private long cumQty;
    private boolean cancelled;

    public Order(String orderId, String member, String clOrdId, String symbol, Side side, long price, long qty) {
        this.orderId = orderId;
        this.member = member;
        this.clOrdId = clOrdId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.qty = qty;
    }

    public void fill(long fillQty) {
        cumQty += fillQty;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isOpen() {
        return !cancelled && leavesQty() > 0;
    }

    public long leavesQty() {
        return cancelled ? 0 : qty - cumQty;
    }

    /** FIX OrdStatus(39). */
    public String ordStatus() {
        if (cancelled) return "4";
        if (cumQty == qty) return "2";
        if (cumQty > 0) return "1";
        return "0";
    }

    public String orderId() { return orderId; }
    public String member() { return member; }
    public String clOrdId() { return clOrdId; }
    public String symbol() { return symbol; }
    public Side side() { return side; }
    public long price() { return price; }
    public long qty() { return qty; }
    public long cumQty() { return cumQty; }
}
