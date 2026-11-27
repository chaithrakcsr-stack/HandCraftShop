package com.handcraftshop.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;

public final class PasswordUtil {
    private static final int ITERATIONS = 150_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordUtil() {}

    public static String hash(String password) {
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] derived = derive(password.toCharArray(), salt, ITERATIONS);
            return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(derived);
        } catch (Exception e) {
            throw new IllegalStateException("Could not secure the password", e);
        }
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        if (!stored.startsWith("pbkdf2$")) {
            // Compatibility for accounts created before password hashing was added.
            return MessageDigest.isEqual(password.getBytes(), stored.getBytes());
        }
        try {
            String[] parts = stored.split("\\$");
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
