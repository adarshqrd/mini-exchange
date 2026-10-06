package com.miniexchange.fix;

import java.util.Map;

/**
 * Parses and encodes FIX tag=value messages. Accepts SOH or '|' as the field
 * delimiter (spec §4). BodyLength(9)/CheckSum(10) are not computed: they belong
 * to the FIX session layer, which spec 001 scopes out.
 */
public final class FixCodec {

    public static final char SOH = '\u0001';
    public static final char PIPE = '|';
    private static final int MAX_LENGTH = 4096;

    private FixCodec() {}

    /** @throws FixParseException on any malformed input; never anything else (NFR-2). */
    public static FixMessage parse(String line) {
        if (line == null || line.isBlank()) {
            throw new FixParseException("Empty message");
        }
        if (line.length() > MAX_LENGTH) {
            throw new FixParseException("Message too long");
        }
        FixMessage msg = new FixMessage();
        for (String field : line.strip().split("[\u0001|]")) {
            if (field.isEmpty()) {
                continue;
            }
            int eq = field.indexOf('=');
            if (eq <= 0 || eq == field.length() - 1) {
                throw new FixParseException("Malformed field '" + field + "'");
            }
            int tag;
            try {
                tag = Integer.parseInt(field.substring(0, eq));
            } catch (NumberFormatException e) {
                throw new FixParseException("Non-numeric tag in '" + field + "'");
            }
            if (tag <= 0) {
                throw new FixParseException("Invalid tag " + tag);
            }
            msg.set(tag, field.substring(eq + 1));
        }
        if (!msg.has(Tag.MSG_TYPE)) {
            throw new FixParseException("Missing MsgType(35)");
        }
        return msg;
    }

    public static String encode(FixMessage msg, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : msg.fields().entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(delimiter);
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
