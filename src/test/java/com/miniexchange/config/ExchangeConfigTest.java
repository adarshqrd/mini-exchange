package com.miniexchange.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeConfigTest {

    @Test
    @DisplayName("NFR-3 symbols and limits load from the config file")
    void loadsShippedConfig() throws Exception {
        ExchangeConfig c = ExchangeConfig.load(Path.of("config/exchange.properties"));
        SymbolConfig d05 = c.symbol("D05");
        assertEquals(3500, d05.refPrice());
        assertEquals(10, d05.collarPct());
        assertEquals(100_000, d05.maxQty());
        assertEquals(200_000_000, d05.maxNotional());
        assertNotNull(c.symbol("Z74"));
    }

    @Test
    @DisplayName("NFR-3 missing or invalid config fails fast at startup")
    void rejectsBadConfig() {
        Properties p = new Properties();
        p.setProperty("symbols", "D05");
        p.setProperty("symbol.D05.refPrice", "35.00");
        assertThrows(IllegalArgumentException.class, () -> ExchangeConfig.fromProperties(p));
    }
}
