package models;

/**
 * User Entity Model for Authentication and Role-Based Access Control (RBAC).
 */
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String role; // "STUDENT", "REGISTRAR", "ADVISOR", "ADMIN"
    private Integer studentId;
    private String studentName;
    private String rollNumber;
    private boolean isActive;
    private String createdAt;

    public User(int id, String username, String passwordHash, String role, Integer studentId, String studentName, String rollNumber, boolean isActive, String createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role != null ? role.toUpperCase() : "STUDENT";
        this.studentId = studentId;
        this.studentName = studentName;
        this.rollNumber = rollNumber;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public User(int id, String username, String passwordHash, String role, Integer studentId, String studentName, boolean isActive, String createdAt) {
        this(id, username, passwordHash, role, studentId, studentName, null, isActive, createdAt);
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Integer getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getRollNumber() { return rollNumber; }
    public boolean isActive() { return isActive; }
    public String getCreatedAt() { return createdAt; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setActive(boolean active) { isActive = active; }
    public void setRole(String role) { this.role = role != null ? role.toUpperCase() : this.role; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }
}
