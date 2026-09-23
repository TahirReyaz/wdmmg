package com.tahir.finance.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 generator (RFC 9562): 48 bits of Unix milliseconds followed by random bits.
 * <p>
 * Time-ordered ids keep B-tree inserts at the right edge of the index instead of
 * scattering them, which matters once the expenses table is large.
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    public static UUID generate() {
        long millis = System.currentTimeMillis();
        byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);

        long msb = (millis << 16)
                | (0x7000L)                                  // version 7
                | ((bytes[0] & 0x0FL) << 8)
                | (bytes[1] & 0xFFL);

        long lsb = 0x8000000000000000L;                      // variant 10xx
        lsb |= ((long) (bytes[2] & 0x3F)) << 56;
        for (int i = 3; i < 10; i++) {
            lsb |= ((long) (bytes[i] & 0xFF)) << (8 * (9 - i));
        }

        return new UUID(msb, lsb);
    }

    /** Sentinel used by the rollup table where a real id would be NULL. */
    public static final UUID NIL = new UUID(0L, 0L);
}
