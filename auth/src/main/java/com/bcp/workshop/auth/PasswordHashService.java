package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class PasswordHashService {
    private static final String HASH_PREFIX = "$pbkdf2-sha256$";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 210_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;
    private static final int HASH_LENGTH_BITS = HASH_LENGTH_BYTES * Byte.SIZE;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(password, salt);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return HASH_PREFIX + ITERATION_COUNT + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    public boolean matches(String password, String encodedHash) {
        if (password == null) {
            return false;
        }

        EncodedPasswordHash parsedHash = parse(encodedHash);
        if (parsedHash == null) {
            return false;
        }

        byte[] candidateHash = derive(password, parsedHash.salt());
        return MessageDigest.isEqual(parsedHash.hash(), candidateHash);
    }

    public boolean isEncodedHash(String value) {
        return parse(value) != null;
    }

    private EncodedPasswordHash parse(String encodedHash) {
        if (encodedHash == null) {
            return null;
        }

        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 5 || !parts[1].equals("pbkdf2-sha256")) {
            return null;
        }

        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[3]);
            byte[] hash = Base64.getUrlDecoder().decode(parts[4]);
            if (iterations != ITERATION_COUNT || salt.length != SALT_LENGTH_BYTES || hash.length != HASH_LENGTH_BYTES) {
                return null;
            }
            return new EncodedPasswordHash(salt, hash);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private byte[] derive(String password, byte[] salt) {
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec keySpec = new PBEKeySpec(passwordCharacters, salt, ITERATION_COUNT, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            keySpec.clearPassword();
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    private record EncodedPasswordHash(byte[] salt, byte[] hash) {}
}
