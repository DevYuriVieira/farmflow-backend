package com.farmflow.config;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class W3CTraceContextUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private W3CTraceContextUtils() {
    }

    public static String generateTraceParent(String correlationId) {
        byte[] traceIdBytes = new byte[16];
        byte[] spanIdBytes = new byte[8];
        RANDOM.nextBytes(traceIdBytes);
        RANDOM.nextBytes(spanIdBytes);

        String traceId = HexFormat.of().formatHex(traceIdBytes);
        String spanId = HexFormat.of().formatHex(spanIdBytes);

        return "00-" + traceId + "-" + spanId + "-01";
    }

    public static boolean isValidW3CTraceParent(String traceParent) {
        if (traceParent == null || traceParent.length() != 55) {
            return false;
        }
        String[] parts = traceParent.split("-");
        return parts.length == 4
                && parts[0].length() == 2
                && parts[1].length() == 32
                && parts[2].length() == 16
                && parts[3].length() == 2;
    }
}
