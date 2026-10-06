package com.miniexchange.engine;

import com.miniexchange.book.OrderBook;
import com.miniexchange.config.ExchangeConfig;
import com.miniexchange.config.SymbolConfig;
import com.miniexchange.fix.FixCodec;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.FixParseException;
import com.miniexchange.fix.Tag;
import com.miniexchange.model.Order;
import com.miniexchange.model.Prices;
import com.miniexchange.risk.NewOrderRequest;
import com.miniexchange.risk.RiskChecker;
import com.miniexchange.risk.Validator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The exchange core: inbound FIX → validate → risk → match/cancel → outbound reports.
 *
 * <p>Single-threaded and deterministic: output depends only on the sequence of
 * inbound messages (including their timestamps), never on wall-clock time or
 * randomness. This is what makes audit replay exact (AC-41).
 */
public final class MatchingEngine {

    /** An inbound message as sequenced by the gateway. {@code timestamp} is epoch millis. */
    public record Inbound(String member, String raw, long timestamp) {}

    public record Outbound(String member, FixMessage message) {}

    public static final String EXCHANGE_ID = "MINIEX";
    private static final DateTimeFormatter FIX_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final ExchangeConfig config;
    private final Validator validator;
    private final RiskChecker riskChecker = new RiskChecker();
    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<String, Map<String, Order>> ordersByMember = new HashMap<>();
    private long nextOrderId = 1;
    private long nextExecId = 1;

    public MatchingEngine(ExchangeConfig config) {
        this.config = config;
        this.validator = new Validator(config);
        config.symbols().keySet().forEach(s -> books.put(s, new OrderBook(s)));
    }

    public List<Outbound> process(Inbound in) {
        List<Outbound> out = new ArrayList<>();
        String ts = FIX_TIME.format(Instant.ofEpochMilli(in.timestamp()));
        FixMessage msg;
        try {
            msg = FixCodec.parse(in.raw());
        } catch (FixParseException e) {
            out.add(new Outbound(in.member(), orderReject(in.member(), new FixMessage(), "Malformed message: " + e.getMessage(), ts)));
            return out;
        }
        switch (msg.get(Tag.MSG_TYPE)) {
            case "D" -> onNewOrder(in.member(), msg, ts, out);
            case "F" -> onCancel(in.member(), msg, ts, out);
            default -> out.add(new Outbound(in.member(),
                    orderReject(in.member(), msg, "Unsupported MsgType(35)=" + msg.get(Tag.MSG_TYPE), ts)));
        }
        return out;
    }

    public OrderBook book(String symbol) {
        return books.get(symbol);
    }

    private void onNewOrder(String member, FixMessage msg, String ts, List<Outbound> out) {
        Map<String, Order> memberOrders = ordersByMember.computeIfAbsent(member, m -> new HashMap<>());

        Validator.Result v = validator.validate(msg, member, memberOrders.keySet());
        if (!v.isOk()) {
            out.add(new Outbound(member, orderReject(member, msg, v.rejectReason(), ts)));
            return;
        }
        NewOrderRequest req = v.request();
        SymbolConfig sc = config.symbol(req.symbol());
        String riskReject = riskChecker.check(req, sc);
        if (riskReject != null) {
            out.add(new Outbound(member, orderReject(member, msg, riskReject, ts)));   // AC-13: never reaches the book
            return;
        }

        Order order = new Order("O" + nextOrderId++, member, req.clOrdId(), req.symbol(), req.side(), req.price(), req.qty());
        memberOrders.put(order.clOrdId(), order);
        out.add(new Outbound(member, execReport(order, "0", order.clOrdId(), ts)));      // AC-01

        books.get(order.symbol()).match(order, (resting, incoming, qty, price) -> {      // AC-23
            out.add(new Outbound(resting.member(), tradeReport(resting, qty, price, ts)));
            out.add(new Outbound(incoming.member(), tradeReport(incoming, qty, price, ts)));
        });
    }

    private void onCancel(String member, FixMessage msg, String ts, List<Outbound> out) {
        String clOrdId = msg.get(Tag.CL_ORD_ID);
        String origClOrdId = msg.get(Tag.ORIG_CL_ORD_ID);
        if (clOrdId == null || origClOrdId == null) {
            out.add(new Outbound(member, cancelReject(member, null, clOrdId, origClOrdId, "1",
                    "Missing required field: " + (clOrdId == null ? "ClOrdID(11)" : "OrigClOrdID(41)"), ts)));
            return;
        }
        // Lookup is scoped to the requesting member, so other members' orders are "unknown" (AC-32).
        Order order = ordersByMember.getOrDefault(member, Map.of()).get(origClOrdId);
        if (order == null) {
            out.add(new Outbound(member, cancelReject(member, null, clOrdId, origClOrdId, "1", "Unknown order", ts)));
            return;
        }
        if (!order.isOpen()) {
            out.add(new Outbound(member, cancelReject(member, order, clOrdId, origClOrdId, "0", "Order not open", ts)));  // AC-31
            return;
        }
        books.get(order.symbol()).remove(order);
        order.cancel();
        out.add(new Outbound(member, execReport(order, "4", clOrdId, ts).set(Tag.ORIG_CL_ORD_ID, origClOrdId)));  // AC-30
    }

    // ---- outbound message builders ----

    private FixMessage header(String msgType, String member) {
        return new FixMessage()
                .set(Tag.BEGIN_STRING, "FIX.4.4")
                .set(Tag.MSG_TYPE, msgType)
                .set(Tag.SENDER_COMP_ID, EXCHANGE_ID)
                .set(Tag.TARGET_COMP_ID, member);
    }

    private FixMessage execReport(Order o, String execType, String clOrdId, String ts) {
        return header("8", o.member())
                .set(Tag.ORDER_ID, o.orderId())
                .set(Tag.CL_ORD_ID, clOrdId)
                .set(Tag.EXEC_ID, "E" + nextExecId++)
                .set(Tag.EXEC_TYPE, execType)
                .set(Tag.ORD_STATUS, o.ordStatus())
                .set(Tag.SYMBOL, o.symbol())
                .set(Tag.SIDE, o.side().fixCode())
                .set(Tag.ORDER_QTY, o.qty())
                .set(Tag.PRICE, Prices.format(o.price()))
                .set(Tag.CUM_QTY, o.cumQty())
                .set(Tag.LEAVES_QTY, o.leavesQty())
                .set(Tag.TRANSACT_TIME, ts);
    }

    private FixMessage tradeReport(Order o, long qty, long price, String ts) {
        return execReport(o, "F", o.clOrdId(), ts)
                .set(Tag.LAST_QTY, qty)
                .set(Tag.LAST_PX, Prices.format(price));
    }

    /** ExecutionReport ExecType=Rejected, echoing whatever identifying fields the inbound message had. */
    private FixMessage orderReject(String member, FixMessage in, String reason, String ts) {
        FixMessage m = header("8", member)
                .set(Tag.ORDER_ID, "NONE")
                .set(Tag.CL_ORD_ID, orDefault(in.get(Tag.CL_ORD_ID), "NONE"))
                .set(Tag.EXEC_ID, "E" + nextExecId++)
                .set(Tag.EXEC_TYPE, "8")
                .set(Tag.ORD_STATUS, "8");
        for (int tag : new int[]{Tag.SYMBOL, Tag.SIDE, Tag.ORDER_QTY, Tag.PRICE}) {
            if (in.has(tag)) {
                m.set(tag, in.get(tag));
            }
        }
        return m.set(Tag.CUM_QTY, 0)
                .set(Tag.LEAVES_QTY, 0)
                .set(Tag.TEXT, reason)
                .set(Tag.TRANSACT_TIME, ts);
    }

    private FixMessage cancelReject(String member, Order order, String clOrdId, String origClOrdId,
                                    String cxlRejReason, String text, String ts) {
        return header("9", member)
                .set(Tag.ORDER_ID, order == null ? "NONE" : order.orderId())
                .set(Tag.CL_ORD_ID, orDefault(clOrdId, "NONE"))
                .set(Tag.ORIG_CL_ORD_ID, orDefault(origClOrdId, "NONE"))
                .set(Tag.ORD_STATUS, order == null ? "8" : order.ordStatus())
                .set(Tag.CXL_REJ_RESPONSE_TO, "1")
                .set(Tag.CXL_REJ_REASON, cxlRejReason)
                .set(Tag.TEXT, text)
                .set(Tag.TRANSACT_TIME, ts);
    }

    private static String orDefault(String v, String dflt) {
        return v == null ? dflt : v;
    }
}
