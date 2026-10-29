package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashServiceTest {
    private final PasswordHashService passwordHashService = new PasswordHashService();

    @Test
    void hashesWithUniqueSaltsAndMatchesOnlyTheOriginalPassword() {
        String firstHash = passwordHashService.hash("test-password");
        String secondHash = passwordHashService.hash("test-password");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHashService.isEncodedHash(firstHash));
        assertTrue(passwordHashService.matches("test-password", firstHash));
        assertFalse(passwordHashService.matches("wrong-password", firstHash));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(passwordHashService.isEncodedHash("plain-text"));
        assertFalse(passwordHashService.matches("test-password", "$pbkdf2-sha256$invalid"));
        assertFalse(passwordHashService.matches(null, passwordHashService.hash("test-password")));
        assertThrows(IllegalArgumentException.class, () -> passwordHashService.hash(null));
    }

    @Test
    void rejectsHashesWithUnexpectedAlgorithmCostOrLengths() {
        String salt = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

        assertFalse(passwordHashService.isEncodedHash("$bcrypt$210000$" + salt + "$" + hash));
        assertFalse(passwordHashService.isEncodedHash("$pbkdf2-sha256$1$" + salt + "$" + hash));
        assertFalse(passwordHashService.isEncodedHash("$pbkdf2-sha256$210000$c2hvcnQ$" + hash));
        assertFalse(passwordHashService.isEncodedHash("$pbkdf2-sha256$210000$" + salt + "$c2hvcnQ"));
    }
}
