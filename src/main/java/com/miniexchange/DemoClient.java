package com.miniexchange;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scripted interview demo. Connects three members to a running exchange and walks
 * through the acceptance criteria, printing each step and every report received.
 */
public final class DemoClient {

    private static final String RESET = "\u001B[0m", BOLD = "\u001B[1m", DIM = "\u001B[2m",
            GREEN = "\u001B[32m", RED = "\u001B[31m", YELLOW = "\u001B[33m", CYAN = "\u001B[36m";

    private final Map<String, PrintWriter> members = new LinkedHashMap<>();
    private final int port;

    private DemoClient(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        new DemoClient(args.length > 0 ? Integer.parseInt(args[0]) : 9878).run();
    }

    private void run() throws Exception {
        connect("ALPHA");
        connect("BRAVO");
        connect("CHARLIE");

        step("AC-20/21 Price-time priority. Three sellers rest on the book...");
        order("ALPHA", "A1", "SELL", 100, "35.10");
        order("BRAVO", "B1", "SELL", 100, "35.05");
        order("ALPHA", "A2", "SELL", 100, "35.05");
        step("CHARLIE buys 250 @ 35.10 → expect BRAVO@35.05, then ALPHA@35.05 (later), then ALPHA@35.10");
        order("CHARLIE", "C1", "BUY", 250, "35.10");

        step("AC-10 Max order qty: CHARLIE buys 1,000,000");
        order("CHARLIE", "C2", "BUY", 1_000_000, "35.00");

        step("AC-12 Fat finger: CHARLIE buys at 350.00 instead of 35.00");
        order("CHARLIE", "C3", "BUY", 100, "350.00");

        step("AC-04 Duplicate ClOrdID: CHARLIE resends C1");
        order("CHARLIE", "C1", "BUY", 100, "35.00");

        step("AC-03 Market order (unsupported)");
        send("CHARLIE", "8=FIX.4.4|35=D|49=CHARLIE|11=C4|55=D05|54=1|38=100|40=1");

        step("AC-32 BRAVO tries to cancel ALPHA's resting order A1");
        send("BRAVO", "8=FIX.4.4|35=F|49=BRAVO|11=B9|41=A1");

        step("AC-30 ALPHA cancels its own remaining 50 on A1");
        send("ALPHA", "8=FIX.4.4|35=F|49=ALPHA|11=A9|41=A1");

        step("NFR-2 BRAVO sends garbage — rejected, connection and exchange stay up");
        send("BRAVO", "hello exchange!!");
        order("BRAVO", "B2", "BUY", 10, "35.00");

        Thread.sleep(500);
        System.out.println("\n" + BOLD + "Demo complete." + RESET
                + " Stop the server (Ctrl+C) and replay the audit log to prove determinism (AC-41).");
        System.exit(0);
    }

    private void connect(String member) throws Exception {
        Socket socket = new Socket("localhost", port);
        members.put(member, new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("    " + describe(member, line));
                }
            } catch (Exception ignored) {
                // demo ends
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void order(String member, String clOrdId, String side, long qty, String price) throws Exception {
        send(member, "8=FIX.4.4|35=D|49=" + member + "|11=" + clOrdId + "|55=D05|54=" + (side.equals("BUY") ? "1" : "2")
                + "|38=" + qty + "|40=2|44=" + price);
    }

    private void send(String member, String fix) throws Exception {
        System.out.println("  " + CYAN + member + " →" + RESET + " " + DIM + fix + RESET);
        members.get(member).println(fix);
        Thread.sleep(250);
    }

    private static void step(String text) throws InterruptedException {
        Thread.sleep(400);
        System.out.println("\n" + BOLD + YELLOW + "▶ " + text + RESET);
    }

    private static String describe(String member, String line) {
        Map<String, String> f = new LinkedHashMap<>();
        for (String kv : line.split("\\|")) {
            int eq = kv.indexOf('=');
            if (eq > 0) f.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        String who = CYAN + "← " + member + RESET + " ";
        if ("9".equals(f.get("35"))) {
            return who + RED + "CANCEL REJECTED " + RESET + f.get("41") + ": " + f.get("58");
        }
        return who + switch (f.getOrDefault("150", "?")) {
            case "0" -> GREEN + "NEW      " + RESET + f.get("11") + " " + side(f) + " " + f.get("38") + " @ " + f.get("44");
            case "F" -> GREEN + BOLD + "TRADE    " + RESET + f.get("11") + " " + side(f) + " " + f.get("32") + " @ " + f.get("31")
                    + DIM + "  (cum " + f.get("14") + ", leaves " + f.get("151") + ")" + RESET;
            case "4" -> YELLOW + "CANCELED " + RESET + f.get("41") + DIM + "  (filled " + f.get("14") + ")" + RESET;
            case "8" -> RED + "REJECTED " + RESET + f.get("11") + ": " + f.get("58");
            default -> line;
        };
    }

    private static String side(Map<String, String> f) {
        return "1".equals(f.get("54")) ? "BUY " : "SELL";
    }
}
