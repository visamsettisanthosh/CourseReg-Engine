package models;

import java.sql.Timestamp;

/**
 * Enrollment associative entity model connecting Student and Course.
 */
public class Enrollment {
    private int enrollmentId;
    private int studentId;
    private String studentName;
    private int courseId;
    private String courseCode;
    private String courseName;
    private String semester;
    private String status; // 'COMPLETED', 'ENROLLED', 'DROPPED', 'PENDING'
    private String grade;  // 'A', 'B', 'C', 'F', or null
    private Timestamp enrolledAt;

    public Enrollment() {}

    public Enrollment(int enrollmentId, int studentId, int courseId, String semester, String status, String grade) {
        this.enrollmentId = enrollmentId;
        this.studentId = studentId;
        this.courseId = courseId;
        this.semester = semester;
        this.status = status;
        this.grade = grade;
    }

    // Overloaded constructor with enriched metadata
    public Enrollment(int enrollmentId, int studentId, String studentName, int courseId, String courseCode, String courseName, String semester, String status, String grade, Timestamp enrolledAt) {
        this(enrollmentId, studentId, courseId, semester, status, grade);
        this.studentName = studentName;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.enrolledAt = enrolledAt;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Timestamp getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Timestamp enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    @Override
    public String toString() {
        return String.format("Enrollment #%d: Student %d -> Course %d [%s] Status: %s, Grade: %s",
                enrollmentId, studentId, courseId, semester, status, grade != null ? grade : "-");
    }
}
