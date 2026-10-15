package exceptions;

import java.util.List;

/**
 * Thrown when a student attempts to enroll in a course without completing all required prerequisites.
 */
public class PrerequisiteNotMetException extends RegistrationException {
    private final int studentId;
    private final int courseId;
    private final List<Integer> missingPrerequisiteIds;
    private final List<String> missingPrerequisiteNames;

    public PrerequisiteNotMetException(int studentId, int courseId, List<Integer> missingPrerequisiteIds, List<String> missingPrerequisiteNames) {
        super(String.format("Prerequisite Violation: Student ID %d cannot enroll in Course ID %d. Missing prerequisites: %s",
                studentId, courseId, missingPrerequisiteNames != null ? missingPrerequisiteNames : missingPrerequisiteIds));
        this.studentId = studentId;
        this.courseId = courseId;
        this.missingPrerequisiteIds = missingPrerequisiteIds;
        this.missingPrerequisiteNames = missingPrerequisiteNames;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getCourseId() {
        return courseId;
    }

    public List<Integer> getMissingPrerequisiteIds() {
        return missingPrerequisiteIds;
    }

    public List<String> getMissingPrerequisiteNames() {
        return missingPrerequisiteNames;
    }
}
