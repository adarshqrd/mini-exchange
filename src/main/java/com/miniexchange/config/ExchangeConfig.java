package com.miniexchange.config;

import com.miniexchange.model.Prices;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Exchange configuration loaded from a properties file (NFR-3). */
public final class ExchangeConfig {

    private final Map<String, SymbolConfig> symbols;

    public ExchangeConfig(Map<String, SymbolConfig> symbols) {
        this.symbols = Collections.unmodifiableMap(new LinkedHashMap<>(symbols));
    }

    public SymbolConfig symbol(String symbol) {
        return symbols.get(symbol);
    }

    public Map<String, SymbolConfig> symbols() {
        return symbols;
    }

    public static ExchangeConfig load(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            Properties p = new Properties();
            p.load(r);
            return fromProperties(p);
        }
    }

    public static ExchangeConfig fromProperties(Properties p) {
        Map<String, SymbolConfig> symbols = new LinkedHashMap<>();
        for (String raw : require(p, "symbols").split(",")) {
            String s = raw.strip();
            String prefix = "symbol." + s + ".";
            symbols.put(s, new SymbolConfig(
                    s,
                    ticks(p, prefix + "refPrice"),
                    Integer.parseInt(require(p, prefix + "collarPct")),
                    Long.parseLong(require(p, prefix + "maxQty")),
                    ticks(p, prefix + "maxNotional")));
        }
        return new ExchangeConfig(symbols);
    }

    private static long ticks(Properties p, String key) {
        long t = Prices.toTicks(require(p, key));
        if (t <= 0) {
            throw new IllegalArgumentException("Invalid price for " + key);
        }
        return t;
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing config key " + key);
        }
        return v.strip();
    }
}
