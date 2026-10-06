package com.miniexchange.engine;

import com.miniexchange.TestExchange;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.miniexchange.TestExchange.only;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    private final TestExchange ex = new TestExchange();

    @Test
    @DisplayName("AC-01 valid limit order is acknowledged with ExecType=New")
    void acceptsValidOrder() {
        FixMessage r = only(ex.buy("A", "A1", 100, "35.00"));
        assertEquals("8", r.get(Tag.MSG_TYPE));
        assertEquals("0", r.get(Tag.EXEC_TYPE));
        assertEquals("0", r.get(Tag.ORD_STATUS));
        assertEquals("A1", r.get(Tag.CL_ORD_ID));
        assertEquals("100", r.get(Tag.LEAVES_QTY));
        assertEquals("A", r.get(Tag.TARGET_COMP_ID));
    }

    @ParameterizedTest(name = "AC-02 {0}")
    @DisplayName("AC-02 missing or invalid fields are rejected naming the problem")
    @CsvSource(delimiter = ';', value = {
            "missing ClOrdID;    8=FIX.4.4|35=D|49=A|55=D05|54=1|38=100|40=2|44=35.00;  Missing required field: ClOrdID(11)",
            "missing Symbol;     8=FIX.4.4|35=D|49=A|11=X|54=1|38=100|40=2|44=35.00;    Missing required field: Symbol(55)",
            "missing Price;      8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=100|40=2;      Missing required field: Price(44)",
            "missing SenderComp; 8=FIX.4.4|35=D|11=X|55=D05|54=1|38=100|40=2|44=35.00;  Missing required field: SenderCompID(49)",
            "zero qty;           8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=0|40=2|44=35.00;    Invalid OrderQty(38)",
            "negative qty;       8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=-5|40=2|44=35.00;   Invalid OrderQty(38)",
            "non-numeric qty;    8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=ten|40=2|44=35.00;  Invalid OrderQty(38)",
            "zero price;         8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=100|40=2|44=0;      Invalid Price(44)",
            "negative price;     8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=100|40=2|44=-1.00;  Invalid Price(44)",
            "off-tick price;     8=FIX.4.4|35=D|49=A|11=X|55=D05|54=1|38=100|40=2|44=35.001; Invalid Price(44)",
            "unknown side;       8=FIX.4.4|35=D|49=A|11=X|55=D05|54=7|38=100|40=2|44=35.00;  Invalid Side(54)",
    })
    void rejectsInvalidFields(String name, String raw, String expectedText) {
        FixMessage r = only(ex.send("A", raw));
        assertEquals("8", r.get(Tag.EXEC_TYPE));
        assertEquals("8", r.get(Tag.ORD_STATUS));
        assertEquals(expectedText, r.get(Tag.TEXT));
    }

    @Test
    @DisplayName("AC-03 non-limit order types are rejected")
    void rejectsMarketOrder() {
        FixMessage r = only(ex.send("A", "8=FIX.4.4|35=D|49=A|11=M1|55=D05|54=1|38=100|40=1"));
        assertEquals("8", r.get(Tag.EXEC_TYPE));
        assertEquals("Unsupported order type", r.get(Tag.TEXT));
    }

    @Test
    @DisplayName("AC-04 duplicate ClOrdID from the same member is rejected")
    void rejectsDuplicateClOrdId() {
        ex.buy("A", "A1", 100, "35.00");
        FixMessage r = only(ex.buy("A", "A1", 100, "35.00"));
        assertEquals("Duplicate ClOrdID", r.get(Tag.TEXT));
    }

    @Test
    @DisplayName("AC-04 (C-1) a rejected order's ClOrdID may be reused")
    void rejectedClOrdIdCanBeReused() {
        assertEquals("8", only(ex.buy("A", "A1", 100, "99.00")).get(Tag.EXEC_TYPE));   // collar reject
        assertEquals("0", only(ex.buy("A", "A1", 100, "35.00")).get(Tag.EXEC_TYPE));   // corrected resend
    }

    @Test
    @DisplayName("AC-04 same ClOrdID from a different member is allowed")
    void clOrdIdIsScopedPerMember() {
        ex.buy("A", "X1", 100, "35.00");
        assertEquals("0", only(ex.buy("B", "X1", 100, "34.90")).get(Tag.EXEC_TYPE));
    }

    @Test
    @DisplayName("AC-05 unknown symbol is rejected")
    void rejectsUnknownSymbol() {
        FixMessage r = only(ex.send("A", "8=FIX.4.4|35=D|49=A|11=U1|55=AAPL|54=1|38=100|40=2|44=35.00"));
        assertEquals("Unknown symbol", r.get(Tag.TEXT));
    }
}
