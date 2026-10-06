package com.miniexchange.book;

import com.miniexchange.model.Order;
import com.miniexchange.model.Side;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Limit order book for one symbol with price-time priority (AC-20..22).
 * Each side maps price → FIFO queue of orders at that price.
 */
public final class OrderBook {

    @FunctionalInterface
    public interface FillHandler {
        /** Called after each fill is applied to both orders. Trades at the resting price (AC-21). */
        void onFill(Order resting, Order incoming, long qty, long price);
    }

    private final String symbol;
    private final NavigableMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /** Matches the incoming order against the opposite side, then rests any remainder (AC-22). */
    public void match(Order incoming, FillHandler handler) {
        NavigableMap<Long, ArrayDeque<Order>> opposite = incoming.side() == Side.BUY ? asks : bids;
        while (incoming.leavesQty() > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, ArrayDeque<Order>> best = opposite.firstEntry();
            long levelPrice = best.getKey();
            boolean crosses = incoming.side() == Side.BUY ? levelPrice <= incoming.price() : levelPrice >= incoming.price();
            if (!crosses) {
                break;
            }
            ArrayDeque<Order> queue = best.getValue();
            Order resting = queue.peekFirst();
            long qty = Math.min(incoming.leavesQty(), resting.leavesQty());
            resting.fill(qty);
            incoming.fill(qty);
            handler.onFill(resting, incoming, qty, levelPrice);
            if (!resting.isOpen()) {
                queue.pollFirst();
            }
            if (queue.isEmpty()) {
                opposite.remove(levelPrice);
            }
        }
        if (incoming.isOpen()) {
            sideOf(incoming).computeIfAbsent(incoming.price(), p -> new ArrayDeque<>()).addLast(incoming);
        }
    }

    /** @return true if the order was resting and has been removed. */
    public boolean remove(Order order) {
        NavigableMap<Long, ArrayDeque<Order>> side = sideOf(order);
        ArrayDeque<Order> queue = side.get(order.price());
        if (queue == null || !queue.remove(order)) {
            return false;
        }
        if (queue.isEmpty()) {
            side.remove(order.price());
        }
        return true;
    }

    public Long bestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public Long bestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    /** NFR-1: the book must never be crossed. */
    public boolean isCrossed() {
        return bestBid() != null && bestAsk() != null && bestBid() >= bestAsk();
    }

    public long quantityAt(Side side, long price) {
        ArrayDeque<Order> q = (side == Side.BUY ? bids : asks).get(price);
        return q == null ? 0 : q.stream().mapToLong(Order::leavesQty).sum();
    }

    public String symbol() {
        return symbol;
    }

    private NavigableMap<Long, ArrayDeque<Order>> sideOf(Order o) {
        return o.side() == Side.BUY ? bids : asks;
    }
}
