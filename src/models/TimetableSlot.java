package models;

/**
 * TimetableSlot represents a scheduled class period for a course.
 * Implements interval representation and overlap logic.
 */
public class TimetableSlot implements Comparable<TimetableSlot> {
    private int slotId;
    private int courseId;
    private String courseCode; // Enriched metadata
    private String dayOfWeek;  // MON, TUE, WED, THU, FRI
    private String startTime;  // "09:00"
    private String endTime;    // "10:30"
    private String roomNumber;
    private int facultyId;
    private String facultyName;

    public TimetableSlot() {}

    public TimetableSlot(int slotId, int courseId, String dayOfWeek, String startTime, String endTime, String roomNumber, int facultyId) {
        this.slotId = slotId;
        this.courseId = courseId;
        this.dayOfWeek = dayOfWeek.toUpperCase();
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomNumber = roomNumber;
        this.facultyId = facultyId;
    }

    // Overloaded constructor with enriched course/faculty labels
    public TimetableSlot(int slotId, int courseId, String courseCode, String dayOfWeek, String startTime, String endTime, String roomNumber, int facultyId, String facultyName) {
        this(slotId, courseId, dayOfWeek, startTime, endTime, roomNumber, facultyId);
        this.courseCode = courseCode;
        this.facultyName = facultyName;
    }

    /**
     * Converts "HH:MM" string to minutes from midnight for ADSA interval calculations.
     */
    public static int parseTimeToMinutes(String timeStr) {
        String[] parts = timeStr.trim().split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        return hours * 60 + minutes;
    }

    public int getStartMinutes() {
        return parseTimeToMinutes(startTime);
    }

    public int getEndMinutes() {
        return parseTimeToMinutes(endTime);
    }

    /**
     * Interval Overlap Theorem:
     * Two intervals [s1, e1) and [s2, e2) overlap iff:
     *   s1 < e2 AND s2 < e1
     */
    public boolean overlapsWith(TimetableSlot other) {
        if (!this.dayOfWeek.equalsIgnoreCase(other.dayOfWeek)) {
            return false;
        }
        int s1 = this.getStartMinutes();
        int e1 = this.getEndMinutes();
        int s2 = other.getStartMinutes();
        int e2 = other.getEndMinutes();

        return (s1 < e2) && (s2 < e1);
    }

    public int getSlotId() {
        return slotId;
    }

    public void setSlotId(int slotId) {
        this.slotId = slotId;
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

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public int getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(int facultyId) {
        this.facultyId = facultyId;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    @Override
    public int compareTo(TimetableSlot o) {
        int dayComp = this.dayOfWeek.compareTo(o.dayOfWeek);
        if (dayComp != 0) return dayComp;
        return Integer.compare(this.getStartMinutes(), o.getStartMinutes());
    }

    @Override
    public String toString() {
        return String.format("%s %s-%s (%s, Room %s)", dayOfWeek, startTime, endTime, courseCode != null ? courseCode : "Course " + courseId, roomNumber);
    }
}
