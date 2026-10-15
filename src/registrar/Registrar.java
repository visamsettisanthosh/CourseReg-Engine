package registrar;

import adsa.IntervalTree;
import adsa.PrerequisiteDAG;
import db.DatabaseManager;
import exceptions.PrerequisiteNotMetException;
import exceptions.RegistrationException;
import exceptions.TimetableClashException;
import models.Course;
import models.Enrollment;
import models.Student;
import models.TimetableSlot;

import java.sql.SQLException;
import java.util.*;

/**
 * Layer 2 Registrar Module.
 * Demonstrates:
 * - OOP: Full Encapsulation (private fields, accessors, mutators).
 * - Constructor Overloading: Default and parameterized constructors.
 * - Method Overloading: Multi-signature enrollment and validation methods.
 * - ADSA Integration: Prerequisite DAG topological validation + Interval Tree clash detection.
 * - JDBC Persistence: Transactional registration and state updates.
 */
public class Registrar {
    private final DatabaseManager dbManager;
    private final PrerequisiteDAG prerequisiteDAG;
    private final IntervalTree intervalTree;
    private String currentSemester;

    // Default constructor
    public Registrar() {
        this(new DatabaseManager(), "Fall 2026");
    }

    // Overloaded Constructor 1: Custom DatabaseManager
    public Registrar(DatabaseManager dbManager) {
        this(dbManager, "Fall 2026");
    }

    // Overloaded Constructor 2: Custom DatabaseManager and Semester
    public Registrar(DatabaseManager dbManager, String currentSemester) {
        this.dbManager = dbManager;
        this.prerequisiteDAG = new PrerequisiteDAG();
        this.intervalTree = new IntervalTree();
        this.currentSemester = currentSemester;
        initializeGraphAndData();
    }

    /**
     * Loads the prerequisite graph into the DAG structure from the database.
     */
    public final void initializeGraphAndData() {
        try {
            List<Course> courses = dbManager.getAllCourses();
            for (Course c : courses) {
                prerequisiteDAG.registerCourse(c.getCourseId(), c.getCourseCode() + " - " + c.getCourseName());
            }

            Map<Integer, List<Integer>> prereqPairs = dbManager.getAllPrerequisitePairs();
            for (Map.Entry<Integer, List<Integer>> entry : prereqPairs.entrySet()) {
                int courseId = entry.getKey();
                for (int prereqId : entry.getValue()) {
                    prerequisiteDAG.addPrerequisiteEdge(courseId, prereqId);
                }
            }

            // Topologically verify DAG integrity on startup
            if (prerequisiteDAG.hasCycle()) {
                System.err.println("[CRITICAL] Prerequisite graph has cycles!");
            } else {
                List<Integer> topo = prerequisiteDAG.getTopologicalSort();
                System.out.println("[+] Prerequisite DAG initialized without cycles. Topological course count: " + topo.size());
            }
        } catch (SQLException e) {
            System.err.println("[-] Error loading prerequisite DAG from database: " + e.getMessage());
        }
    }

    /**
     * Comprehensive result record encapsulating the 5-step registration check.
     */
    public static class ValidationResult {
        private final boolean eligible;
        private final boolean prerequisitesMet;
        private final List<String> missingPrerequisites;
        private final boolean alreadyEnrolled;
        private final boolean hasTimetableClash;
        private final String clashDetails;
        private final boolean creditLimitExceeded;
        private final int currentCredits;
        private final int newTotalCredits;
        private final String failureReason;

        public ValidationResult(boolean eligible, boolean prerequisitesMet, List<String> missingPrerequisites,
                                boolean alreadyEnrolled, boolean hasTimetableClash, String clashDetails,
                                boolean creditLimitExceeded, int currentCredits, int newTotalCredits,
                                String failureReason) {
            this.eligible = eligible;
            this.prerequisitesMet = prerequisitesMet;
            this.missingPrerequisites = missingPrerequisites != null ? missingPrerequisites : Collections.emptyList();
            this.alreadyEnrolled = alreadyEnrolled;
            this.hasTimetableClash = hasTimetableClash;
            this.clashDetails = clashDetails;
            this.creditLimitExceeded = creditLimitExceeded;
            this.currentCredits = currentCredits;
            this.newTotalCredits = newTotalCredits;
            this.failureReason = failureReason;
        }

        public boolean isEligible() { return eligible; }
        public boolean isPrerequisitesMet() { return prerequisitesMet; }
        public List<String> getMissingPrerequisites() { return missingPrerequisites; }
        public boolean isAlreadyEnrolled() { return alreadyEnrolled; }
        public boolean hasTimetableClash() { return hasTimetableClash; }
        public String getClashDetails() { return clashDetails; }
        public boolean isCreditLimitExceeded() { return creditLimitExceeded; }
        public int getCurrentCredits() { return currentCredits; }
        public int getNewTotalCredits() { return newTotalCredits; }
        public String getFailureReason() { return failureReason; }
    }

    public static class EnrollmentOutcome {
        private final boolean success;
        private final String courseTitle;
        private final String prereqStatus;
        private final String timetableStatus;
        private final String statusLabel;
        private final String failureReason;
        private final Enrollment enrollment;

        public EnrollmentOutcome(boolean success, String courseTitle, String prereqStatus, String timetableStatus, String statusLabel, String failureReason, Enrollment enrollment) {
            this.success = success;
            this.courseTitle = courseTitle;
            this.prereqStatus = prereqStatus;
            this.timetableStatus = timetableStatus;
            this.statusLabel = statusLabel;
            this.failureReason = failureReason;
            this.enrollment = enrollment;
        }

        public boolean isSuccess() { return success; }
        public String getCourseTitle() { return courseTitle; }
        public String getPrereqStatus() { return prereqStatus; }
        public String getTimetableStatus() { return timetableStatus; }
        public String getStatusLabel() { return statusLabel; }
        public String getFailureReason() { return failureReason; }
        public Enrollment getEnrollment() { return enrollment; }
    }

    // ------------------------------------------------------------------------
    // METHOD OVERLOADING: VALIDATE ELIGIBILITY (STRICT 5-STEP PIPELINE)
    // ------------------------------------------------------------------------

    public ValidationResult validateEligibility(int studentId, int courseId) throws SQLException {
        Student student = dbManager.getStudentById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("Student not found with ID: " + studentId);
        }

        Course course = dbManager.getCourseById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("Course not found with ID: " + courseId);
        }

        return validateEligibility(student, course);
    }

    public ValidationResult validateEligibility(Student student, Course course) throws SQLException {
        int studentId = student.getStudentId();
        int courseId = course.getCourseId();

        // Populate completed and active enrollments
        List<Enrollment> enrollments = dbManager.getEnrollmentsForStudent(studentId);
        Set<Integer> completed = new HashSet<>();
        boolean alreadyEnrolled = false;

        for (Enrollment e : enrollments) {
            if ("COMPLETED".equalsIgnoreCase(e.getStatus())) {
                completed.add(e.getCourseId());
            }
            if ("ENROLLED".equalsIgnoreCase(e.getStatus()) && e.getCourseId() == courseId) {
                alreadyEnrolled = true;
            }
        }

        // STEP 1: Prerequisite DAG Validation
        List<Integer> missingIds = prerequisiteDAG.findMissingPrerequisites(courseId, completed);
        boolean prereqsMet = missingIds.isEmpty();
        List<String> missingNames = new ArrayList<>();
        for (int pId : missingIds) {
            missingNames.add(prerequisiteDAG.getCourseName(pId));
        }

        if (!prereqsMet) {
            String reason = "Missing prerequisite: " + String.join(", ", missingNames);
            return new ValidationResult(false, false, missingNames, alreadyEnrolled, false, null, false, 0, 0, reason);
        }

        // STEP 2: Duplicate Enrollment Check
        if (alreadyEnrolled) {
            String reason = "Already actively enrolled in " + course.getCourseCode() + " for this semester.";
            return new ValidationResult(false, true, Collections.emptyList(), true, false, null, false, 0, 0, reason);
        }

        // STEP 3: Timetable Clash Validation via ADSA Interval Tree
        List<TimetableSlot> activeSlots = dbManager.getActiveSlotsForStudent(studentId);
        intervalTree.populate(activeSlots);

        List<TimetableSlot> candidateSlots = dbManager.getSlotsForCourse(courseId);
        boolean hasClash = false;
        String clashDetails = null;

        for (TimetableSlot cand : candidateSlots) {
            IntervalTree.ClashDetail detail = intervalTree.inspectClash(cand);
            if (detail.isClashDetected()) {
                hasClash = true;
                clashDetails = detail.getFormattedExplanation();
                break;
            }
        }

        if (hasClash) {
            String reason = "Timetable clash detected:\n" + clashDetails;
            return new ValidationResult(false, true, Collections.emptyList(), false, true, clashDetails, false, 0, 0, reason);
        }

        // STEP 4: Semester Academic Credit Limit Check (Max 18 Credits)
        int currentCredits = dbManager.getTotalCreditsForStudent(studentId, this.currentSemester);
        int newTotal = currentCredits + course.getCredits();
        if (newTotal > 18) {
            String reason = String.format("Credit limit exceeded: Registration would result in %d credits (Maximum allowed: 18 credits per semester).", newTotal);
            return new ValidationResult(false, true, Collections.emptyList(), false, false, null, true, currentCredits, newTotal, reason);
        }

        // STEP 5: Eligible to Enroll
        return new ValidationResult(true, true, Collections.emptyList(), false, false, null, false, currentCredits, newTotal, null);
    }

    // ------------------------------------------------------------------------
    // METHOD OVERLOADING: ENROLL STUDENT
    // ------------------------------------------------------------------------

    public Enrollment enrollStudent(int studentId, int courseId) throws RegistrationException, SQLException {
        return enrollStudent(studentId, courseId, this.currentSemester);
    }

    public Enrollment enrollStudent(int studentId, int courseId, String semester) throws RegistrationException, SQLException {
        Student student = dbManager.getStudentById(studentId);
        if (student == null) {
            throw new RegistrationException("Enrollment rejected: Student ID " + studentId + " does not exist.");
        }

        Course course = dbManager.getCourseById(courseId);
        if (course == null) {
            throw new RegistrationException("Enrollment rejected: Course ID " + courseId + " does not exist.");
        }

        // Execute strict 5-step registration validation sequence
        ValidationResult check = validateEligibility(student, course);

        if (!check.isEligible()) {
            throw new RegistrationException("❌ Registration Blocked\n\nReason:\n" + check.getFailureReason());
        }

        // Commit enrollment to database
        int enrollmentId = dbManager.insertEnrollment(studentId, courseId, semester);
        System.out.printf("[+] Successfully enrolled student %s (ID %d) in %s (%s)%n",
                student.getName(), studentId, course.getCourseCode(), semester);

        return new Enrollment(enrollmentId, studentId, student.getName(), courseId, course.getCourseCode(), course.getCourseName(), semester, "ENROLLED", null, null);
    }

    /**
     * Displays advisor alerts for at-risk students (Layer 3 integration).
     */
    public void printAdvisorRiskAlerts() {
        try {
            List<Student> atRisk = dbManager.getAtRiskStudents();
            System.out.println("\n" + "=".repeat(75));
            System.out.println("ACADEMIC ADVISOR ALERT DASHBOARD: AT-RISK STUDENTS");
            System.out.println("=".repeat(75));
            System.out.printf("%-4s %-12s %-18s %-6s %-9s %-10s %-10s%n",
                    "ID", "Roll No", "Student Name", "GPA", "Attend%", "Risk Flag", "Risk Score");
            System.out.println("-".repeat(75));

            for (Student s : atRisk) {
                String flag = s.getRiskFlag();
                String urgency = "HIGH".equals(flag) ? "[URGENT INTERVENTION]" : "[MONITOR]";
                System.out.printf("%-4d %-12s %-18s %-6.2f %-9.1f %-10s %-6.3f %s%n",
                        s.getStudentId(), s.getRollNumber(), s.getName(), s.getGpa(),
                        s.getAttendancePct(), s.getRiskFlag(), s.getRiskScore(), urgency);
            }
            System.out.println("=".repeat(75) + "\n");
        } catch (SQLException e) {
            System.err.println("[-] Error fetching at-risk students: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // REGISTRAR / ADMIN MODULE FEATURES
    // ------------------------------------------------------------------------

    public synchronized int addCourse(String courseCode, String courseName, int credits, String department) throws SQLException {
        if (courseCode == null || courseCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Course code cannot be empty.");
        }
        if (courseName == null || courseName.trim().isEmpty()) {
            throw new IllegalArgumentException("Course name cannot be empty.");
        }
        if (credits <= 0 || credits > 6) {
            throw new IllegalArgumentException("Course credits must be between 1 and 6.");
        }

        int newId = dbManager.addCourse(courseCode, courseName, credits, department);
        prerequisiteDAG.registerCourse(newId, courseCode.trim().toUpperCase() + " - " + courseName.trim());
        return newId;
    }

    public synchronized boolean editCourse(int courseId, String courseCode, String courseName, int credits, String department) throws SQLException {
        if (courseCode == null || courseCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Course code cannot be empty.");
        }
        if (credits <= 0 || credits > 6) {
            throw new IllegalArgumentException("Course credits must be between 1 and 6.");
        }

        boolean updated = dbManager.editCourse(courseId, courseCode, courseName, credits, department);
        if (updated) {
            initializeGraphAndData(); // Refresh DAG course names
        }
        return updated;
    }

    public synchronized void addPrerequisite(int courseId, int prerequisiteId) throws SQLException {
        if (courseId == prerequisiteId) {
            throw new IllegalArgumentException("A course cannot be a prerequisite of itself.");
        }
        Course target = dbManager.getCourseById(courseId);
        Course prereq = dbManager.getCourseById(prerequisiteId);
        if (target == null || prereq == null) {
            throw new IllegalArgumentException("Specified course or prerequisite course does not exist.");
        }

        // Test cycle enforcement in the DAG before committing to database
        try {
            prerequisiteDAG.addPrerequisiteEdge(courseId, prerequisiteId);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("❌ Invalid prerequisite graph: Cycle detected. Adding dependency (" + prereq.getCourseCode() + " -> " + target.getCourseCode() + ") would create a closed dependency loop.");
        }

        // Commit to database
        dbManager.addPrerequisite(courseId, prerequisiteId);
    }

    public synchronized int addTimetableSlot(int courseId, String dayOfWeek, String startTime, String endTime, String roomNumber, int facultyId) throws SQLException {
        int startMin = TimetableSlot.parseTimeToMinutes(startTime);
        int endMin = TimetableSlot.parseTimeToMinutes(endTime);
        if (startMin >= endMin) {
            throw new IllegalArgumentException("Start time must be strictly before end time.");
        }
        return dbManager.addTimetableSlot(courseId, dayOfWeek, startTime, endTime, roomNumber, facultyId);
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public PrerequisiteDAG getPrerequisiteDAG() {
        return prerequisiteDAG;
    }

    public String getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(String currentSemester) {
        this.currentSemester = currentSemester;
    }
}
