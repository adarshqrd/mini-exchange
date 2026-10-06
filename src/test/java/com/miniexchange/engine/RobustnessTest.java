package com.miniexchange.engine;

import com.miniexchange.TestExchange;
import com.miniexchange.fix.FixMessage;
import com.miniexchange.fix.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.miniexchange.TestExchange.only;
import static org.junit.jupiter.api.Assertions.*;

class RobustnessTest {

    private final TestExchange ex = new TestExchange();

    @Test
    @DisplayName("NFR-2 malformed messages are rejected, not thrown")
    void malformedIsRejected() {
        FixMessage r = only(ex.send("A", "this is not fix"));
        assertEquals("8", r.get(Tag.EXEC_TYPE));
        assertTrue(r.get(Tag.TEXT).startsWith("Malformed message"));

        assertEquals("Unsupported MsgType(35)=G", only(ex.send("A", "8=FIX.4.4|35=G|49=A|11=X")).get(Tag.TEXT));
    }

    @Test
    @DisplayName("NFR-2 fuzzed input never throws and other members keep trading")
    void fuzz() {
        Random rnd = new Random(1234);
        String[] fragments = {"35=D", "35=F", "35=8", "49=A", "11=", "11=X", "55=D05", "55=", "54=1", "54=9",
                "38=100", "38=-1", "38=99999999999999999999", "40=2", "40=1", "44=35.00", "44=abc", "44=1e400",
                "41=X", "=", "|", "\u0001", "==", "8=FIX.4.4", "999=z", "\t", " "};
        for (int i = 0; i < 50_000; i++) {
            StringBuilder sb = new StringBuilder();
            int n = rnd.nextInt(10);
            for (int j = 0; j < n; j++) {
                sb.append(fragments[rnd.nextInt(fragments.length)]).append(rnd.nextBoolean() ? "|" : "");
            }
            String raw = sb.toString();
            assertDoesNotThrow(() -> ex.send("F", raw), raw);
        }
        // the exchange still works for a well-behaved member
        assertEquals("0", ex.sell("GOOD", "S1", 100, "38.00").get(0).message().get(Tag.EXEC_TYPE));
    }
}
