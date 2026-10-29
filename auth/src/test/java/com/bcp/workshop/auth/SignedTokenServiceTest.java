package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedTokenServiceTest {
    private static final String TEST_SECRET = "test-only-token-signing-key-for-auth-workshop";

    @Test
    void rejectsSigningKeysShorterThanTheHmacMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new SignedTokenService("short"));
    }
}
