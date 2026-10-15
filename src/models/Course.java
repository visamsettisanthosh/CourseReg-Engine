package models;

import java.util.ArrayList;
import java.util.List;

/**
 * Course entity model demonstrating OOP encapsulation and association.
 */
public class Course {
    private int courseId;
    private String courseCode;
    private String courseName;
    private int credits;
    private String department;
    private List<Integer> prerequisiteIds;
    private List<TimetableSlot> timetableSlots;

    public Course() {
        this.prerequisiteIds = new ArrayList<>();
        this.timetableSlots = new ArrayList<>();
    }

    public Course(int courseId, String courseCode, String courseName, int credits, String department) {
        this.courseId = courseId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.department = department;
        this.prerequisiteIds = new ArrayList<>();
        this.timetableSlots = new ArrayList<>();
    }

    // Overloaded constructor with prerequisites
    public Course(int courseId, String courseCode, String courseName, int credits, String department, List<Integer> prerequisiteIds) {
        this(courseId, courseCode, courseName, credits, department);
        if (prerequisiteIds != null) {
            this.prerequisiteIds.addAll(prerequisiteIds);
        }
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

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public List<Integer> getPrerequisiteIds() {
        return prerequisiteIds;
    }

    public void setPrerequisiteIds(List<Integer> prerequisiteIds) {
        this.prerequisiteIds = prerequisiteIds;
    }

    public void addPrerequisiteId(int prereqId) {
        if (!this.prerequisiteIds.contains(prereqId)) {
            this.prerequisiteIds.add(prereqId);
        }
    }

    public List<TimetableSlot> getTimetableSlots() {
        return timetableSlots;
    }

    public void setTimetableSlots(List<TimetableSlot> timetableSlots) {
        this.timetableSlots = timetableSlots;
    }

    public void addTimetableSlot(TimetableSlot slot) {
        this.timetableSlots.add(slot);
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: %s (%d cr, %s)", courseId, courseCode, courseName, credits, department);
    }
}
