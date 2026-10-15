package exceptions;

import models.TimetableSlot;

/**
 * Thrown when a course timetable slot overlaps with another course already on the student's schedule.
 */
public class TimetableClashException extends RegistrationException {
    private final int studentId;
    private final int candidateCourseId;
    private final TimetableSlot candidateSlot;
    private final TimetableSlot conflictingSlot;

    public TimetableClashException(int studentId, int candidateCourseId, TimetableSlot candidateSlot, TimetableSlot conflictingSlot) {
        super(String.format("Timetable Clash: Student %d has a schedule overlap on %s. Candidate slot: %s (%s-%s) clashes with enrolled slot: %s (%s-%s) in Room %s.",
                studentId, candidateSlot.getDayOfWeek(),
                candidateSlot.getCourseCode() != null ? candidateSlot.getCourseCode() : "Course " + candidateCourseId,
                candidateSlot.getStartTime(), candidateSlot.getEndTime(),
                conflictingSlot.getCourseCode() != null ? conflictingSlot.getCourseCode() : "Course " + conflictingSlot.getCourseId(),
                conflictingSlot.getStartTime(), conflictingSlot.getEndTime(),
                conflictingSlot.getRoomNumber()));
        this.studentId = studentId;
        this.candidateCourseId = candidateCourseId;
        this.candidateSlot = candidateSlot;
        this.conflictingSlot = conflictingSlot;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getCandidateCourseId() {
        return candidateCourseId;
    }

    public TimetableSlot getCandidateSlot() {
        return candidateSlot;
    }

    public TimetableSlot getConflictingSlot() {
        return conflictingSlot;
    }
}
