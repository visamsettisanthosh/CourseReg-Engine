package security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise In-Memory OTP Manager for Student Password Recovery.
 * Generates secure time-limited 6-digit OTPs, stores them hashed (never plain text),
 * tracks request cooldowns, expiration (5 mins), and maximum incorrect attempt limits.
 */
public class OtpManager {

    // Configurable security constants
    public static final long OTP_VALIDITY_MS = 5 * 60 * 1000L; // 5 minutes
    public static final long RESEND_COOLDOWN_MS = 45 * 1000L;   // 45 seconds cooldown
    public static final int MAX_FAILED_ATTEMPTS = 5;            // Max 5 attempts
    public static final int MAX_REQUESTS_PER_WINDOW = 4;        // Max 4 OTP requests in 10 minutes
    public static final long REQUEST_WINDOW_MS = 10 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static class OtpRecord {
        private final String rollNumber;
        private final String hashedOtp;
        private final String salt;
        private final String targetChannel; // "EMAIL" or "MOBILE"
        private final String maskedDestination;
        private final long createdAt;
        private final long expiresAt;
        private int failedAttempts;
        private boolean verified;

        public OtpRecord(String rollNumber, String hashedOtp, String salt, String targetChannel, String maskedDestination, long expiresAt) {
            this.rollNumber = rollNumber;
            this.hashedOtp = hashedOtp;
            this.salt = salt;
            this.targetChannel = targetChannel;
            this.maskedDestination = maskedDestination;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = expiresAt;
            this.failedAttempts = 0;
            this.verified = false;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public boolean isMaxAttemptsExceeded() {
            return failedAttempts >= MAX_FAILED_ATTEMPTS;
        }

        public String getRollNumber() { return rollNumber; }
        public String getTargetChannel() { return targetChannel; }
        public String getMaskedDestination() { return maskedDestination; }
        public long getCreatedAt() { return createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public int getFailedAttempts() { return failedAttempts; }
        public boolean isVerified() { return verified; }
    }

    public static class RequestRateTracker {
        private long lastRequestTime = 0;
        private int requestCount = 0;
        private long windowStartTime = 0;
    }

    // Active OTPs by uppercase Roll Number
    private static final Map<String, OtpRecord> activeOtps = new ConcurrentHashMap<>();
    // Rate limiter by uppercase Roll Number
    private static final Map<String, RequestRateTracker> rateTrackers = new ConcurrentHashMap<>();

    /**
     * Checks if a new OTP request is allowed under rate limits and cooldowns.
     * @return null if allowed, or error message if blocked.
     */
    public static synchronized String checkRequestRateLimit(String rollNumber) {
        String key = rollNumber.trim().toUpperCase();
        long now = System.currentTimeMillis();
        RequestRateTracker tracker = rateTrackers.computeIfAbsent(key, k -> new RequestRateTracker());

        // Check 45-second resend cooldown
        if (now - tracker.lastRequestTime < RESEND_COOLDOWN_MS) {
            long remainingSec = (RESEND_COOLDOWN_MS - (now - tracker.lastRequestTime)) / 1000L + 1;
            return "Please wait " + remainingSec + " seconds before requesting a new OTP.";
        }

        // Check sliding window request count limit
        if (now - tracker.windowStartTime > REQUEST_WINDOW_MS) {
            tracker.windowStartTime = now;
            tracker.requestCount = 0;
        }
        if (tracker.requestCount >= MAX_REQUESTS_PER_WINDOW) {
            return "Too many OTP requests. Please wait a few minutes before trying again.";
        }

        return null;
    }

    /**
     * Generates a 6-digit OTP, securely hashes it, and stores the OtpRecord.
     * @return the plain 6-digit OTP code to simulate dispatch via SMS or Email service.
     */
    public static synchronized String generateAndStoreOtp(String rollNumber, String targetChannel, String maskedDestination) {
        String key = rollNumber.trim().toUpperCase();
        long now = System.currentTimeMillis();

        // Update rate tracker
        RequestRateTracker tracker = rateTrackers.computeIfAbsent(key, k -> new RequestRateTracker());
        tracker.lastRequestTime = now;
        tracker.requestCount++;
        if (tracker.windowStartTime == 0) {
            tracker.windowStartTime = now;
        }

        // Generate 6-digit numeric OTP (100000 - 999999)
        int codeInt = 100000 + RANDOM.nextInt(900000);
        String plainOtp = String.valueOf(codeInt);

        // Securely hash OTP before storage - NEVER store plain text
        String salt = PasswordUtil.generateSalt();
        String hashedOtp = PasswordUtil.hashPassword(plainOtp, salt);

        OtpRecord record = new OtpRecord(key, hashedOtp, salt, targetChannel, maskedDestination, now + OTP_VALIDITY_MS);
        activeOtps.put(key, record);

        return plainOtp;
    }

    /**
     * Verifies the submitted OTP against the stored hash.
     * @return null on success, or an error message on failure.
     */
    public static synchronized String verifyOtp(String rollNumber, String candidateOtp) {
        String key = rollNumber.trim().toUpperCase();
        OtpRecord record = activeOtps.get(key);

        if (record == null) {
            return "No active OTP request found. Please request a new OTP.";
        }

        if (record.isExpired()) {
            activeOtps.remove(key);
            return "OTP expired. Please request a new OTP.";
        }

        if (record.isMaxAttemptsExceeded()) {
            activeOtps.remove(key);
            return "Too many incorrect OTP attempts. For security, please request a new OTP.";
        }

        if (candidateOtp == null || candidateOtp.trim().isEmpty()) {
            record.failedAttempts++;
            return "Please enter the 6-digit OTP.";
        }

        String computedHash = PasswordUtil.hashPassword(candidateOtp.trim(), record.salt);
        boolean matches = MessageDigest.isEqual(record.hashedOtp.getBytes(), computedHash.getBytes());

        if (!matches) {
            record.failedAttempts++;
            int remaining = MAX_FAILED_ATTEMPTS - record.failedAttempts;
            if (remaining <= 0) {
                activeOtps.remove(key);
                return "Too many incorrect OTP attempts. Please request a new OTP.";
            }
            return "Invalid OTP. Please try again. (" + remaining + " attempts remaining)";
        }

        // OTP verified successfully
        record.verified = true;
        return null;
    }

    /**
     * Checks if the student has successfully verified their OTP and is authorized to reset password.
     */
    public static synchronized boolean isVerifiedForReset(String rollNumber) {
        String key = rollNumber.trim().toUpperCase();
        OtpRecord record = activeOtps.get(key);
        return record != null && record.isVerified() && !record.isExpired();
    }

    /**
     * Invalidates and destroys the OTP record after successful password change or logout.
     */
    public static synchronized void invalidateOtp(String rollNumber) {
        if (rollNumber != null) {
            activeOtps.remove(rollNumber.trim().toUpperCase());
        }
    }

    /**
     * Helper to mask an email address (e.g., "sa****@gmail.com").
     */
    public static String maskEmail(String email) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            return "******@univ.edu";
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        String user = trimmed.substring(0, atIndex);
        String domain = trimmed.substring(atIndex);

        if (user.length() <= 2) {
            return user.charAt(0) + "****" + domain;
        } else {
            return user.substring(0, 2) + "****" + domain;
        }
    }

    /**
     * Helper to mask a mobile number (e.g., "******1234").
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            return "******1234";
        }
        String clean = mobile.replaceAll("[^0-9]", "");
        if (clean.length() <= 4) {
            return "******" + clean;
        }
        String lastFour = clean.substring(clean.length() - 4);
        return "******" + lastFour;
    }
}
