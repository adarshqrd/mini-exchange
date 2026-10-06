package com.miniexchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatencyTest {

    @Test
    @DisplayName("NFR-4 inbound→ack latency is measured and p50/p99 reported")
    void measuresLatency() {
        Latency.Report r = Latency.measure(TestExchange.config(), 5_000, 20_000);
        System.out.println("NFR-4 " + r);
        assertEquals(20_000, r.samples());
        assertTrue(r.p50() > 0 && r.p50() <= r.p99() && r.p99() <= r.p999() && r.p999() <= r.max());
    }
}
