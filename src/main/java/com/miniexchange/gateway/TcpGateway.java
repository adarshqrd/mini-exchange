package com.miniexchange.gateway;

import com.miniexchange.engine.EngineRunner;
import com.miniexchange.fix.FixCodec;
import com.miniexchange.fix.FixParseException;
import com.miniexchange.fix.Tag;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Accepts member TCP connections (one FIX message per line) and hands inbound
 * lines to the engine. Routes outbound reports back to the member's connection.
 * Concurrency lives here, at the edge; the engine itself is single-threaded.
 */
public final class TcpGateway implements EngineRunner.OutboundSink, AutoCloseable {

    private final ServerSocket server;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger connectionIds = new AtomicInteger();
    private volatile boolean running = true;

    public TcpGateway(int port) throws IOException {
        this.server = new ServerSocket(port);
    }

    public int port() {
        return server.getLocalPort();
    }

    /** @param onMessage receives (member, raw FIX line) for each inbound message. */
    public void start(BiConsumer<String, String> onMessage) {
        Thread acceptor = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = server.accept();
                    Session s = new Session(socket, connectionIds.incrementAndGet(), onMessage);
                    Thread t = new Thread(s::readLoop, "session-" + s.connectionId);
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("accept failed: " + e.getMessage());
                    }
                }
            }
        }, "acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @Override
    public void send(String member, String fixLine) {
        Session s = sessions.get(member);
        if (s != null) {
            s.write(fixLine);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        server.close();
        sessions.values().forEach(Session::close);
    }

    private final class Session {
        private final Socket socket;
        private final int connectionId;
        private final BiConsumer<String, String> onMessage;
        private final BufferedWriter out;
        private String member;   // bound from SenderCompID of the first message (spec C-2)

        Session(Socket socket, int connectionId, BiConsumer<String, String> onMessage) throws IOException {
            this.socket = socket;
            this.connectionId = connectionId;
            this.onMessage = onMessage;
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void readLoop() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    if (member == null) {
                        member = bindMember(line);
                    }
                    onMessage.accept(member, line);
                }
            } catch (IOException e) {
                // connection dropped; fall through to cleanup
            } finally {
                if (member != null) {
                    sessions.remove(member, this);
                }
                close();
            }
        }

        private String bindMember(String firstLine) {
            String id = null;
            try {
                id = FixCodec.parse(firstLine).get(Tag.SENDER_COMP_ID);
            } catch (FixParseException ignored) {
                // fall back to a connection id so the sender still receives its reject
            }
            String bound = (id == null || id.isBlank()) ? "conn-" + connectionId : id.replaceAll("\\s", "_");
            sessions.put(bound, this);
            return bound;
        }

        synchronized void write(String line) {
            try {
                out.write(line);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
