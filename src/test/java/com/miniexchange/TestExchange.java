package com.miniexchange;

import com.miniexchange.config.ExchangeConfig;
import com.miniexchange.config.SymbolConfig;
import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.Tag;

import java.util.List;
import java.util.Map;

/** Test fixture: an engine with one symbol D05 (ref 35.00, collar 10%, maxQty 100k, maxNotional 2M). */
public final class TestExchange {

    public static ExchangeConfig config() {
        return new ExchangeConfig(Map.of("D05", new SymbolConfig("D05", 3500, 10, 100_000, 200_000_000)));
    }

    public final MatchingEngine engine = new MatchingEngine(config());
    private long clock = 1_760_000_000_000L;

    public List<MatchingEngine.Outbound> send(String member, String raw) {
        return engine.process(new MatchingEngine.Inbound(member, raw, clock++));
    }

    public List<MatchingEngine.Outbound> buy(String member, String clOrdId, long qty, String price) {
        return send(member, newOrder(member, clOrdId, "1", qty, price));
    }

    public List<MatchingEngine.Outbound> sell(String member, String clOrdId, long qty, String price) {
        return send(member, newOrder(member, clOrdId, "2", qty, price));
    }

    public List<MatchingEngine.Outbound> cancel(String member, String clOrdId, String origClOrdId) {
        return send(member, "8=FIX.4.4|35=F|49=" + member + "|11=" + clOrdId + "|41=" + origClOrdId);
    }

    public static String newOrder(String member, String clOrdId, String side, long qty, String price) {
        return "8=FIX.4.4|35=D|49=" + member + "|11=" + clOrdId + "|55=D05|54=" + side
                + "|38=" + qty + "|40=2|44=" + price;
    }

    /** The single outbound message, asserting there is exactly one. */
    public static FixMessage only(List<MatchingEngine.Outbound> out) {
        if (out.size() != 1) {
            throw new AssertionError("Expected 1 outbound message, got " + out.size() + ": " + out);
        }
        return out.get(0).message();
    }

    public static List<FixMessage> to(String member, List<MatchingEngine.Outbound> out) {
        return out.stream().filter(o -> o.member().equals(member)).map(MatchingEngine.Outbound::message).toList();
    }

    public static List<FixMessage> trades(List<MatchingEngine.Outbound> out) {
        return out.stream().map(MatchingEngine.Outbound::message).filter(m -> "F".equals(m.get(Tag.EXEC_TYPE))).toList();
    }
}
