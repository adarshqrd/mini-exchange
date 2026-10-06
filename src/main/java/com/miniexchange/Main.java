package com.miniexchange;

import com.miniexchange.audit.AuditLog;
import com.miniexchange.audit.Replayer;
import com.miniexchange.config.ExchangeConfig;
import com.miniexchange.engine.EngineRunner;
import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.gateway.TcpGateway;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

/**
 * Usage:
 *   Main [port] [config]            start the exchange (default 9878, config/exchange.properties)
 *   Main replay <audit-file> [config]   verify an audit log replays identically (AC-41)
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("replay")) {
            replay(Path.of(args[1]), Path.of(args.length > 2 ? args[2] : "config/exchange.properties"));
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9878;
        ExchangeConfig config = ExchangeConfig.load(Path.of(args.length > 1 ? args[1] : "config/exchange.properties"));
        Path auditFile = Path.of("audit", "audit-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log");

        TcpGateway gateway = new TcpGateway(port);
        EngineRunner runner = new EngineRunner(new MatchingEngine(config), AuditLog.open(auditFile), gateway).start();
        gateway.start(runner::submit);

        System.out.println("mini-exchange listening on port " + gateway.port());
        System.out.println("  symbols: " + config.symbols().keySet());
        System.out.println("  audit:   " + auditFile);
        System.out.println("Ctrl+C to stop.");

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                gateway.close();
                runner.close();
                System.out.println("\nStopped. Verify with: replay " + auditFile);
            } catch (Exception e) {
                System.err.println("shutdown error: " + e);
            }
            stopped.countDown();
        }));
        stopped.await();
    }

    private static void replay(Path auditFile, Path configFile) throws Exception {
        Replayer.Result r = Replayer.replay(AuditLog.read(auditFile), ExchangeConfig.load(configFile));
        System.out.printf("Replayed %d inbound messages, compared %d outbound messages%n", r.inbound(), r.outbound());
        if (r.identical()) {
            System.out.println("✅ IDENTICAL — engine output is fully reproducible from the audit log");
        } else {
            System.out.println("❌ MISMATCH at " + r.firstDifference());
            System.exit(1);
        }
    }
}
