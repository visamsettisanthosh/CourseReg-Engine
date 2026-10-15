package models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Student entity model demonstrating OOP encapsulation and academic profile state.
 */
public class Student {
    private int studentId;
    private String rollNumber;
    private String name;
    private String email;
    private String mobile;
    private double gpa;
    private double attendancePct;
    private int failedCourses;
    private double riskScore;
    private String riskFlag; // "LOW", "MEDIUM", "HIGH"
    private String currentSemester;

    // Set of course IDs completed with passing grade
    private Set<Integer> completedCourseIds;
    // Set of course IDs currently enrolled
    private Set<Integer> currentlyEnrolledCourseIds;
    // Full list of active timetable slots currently booked by the student
    private List<TimetableSlot> activeSchedule;

    public Student() {
        this.completedCourseIds = new HashSet<>();
        this.currentlyEnrolledCourseIds = new HashSet<>();
        this.activeSchedule = new ArrayList<>();
        this.riskFlag = "LOW";
        this.currentSemester = "Spring 2026";
        this.failedCourses = 0;
    }

    public Student(int studentId, String rollNumber, String name, String email, double gpa, double attendancePct) {
        this();
        this.studentId = studentId;
        this.rollNumber = rollNumber;
        this.name = name;
        this.email = email;
        this.gpa = gpa;
        this.attendancePct = attendancePct;
    }

    // Overloaded constructor with risk flags
    public Student(int studentId, String rollNumber, String name, String email, double gpa, double attendancePct, double riskScore, String riskFlag) {
        this(studentId, rollNumber, name, email, gpa, attendancePct);
        this.riskScore = riskScore;
        this.riskFlag = riskFlag != null ? riskFlag : "LOW";
    }

    // Overloaded constructor with failed courses count
    public Student(int studentId, String rollNumber, String name, String email, double gpa, double attendancePct, int failedCourses, double riskScore, String riskFlag) {
        this(studentId, rollNumber, name, email, gpa, attendancePct, riskScore, riskFlag);
        this.failedCourses = failedCourses;
    }

    public boolean hasCompletedCourse(int courseId) {
        return completedCourseIds.contains(courseId);
    }

    public void addCompletedCourse(int courseId) {
        completedCourseIds.add(courseId);
    }

    public boolean isCurrentlyEnrolledIn(int courseId) {
        return currentlyEnrolledCourseIds.contains(courseId);
    }

    public void addEnrolledCourse(int courseId) {
        currentlyEnrolledCourseIds.add(courseId);
    }

    public void addSlotToSchedule(TimetableSlot slot) {
        activeSchedule.add(slot);
    }

    // Getters and Setters
    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public double getGpa() {
        return gpa;
    }

    public void setGpa(double gpa) {
        this.gpa = gpa;
    }

    public double getAttendancePct() {
        return attendancePct;
    }

    public void setAttendancePct(double attendancePct) {
        this.attendancePct = attendancePct;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskFlag() {
        return riskFlag;
    }

    public void setRiskFlag(String riskFlag) {
        this.riskFlag = riskFlag;
    }

    public Set<Integer> getCompletedCourseIds() {
        return completedCourseIds;
    }

    public void setCompletedCourseIds(Set<Integer> completedCourseIds) {
        this.completedCourseIds = completedCourseIds;
    }

    public Set<Integer> getCurrentlyEnrolledCourseIds() {
        return currentlyEnrolledCourseIds;
    }

    public void setCurrentlyEnrolledCourseIds(Set<Integer> currentlyEnrolledCourseIds) {
        this.currentlyEnrolledCourseIds = currentlyEnrolledCourseIds;
    }

    public List<TimetableSlot> getActiveSchedule() {
        return activeSchedule;
    }

    public void setActiveSchedule(List<TimetableSlot> activeSchedule) {
        this.activeSchedule = activeSchedule;
    }

    public int getFailedCourses() {
        return failedCourses;
    }

    public void setFailedCourses(int failedCourses) {
        this.failedCourses = failedCourses;
    }

    public String getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(String currentSemester) {
        this.currentSemester = currentSemester;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s) - GPA: %.2f, Att: %.1f%%, Failed: %d, Risk: %s (%.2f)",
                studentId, name, rollNumber, gpa, attendancePct, failedCourses, riskFlag, riskScore);
    }
}
