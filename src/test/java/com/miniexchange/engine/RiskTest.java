package com.miniexchange.engine;

import com.miniexchange.TestExchange;
import com.miniexchange.config.SymbolConfig;
import com.miniexchange.fix.Tag;
import com.miniexchange.model.Side;
import com.miniexchange.risk.NewOrderRequest;
import com.miniexchange.risk.RiskChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.miniexchange.TestExchange.only;
import static org.junit.jupiter.api.Assertions.*;

class RiskTest {

    private final TestExchange ex = new TestExchange();

    @Test
    @DisplayName("AC-10 quantity above max order qty is rejected; at the limit is accepted")
    void maxQty() {
        assertEquals("Max order qty exceeded", only(ex.buy("A", "A1", 100_001, "35.00")).get(Tag.TEXT));

        // Boundary checked directly, with a notional limit high enough not to interfere.
        RiskChecker risk = new RiskChecker();
        SymbolConfig sc = new SymbolConfig("D05", 3500, 10, 100_000, Long.MAX_VALUE);
        assertNull(risk.check(new NewOrderRequest("A", "A2", "D05", Side.BUY, 100_000, 3500), sc));
    }

    @Test
    @DisplayName("AC-11 notional above max notional is rejected; at the limit is accepted")
    void maxNotional() {
        // max notional 2,000,000.00 ; 57,143 × 35.00 = 2,000,005.00
        assertEquals("Max notional exceeded", only(ex.buy("A", "A1", 57_143, "35.00")).get(Tag.TEXT));
        // 50,000 × 40.00 would be exactly 2M but is outside the collar, so use 40,000 × 35.00 = 1.4M
        assertEquals("0", only(ex.buy("A", "A2", 40_000, "35.00")).get(Tag.EXEC_TYPE));
        // exactly at the limit: 62,500 × 32.00 = 2,000,000.00
        assertEquals("0", only(ex.buy("A", "A3", 62_500, "32.00")).get(Tag.EXEC_TYPE));
    }

    @Test
    @DisplayName("AC-12 price outside the collar is rejected; on the boundary is accepted")
    void priceCollar() {
        // ref 35.00, collar 10% → accepted range 31.50 .. 38.50
        assertEquals("Price outside collar", only(ex.buy("A", "A1", 100, "38.51")).get(Tag.TEXT));
        assertEquals("Price outside collar", only(ex.sell("A", "A2", 100, "31.49")).get(Tag.TEXT));
        assertEquals("Price outside collar", only(ex.buy("A", "A3", 100, "350.00")).get(Tag.TEXT)); // fat finger
        assertEquals("0", only(ex.buy("A", "A4", 100, "31.50")).get(Tag.EXEC_TYPE));
        assertEquals("0", only(ex.sell("B", "B1", 100, "38.50")).get(Tag.EXEC_TYPE));
    }

    @Test
    @DisplayName("AC-13 a rejected order never reaches the book and never trades")
    void rejectedOrdersNeverTrade() {
        ex.sell("A", "A1", 100, "35.00");
        // B's buy would cross A's sell but breaches the collar: no trade, nothing rests
        assertEquals("Price outside collar", only(ex.buy("B", "B1", 100, "40.00")).get(Tag.TEXT));
        assertNull(ex.engine.book("D05").bestBid());
        assertEquals(100, ex.engine.book("D05").quantityAt(Side.SELL, 3500));
    }
}
