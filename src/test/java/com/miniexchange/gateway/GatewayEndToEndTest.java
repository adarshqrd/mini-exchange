package com.miniexchange.gateway;

import com.miniexchange.TestExchange;
import com.miniexchange.audit.AuditLog;
import com.miniexchange.audit.Replayer;
import com.miniexchange.engine.EngineRunner;
import com.miniexchange.engine.MatchingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GatewayEndToEndTest {

    @TempDir
    Path dir;

    record Client(Socket socket, PrintWriter out, BufferedReader in) implements AutoCloseable {
        static Client connect(int port) throws Exception {
            Socket s = new Socket("localhost", port);
            s.setSoTimeout(3000);
            return new Client(s, new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8),
                    new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)));
        }

        String send(String line) throws Exception {
            out.println(line);
            return in.readLine();
        }

        public void close() throws Exception {
            socket.close();
        }
    }

    @Test
    @DisplayName("§4 AC-01 AC-23 AC-40 AC-41 two members trade over TCP; audit replays identically")
    void endToEnd() throws Exception {
        Path audit = dir.resolve("audit.log");
        try (TcpGateway gw = new TcpGateway(0)) {
            EngineRunner runner = new EngineRunner(new MatchingEngine(TestExchange.config()), AuditLog.open(audit), gw).start();
            gw.start(runner::submit);

            try (Client seller = Client.connect(gw.port()); Client buyer = Client.connect(gw.port())) {
                String ack = seller.send(TestExchange.newOrder("SELLER", "S1", "2", 100, "35.00"));
                assertTrue(ack.contains("150=0"), ack);

                String buyAck = buyer.send(TestExchange.newOrder("BUYER", "B1", "1", 100, "35.00"));
                assertTrue(buyAck.contains("150=0"), buyAck);
                String buyFill = buyer.in().readLine();
                assertTrue(buyFill.contains("150=F") && buyFill.contains("31=35.00"), buyFill);

                String sellFill = seller.in().readLine();   // the resting seller is notified too
                assertTrue(sellFill.contains("150=F") && sellFill.contains("56=SELLER"), sellFill);

                String junk = buyer.send("not fix at all");  // NFR-2: rejected, connection stays up
                assertTrue(junk.contains("150=8") && junk.contains("Malformed"), junk);
                assertTrue(buyer.send(TestExchange.newOrder("BUYER", "B2", "1", 10, "35.00")).contains("150=0"));
            }
            runner.close();
        }
        assertTrue(Replayer.replay(AuditLog.read(audit), TestExchange.config()).identical());
    }
}
