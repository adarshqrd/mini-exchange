package com.miniexchange.engine;

import com.miniexchange.audit.AuditLog;
import com.miniexchange.fix.FixCodec;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Runs the engine on a single thread. Gateway threads only enqueue; this thread
 * sequences, audits, processes and dispatches. Outbound messages are audited and
 * flushed before they are sent (AC-40).
 */
public final class EngineRunner implements AutoCloseable {

    @FunctionalInterface
    public interface OutboundSink {
        void send(String member, String fixLine);
    }

    private final MatchingEngine engine;
    private final AuditLog audit;
    private final OutboundSink sink;
    private final BlockingQueue<MatchingEngine.Inbound> queue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private static final MatchingEngine.Inbound POISON = new MatchingEngine.Inbound(null, null, 0);

    public EngineRunner(MatchingEngine engine, AuditLog audit, OutboundSink sink) {
        this.engine = engine;
        this.audit = audit;
        this.sink = sink;
        this.thread = new Thread(this::run, "engine");
    }

    public EngineRunner start() {
        thread.start();
        return this;
    }

    /** Thread-safe; called by gateway sessions. */
    public void submit(String member, String raw) {
        queue.add(new MatchingEngine.Inbound(member, raw, System.currentTimeMillis()));
    }

    private void run() {
        try {
            for (MatchingEngine.Inbound in = queue.take(); in != POISON; in = queue.take()) {
                handle(in);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(MatchingEngine.Inbound in) {
        audit.append(in.timestamp(), AuditLog.Direction.IN, in.member(), in.raw());
        List<MatchingEngine.Outbound> outs;
        try {
            outs = engine.process(in);
        } catch (RuntimeException e) {
            // NFR-2: a bug triggered by one message must not take down the exchange.
            System.err.println("ENGINE ERROR processing seq from " + in.member() + ": " + e);
            outs = List.of();
        }
        for (MatchingEngine.Outbound o : outs) {
            audit.append(in.timestamp(), AuditLog.Direction.OUT, o.member(), FixCodec.encode(o.message(), FixCodec.PIPE));
        }
        audit.flush();
        for (MatchingEngine.Outbound o : outs) {
            sink.send(o.member(), FixCodec.encode(o.message(), FixCodec.PIPE));
        }
    }

    /** Graceful shutdown: processes everything already queued, then stops. */
    @Override
    public void close() throws Exception {
        queue.add(POISON);
        thread.join(5000);
        audit.close();
    }
}
