package com.miniexchange;

import com.miniexchange.config.ExchangeConfig;
import com.miniexchange.engine.MatchingEngine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * NFR-4: measures in-process engine latency (inbound FIX string → outbound reports),
 * including FIX parsing, validation, risk and matching. Excludes network and audit I/O.
 */
public final class Latency {

    public record Report(int samples, long p50, long p99, long p999, long max) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%,d orders  p50=%.1fµs  p99=%.1fµs  p99.9=%.1fµs  max=%.1fµs",
                    samples, p50 / 1e3, p99 / 1e3, p999 / 1e3, max / 1e3);
        }
    }

    private Latency() {}

    public static Report measure(ExchangeConfig config, int warmup, int samples) {
        MatchingEngine engine = new MatchingEngine(config);
        String symbol = config.symbols().keySet().iterator().next();
        long ref = config.symbol(symbol).refPrice();
        Random rnd = new Random(99);
        long[] nanos = new long[samples];
        for (int i = 0; i < warmup + samples; i++) {
            String member = "M" + rnd.nextInt(10);
            long price = ref - 50 + rnd.nextInt(101);   // ±0.50 around ref → frequent matches
            String raw = "8=FIX.4.4|35=D|49=" + member + "|11=C" + i + "|55=" + symbol
                    + "|54=" + (rnd.nextBoolean() ? "1" : "2") + "|38=" + (1 + rnd.nextInt(500))
                    + "|40=2|44=" + (price / 100) + "." + String.format(Locale.ROOT, "%02d", price % 100);
            long t0 = System.nanoTime();
            engine.process(new MatchingEngine.Inbound(member, raw, i));
            long dt = System.nanoTime() - t0;
            if (i >= warmup) {
                nanos[i - warmup] = dt;
            }
        }
        Arrays.sort(nanos);
        return new Report(samples, pct(nanos, 50), pct(nanos, 99), pct(nanos, 99.9), nanos[samples - 1]);
    }

    private static long pct(long[] sorted, double p) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100 * sorted.length) - 1)];
    }

    public static void main(String[] args) throws Exception {
        ExchangeConfig config = ExchangeConfig.load(Path.of(args.length > 0 ? args[0] : "config/exchange.properties"));
        System.out.println("Engine latency (in-process, single thread):");
        System.out.println("  " + measure(config, 50_000, 200_000));
    }
}
