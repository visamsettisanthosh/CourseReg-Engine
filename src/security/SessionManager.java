package security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory Session & Token Manager.
 * Tracks authenticated sessions, user roles, expiration timestamps,
 * and handles secure token destruction upon logout.
 */
public class SessionManager {

    public static class Session {
        private final String token;
        private final int userId;
        private final String username;
        private final String role; // "STUDENT", "REGISTRAR", "ADVISOR", "ADMIN"
        private final Integer studentId; // nullable (present if role == STUDENT)
        private final String displayName;
        private final String rollNumber;
        private final long createdAt;
        private long lastAccess;

        public Session(String token, int userId, String username, String role, Integer studentId, String displayName, String rollNumber) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.role = role.toUpperCase();
            this.studentId = studentId;
            this.displayName = displayName;
            this.rollNumber = rollNumber;
            this.createdAt = System.currentTimeMillis();
            this.lastAccess = this.createdAt;
        }

        public Session(String token, int userId, String username, String role, Integer studentId, String displayName) {
            this(token, userId, username, role, studentId, displayName, null);
        }

        public boolean isExpired(long maxAgeMillis) {
            return System.currentTimeMillis() - lastAccess > maxAgeMillis;
        }

        public void touch() {
            this.lastAccess = System.currentTimeMillis();
        }

        public String getToken() { return token; }
        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public Integer getStudentId() { return studentId; }
        public String getDisplayName() { return displayName; }
        public String getRollNumber() { return rollNumber; }
        public long getCreatedAt() { return createdAt; }
        public long getLastAccess() { return lastAccess; }

        public boolean hasRole(String... targetRoles) {
            for (String r : targetRoles) {
                if (this.role.equalsIgnoreCase(r)) return true;
            }
            return false;
        }
    }

    // 24 hour sliding session timeout
    private static final long SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L;
    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    public static Session createSession(int userId, String username, String role, Integer studentId, String displayName, String rollNumber) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, userId, username, role, studentId, displayName, rollNumber);
        activeSessions.put(token, session);
        return session;
    }

    public static Session createSession(int userId, String username, String role, Integer studentId, String displayName) {
        return createSession(userId, username, role, studentId, displayName, null);
    }

    public static Session getSession(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        Session session = activeSessions.get(token.trim());
        if (session != null) {
            if (session.isExpired(SESSION_TIMEOUT_MS)) {
                activeSessions.remove(token.trim());
                return null;
            }
            session.touch();
        }
        return session;
    }

    public static void destroySession(String token) {
        if (token != null) {
            activeSessions.remove(token.trim());
        }
    }

    public static int getActiveSessionCount() {
        return activeSessions.size();
    }
}
