package com.miniexchange.fix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixCodecTest {

    @Test
    @DisplayName("§4 parses pipe-delimited messages")
    void parsesPipe() {
        FixMessage m = FixCodec.parse("8=FIX.4.4|35=D|11=A1|44=35.10");
        assertEquals("D", m.get(Tag.MSG_TYPE));
        assertEquals("35.10", m.get(Tag.PRICE));
    }

    @Test
    @DisplayName("§4 parses SOH-delimited messages")
    void parsesSoh() {
        FixMessage m = FixCodec.parse("8=FIX.4.4\u000135=D\u000111=A1\u0001");
        assertEquals("A1", m.get(Tag.CL_ORD_ID));
    }

    @Test
    @DisplayName("§4 encode then parse round-trips")
    void roundTrip() {
        FixMessage m = new FixMessage().set(8, "FIX.4.4").set(35, "8").set(58, "a b=c");
        assertEquals("8=FIX.4.4|35=8|58=a b=c", FixCodec.encode(m, FixCodec.PIPE));
        assertEquals("a b=c", FixCodec.parse(FixCodec.encode(m, FixCodec.SOH)).get(58));
    }

    @Test
    @DisplayName("NFR-2 malformed input raises FixParseException only")
    void malformed() {
        for (String bad : new String[]{"", "   ", "garbage", "35", "=D", "35=", "abc=1|35=D", "-1=x|35=D",
                "11=A1", "35=D|99999999999=x", "x".repeat(10_000)}) {
            assertThrows(FixParseException.class, () -> FixCodec.parse(bad), bad);
        }
        assertThrows(FixParseException.class, () -> FixCodec.parse(null));
    }
}
