package com.miniexchange.engine;

import com.miniexchange.TestExchange;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.Tag;
import com.miniexchange.model.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.miniexchange.TestExchange.only;
import static org.junit.jupiter.api.Assertions.*;

class CancelTest {

    private final TestExchange ex = new TestExchange();

    @Test
    @DisplayName("AC-30 cancelling a resting order removes it and confirms with ExecType=Canceled")
    void cancelResting() {
        ex.buy("A", "A1", 100, "35.00");
        FixMessage r = only(ex.cancel("A", "A2", "A1"));
        assertEquals("8", r.get(Tag.MSG_TYPE));
        assertEquals("4", r.get(Tag.EXEC_TYPE));
        assertEquals("4", r.get(Tag.ORD_STATUS));
        assertEquals("A2", r.get(Tag.CL_ORD_ID));
        assertEquals("A1", r.get(Tag.ORIG_CL_ORD_ID));
        assertEquals("0", r.get(Tag.LEAVES_QTY));
        assertNull(ex.engine.book("D05").bestBid());
    }

    @Test
    @DisplayName("AC-30 cancelling a partially filled order cancels the remainder")
    void cancelPartiallyFilled() {
        ex.buy("A", "A1", 300, "35.00");
        ex.sell("B", "B1", 100, "35.00");
        FixMessage r = only(ex.cancel("A", "A2", "A1"));
        assertEquals("4", r.get(Tag.EXEC_TYPE));
        assertEquals("100", r.get(Tag.CUM_QTY));
        assertEquals(0, ex.engine.book("D05").quantityAt(Side.BUY, 3500));
    }

    @Test
    @DisplayName("AC-31 cancel of unknown, filled or already-cancelled order is rejected; book unchanged")
    void cancelRejects() {
        FixMessage unknown = only(ex.cancel("A", "X1", "NOPE"));
        assertEquals("9", unknown.get(Tag.MSG_TYPE));
        assertEquals("Unknown order", unknown.get(Tag.TEXT));

        ex.sell("A", "A1", 100, "35.00");
        ex.buy("B", "B1", 100, "35.00");                   // fills A1
        FixMessage filled = only(ex.cancel("A", "X2", "A1"));
        assertEquals("9", filled.get(Tag.MSG_TYPE));
        assertEquals("2", filled.get(Tag.ORD_STATUS));

        ex.sell("A", "A3", 100, "35.10");
        ex.cancel("A", "X3", "A3");
        FixMessage twice = only(ex.cancel("A", "X4", "A3"));
        assertEquals("9", twice.get(Tag.MSG_TYPE));
        assertEquals("Order not open", twice.get(Tag.TEXT));

        assertNull(ex.engine.book("D05").bestAsk());
        assertNull(ex.engine.book("D05").bestBid());
    }

    @Test
    @DisplayName("AC-31 cancel missing OrigClOrdID is rejected")
    void cancelMissingField() {
        FixMessage r = only(ex.send("A", "8=FIX.4.4|35=F|49=A|11=X1"));
        assertEquals("9", r.get(Tag.MSG_TYPE));
        assertEquals("Missing required field: OrigClOrdID(41)", r.get(Tag.TEXT));
    }

    @Test
    @DisplayName("AC-32 a member cannot cancel another member's order")
    void cannotCancelOthersOrder() {
        ex.buy("A", "A1", 100, "35.00");
        FixMessage r = only(ex.cancel("B", "B9", "A1"));
        assertEquals("9", r.get(Tag.MSG_TYPE));
        assertEquals("Unknown order", r.get(Tag.TEXT));
        assertEquals("B", r.get(Tag.TARGET_COMP_ID));
        assertEquals(100, ex.engine.book("D05").quantityAt(Side.BUY, 3500));
    }
}
