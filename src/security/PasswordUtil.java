package security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Enterprise Password Hashing and Verification Utility.
 * Uses SHA-256 with cryptographically secure random per-user salt.
 * Performs constant-time comparison to prevent timing side-channel attacks.
 * Pure standard Java SE - zero external third-party dependencies required.
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a 16-byte cryptographically secure random salt in hex string format.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Hashes password with salt using SHA-256.
     */
    public static String hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt must not be null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] hashed = md.digest(password.getBytes());
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in current JRE", e);
        }
    }

    /**
     * Creates a composite stored hash string formatted as: "salt:hash".
     */
    public static String createSecureHash(String password) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        return salt + ":" + hash;
    }

    /**
     * Verifies raw password against the stored composite hash ("salt:hash").
     */
    public static boolean verifyPassword(String rawPassword, String storedCompositeHash) {
        if (rawPassword == null || storedCompositeHash == null || !storedCompositeHash.contains(":")) {
            return false;
        }
        String[] parts = storedCompositeHash.split(":", 2);
        String salt = parts[0];
        String expectedHash = parts[1];
        String computedHash = hashPassword(rawPassword, salt);
        return MessageDigest.isEqual(expectedHash.getBytes(), computedHash.getBytes());
    }
}
