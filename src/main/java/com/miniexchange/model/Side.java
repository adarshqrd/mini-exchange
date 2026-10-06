package com.miniexchange.model;

public enum Side {
    BUY("1"), SELL("2");

    private final String fixCode;

    Side(String fixCode) {
        this.fixCode = fixCode;
    }

    public String fixCode() {
        return fixCode;
    }

    /** @return the side, or null if the code is not a supported side. */
    public static Side fromFix(String code) {
        for (Side s : values()) {
            if (s.fixCode.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
