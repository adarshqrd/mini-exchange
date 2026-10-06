package com.miniexchange.engine;

import com.miniexchange.TestExchange;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.Tag;
import com.miniexchange.model.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.miniexchange.TestExchange.only;
import static com.miniexchange.TestExchange.to;
import static com.miniexchange.TestExchange.trades;
import static org.junit.jupiter.api.Assertions.*;

class MatchingTest {

    private final TestExchange ex = new TestExchange();

    @Test
    @DisplayName("AC-20 best price matches first, then earliest arrival at the same price")
    void priceTimePriority() {
        ex.sell("A", "A1", 100, "35.02");   // worse price
        ex.sell("B", "B1", 100, "35.01");   // best price, later
        ex.sell("C", "C1", 100, "35.01");   // best price, latest
        List<FixMessage> fills = trades(ex.buy("D", "D1", 250, "35.02"));

        // reports alternate resting, incoming; resting side tells us who was filled in what order
        List<FixMessage> resting = fills.stream().filter(m -> !"D".equals(m.get(Tag.TARGET_COMP_ID))).toList();
        assertEquals(List.of("B", "C", "A"), resting.stream().map(m -> m.get(Tag.TARGET_COMP_ID)).toList());
        assertEquals(List.of("100", "100", "50"), resting.stream().map(m -> m.get(Tag.LAST_QTY)).toList());
    }

    @Test
    @DisplayName("AC-20 sell side: highest bid matches first")
    void sellMatchesHighestBid() {
        ex.buy("A", "A1", 100, "34.90");
        ex.buy("B", "B1", 100, "34.95");
        List<FixMessage> fills = trades(ex.sell("C", "C1", 100, "34.80"));
        assertEquals("B", fills.get(0).get(Tag.TARGET_COMP_ID));
        assertEquals("34.95", fills.get(0).get(Tag.LAST_PX));
    }

    @Test
    @DisplayName("AC-21 trades execute at the resting order's price")
    void tradesAtRestingPrice() {
        ex.sell("A", "A1", 100, "35.00");
        List<FixMessage> fills = trades(ex.buy("B", "B1", 100, "35.50"));
        assertEquals(2, fills.size());
        fills.forEach(f -> assertEquals("35.00", f.get(Tag.LAST_PX)));
    }

    @Test
    @DisplayName("AC-22 partially matched incoming order rests the remainder")
    void remainderRests() {
        ex.sell("A", "A1", 100, "35.00");
        ex.buy("B", "B1", 300, "35.00");
        assertEquals(200, ex.engine.book("D05").quantityAt(Side.BUY, 3500));
        assertNull(ex.engine.book("D05").bestAsk());
    }

    @Test
    @DisplayName("AC-22 non-crossing order rests without trading")
    void nonCrossingRests() {
        ex.sell("A", "A1", 100, "35.10");
        assertTrue(trades(ex.buy("B", "B1", 100, "35.00")).isEmpty());
        assertEquals(3500L, ex.engine.book("D05").bestBid());
        assertEquals(3510L, ex.engine.book("D05").bestAsk());
    }

    @Test
    @DisplayName("AC-23 both sides receive trade reports with correct quantities and status")
    void tradeReports() {
        ex.sell("A", "A1", 100, "35.00");
        List<com.miniexchange.engine.MatchingEngine.Outbound> out = ex.buy("B", "B1", 150, "35.00");

        FixMessage ack = to("B", out).get(0);
        assertEquals("0", ack.get(Tag.EXEC_TYPE));                // New first, then the trade

        FixMessage seller = only(out.stream().filter(o -> o.member().equals("A")).toList());
        assertEquals("F", seller.get(Tag.EXEC_TYPE));
        assertEquals("100", seller.get(Tag.LAST_QTY));
        assertEquals("35.00", seller.get(Tag.LAST_PX));
        assertEquals("100", seller.get(Tag.CUM_QTY));
        assertEquals("0", seller.get(Tag.LEAVES_QTY));
        assertEquals("2", seller.get(Tag.ORD_STATUS));            // filled

        FixMessage buyer = to("B", out).get(1);
        assertEquals("F", buyer.get(Tag.EXEC_TYPE));
        assertEquals("100", buyer.get(Tag.CUM_QTY));
        assertEquals("50", buyer.get(Tag.LEAVES_QTY));
        assertEquals("1", buyer.get(Tag.ORD_STATUS));             // partially filled
    }

    @Test
    @DisplayName("AC-23 cumulative quantities are correct across multiple fills")
    void cumulativeAcrossFills() {
        ex.sell("A", "A1", 100, "35.00");
        ex.sell("A", "A2", 100, "35.01");
        List<FixMessage> buyerFills = to("B", ex.buy("B", "B1", 200, "35.01")).stream()
                .filter(m -> "F".equals(m.get(Tag.EXEC_TYPE))).toList();
        assertEquals(List.of("100", "200"), buyerFills.stream().map(m -> m.get(Tag.CUM_QTY)).toList());
        assertEquals(List.of("100", "0"), buyerFills.stream().map(m -> m.get(Tag.LEAVES_QTY)).toList());
        assertEquals(List.of("1", "2"), buyerFills.stream().map(m -> m.get(Tag.ORD_STATUS)).toList());
    }

    @Test
    @DisplayName("NFR-1 book is never crossed under a random order stream")
    void neverCrossed() {
        Random rnd = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            String member = "M" + rnd.nextInt(5);
            long qty = 1 + rnd.nextInt(500);
            String price = String.format(Locale.ROOT, "%.2f", 34.00 + rnd.nextInt(201) / 100.0);
            if (rnd.nextInt(10) == 0) {
                ex.cancel(member, "X" + i, "C" + rnd.nextInt(i + 1));
            } else if (rnd.nextBoolean()) {
                ex.buy(member, "C" + i, qty, price);
            } else {
                ex.sell(member, "C" + i, qty, price);
            }
            assertFalse(ex.engine.book("D05").isCrossed(), "book crossed after event " + i);
        }
    }
}
