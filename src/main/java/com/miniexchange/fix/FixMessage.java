package com.miniexchange.fix;

import java.util.LinkedHashMap;
import java.util.Map;

/** A FIX message as an ordered tag → value map. */
public final class FixMessage {

    private final Map<Integer, String> fields = new LinkedHashMap<>();

    public FixMessage set(int tag, String value) {
        fields.put(tag, value);
        return this;
    }

    public FixMessage set(int tag, long value) {
        return set(tag, Long.toString(value));
    }

    public String get(int tag) {
        return fields.get(tag);
    }

    public boolean has(int tag) {
        return fields.containsKey(tag);
    }

    Map<Integer, String> fields() {
        return fields;
    }

    @Override
    public String toString() {
        return FixCodec.encode(this, FixCodec.PIPE);
    }
}
