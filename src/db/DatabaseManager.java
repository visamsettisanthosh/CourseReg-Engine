package db;

import models.*;
import security.PasswordUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.*;
import java.util.*;

/**
 * Layer 1 Database Access Object & JDBC Manager.
 * Connects to SQLite / PostgreSQL / MySQL and handles all CRUD operations
 * with prepared statements, transactions, and resource safety.
 */
public class DatabaseManager {
    private final String dbUrl;
    private final String user;
    private final String password;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[!] Note: org.sqlite.JDBC not in classpath.");
        }
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}
    }

    public DatabaseManager() {
        // Defaults to SQLite database in current/parent workspace
        String dbPath = "registration.db";
        File f = new File(dbPath);
        this.dbUrl = "jdbc:sqlite:" + f.getAbsolutePath();
        this.user = null;
        this.password = null;
        initUsersTableAndDemoAccounts();
    }

    public DatabaseManager(String jdbcUrl, String user, String password) {
        this.dbUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        initUsersTableAndDemoAccounts();
    }

    public Connection getConnection() throws SQLException {
        if (user != null && password != null) {
            return DriverManager.getConnection(dbUrl, user, password);
        } else {
            return DriverManager.getConnection(dbUrl);
        }
    }

    /**
     * Initializes database schema and seed data from schema.sql if empty.
     */
    public void initializeSchemaIfEmpty(String schemaSqlPath) {
        try (Connection conn = getConnection()) {
            boolean hasTables = false;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM student")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    hasTables = true;
                }
            } catch (SQLException ignored) {
                // Table doesn't exist yet
            }

            if (!hasTables) {
                System.out.println("[*] Initializing database from schema file: " + schemaSqlPath);
                File schemaFile = new File(schemaSqlPath);
                if (!schemaFile.exists()) {
                    schemaFile = new File("schema.sql");
                }

                if (schemaFile.exists()) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new FileReader(schemaFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Strip single line comments
                            if (line.trim().startsWith("--")) continue;
                            sb.append(line).append("\n");
                        }
                    }

                    // Execute statements delimited by semicolon
                    String[] statements = sb.toString().split(";");
                    try (Statement stmt = conn.createStatement()) {
                        for (String sql : statements) {
                            String trimmed = sql.trim();
                            if (!trimmed.isEmpty()) {
                                try {
                                    stmt.execute(trimmed);
                                } catch (SQLException ex) {
                                    // Ignore drop table failures
                                    if (!trimmed.toUpperCase().startsWith("DROP")) {
                                        System.err.println("[!] Warning during schema init: " + ex.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    System.out.println("[+] Database successfully initialized with tables and sample data.");
                } else {
                    System.err.println("[-] Warning: schema.sql not found at " + schemaSqlPath);
                }
            }
        } catch (Exception e) {
            System.err.println("[-] Error initializing database: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // READ METHODS
    // ------------------------------------------------------------------------

    public List<Student> getAllStudents() throws SQLException {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT student_id, roll_number, name, email, COALESCE(mobile, '') AS mobile, gpa, attendance_pct, COALESCE(failed_courses, 0) AS failed_courses, risk_score, risk_flag FROM student ORDER BY student_id ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Student s = new Student(
                        rs.getInt("student_id"),
                        rs.getString("roll_number"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getDouble("gpa"),
                        rs.getDouble("attendance_pct"),
                        rs.getInt("failed_courses"),
                        rs.getDouble("risk_score"),
                        rs.getString("risk_flag")
                );
                s.setMobile(rs.getString("mobile"));
                students.add(s);
            }
        }
        return students;
    }

    public Student getStudentById(int studentId) throws SQLException {
        String sql = "SELECT student_id, roll_number, name, email, COALESCE(mobile, '') AS mobile, gpa, attendance_pct, COALESCE(failed_courses, 0) AS failed_courses, risk_score, risk_flag FROM student WHERE student_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student s = new Student(
                            rs.getInt("student_id"),
                            rs.getString("roll_number"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getDouble("gpa"),
                            rs.getDouble("attendance_pct"),
                            rs.getInt("failed_courses"),
                            rs.getDouble("risk_score"),
                            rs.getString("risk_flag")
                    );
                    s.setMobile(rs.getString("mobile"));
                    return s;
                }
            }
        }
        return null;
    }

    public List<Course> getAllCourses() throws SQLException {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT course_id, course_code, course_name, credits, department FROM course ORDER BY course_id ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                courses.add(new Course(
                        rs.getInt("course_id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getInt("credits"),
                        rs.getString("department")
                ));
            }
        }

        // Attach prerequisites
        Map<Integer, List<Integer>> prereqMap = getAllPrerequisitePairs();
        // Attach timetable slots
        Map<Integer, List<TimetableSlot>> slotMap = getAllTimetableSlotsByCourse();

        for (Course c : courses) {
            if (prereqMap.containsKey(c.getCourseId())) {
                c.setPrerequisiteIds(prereqMap.get(c.getCourseId()));
            }
            if (slotMap.containsKey(c.getCourseId())) {
                c.setTimetableSlots(slotMap.get(c.getCourseId()));
            }
        }

        return courses;
    }

    public Course getCourseById(int courseId) throws SQLException {
        String sql = "SELECT course_id, course_code, course_name, credits, department FROM course WHERE course_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Course c = new Course(
                            rs.getInt("course_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("credits"),
                            rs.getString("department")
                    );
                    c.setPrerequisiteIds(getPrerequisitesForCourse(courseId));
                    c.setTimetableSlots(getSlotsForCourse(courseId));
                    return c;
                }
            }
        }
        return null;
    }

    public Map<Integer, List<Integer>> getAllPrerequisitePairs() throws SQLException {
        Map<Integer, List<Integer>> map = new HashMap<>();
        String sql = "SELECT course_id, prerequisite_id FROM prerequisite";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int cId = rs.getInt("course_id");
                int pId = rs.getInt("prerequisite_id");
                map.computeIfAbsent(cId, k -> new ArrayList<>()).add(pId);
            }
        }
        return map;
    }

    public List<Integer> getPrerequisitesForCourse(int courseId) throws SQLException {
        List<Integer> prereqs = new ArrayList<>();
        String sql = "SELECT prerequisite_id FROM prerequisite WHERE course_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    prereqs.add(rs.getInt("prerequisite_id"));
                }
            }
        }
        return prereqs;
    }

    public Map<Integer, List<TimetableSlot>> getAllTimetableSlotsByCourse() throws SQLException {
        Map<Integer, List<TimetableSlot>> map = new HashMap<>();
        String sql = "SELECT t.slot_id, t.course_id, c.course_code, t.day_of_week, t.start_time, t.end_time, t.room_number, t.faculty_id, f.name AS faculty_name " +
                     "FROM timetable t " +
                     "JOIN course c ON c.course_id = t.course_id " +
                     "JOIN faculty f ON f.faculty_id = t.faculty_id " +
                     "ORDER BY t.day_of_week, t.start_time";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TimetableSlot slot = new TimetableSlot(
                        rs.getInt("slot_id"),
                        rs.getInt("course_id"),
                        rs.getString("course_code"),
                        rs.getString("day_of_week"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("room_number"),
                        rs.getInt("faculty_id"),
                        rs.getString("faculty_name")
                );
                map.computeIfAbsent(slot.getCourseId(), k -> new ArrayList<>()).add(slot);
            }
        }
        return map;
    }

    public List<TimetableSlot> getSlotsForCourse(int courseId) throws SQLException {
        List<TimetableSlot> slots = new ArrayList<>();
        String sql = "SELECT t.slot_id, t.course_id, c.course_code, t.day_of_week, t.start_time, t.end_time, t.room_number, t.faculty_id, f.name AS faculty_name " +
                     "FROM timetable t " +
                     "JOIN course c ON c.course_id = t.course_id " +
                     "JOIN faculty f ON f.faculty_id = t.faculty_id " +
                     "WHERE t.course_id = ? " +
                     "ORDER BY t.day_of_week, t.start_time";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slots.add(new TimetableSlot(
                            rs.getInt("slot_id"),
                            rs.getInt("course_id"),
                            rs.getString("course_code"),
                            rs.getString("day_of_week"),
                            rs.getString("start_time"),
                            rs.getString("end_time"),
                            rs.getString("room_number"),
                            rs.getInt("faculty_id"),
                            rs.getString("faculty_name")
                    ));
                }
            }
        }
        return slots;
    }

    public List<Enrollment> getEnrollmentsForStudent(int studentId) throws SQLException {
        List<Enrollment> list = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, e.student_id, s.name AS student_name, e.course_id, c.course_code, c.course_name, " +
                     "e.semester, e.status, e.grade, e.enrolled_at " +
                     "FROM enrollment e " +
                     "JOIN student s ON s.student_id = e.student_id " +
                     "JOIN course c ON c.course_id = e.course_id " +
                     "WHERE e.student_id = ? " +
                     "ORDER BY e.enrollment_id DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Enrollment(
                            rs.getInt("enrollment_id"),
                            rs.getInt("student_id"),
                            rs.getString("student_name"),
                            rs.getInt("course_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("semester"),
                            rs.getString("status"),
                            rs.getString("grade"),
                            rs.getTimestamp("enrolled_at")
                    ));
                }
            }
        }
        return list;
    }

    public List<Enrollment> getAllEnrollments() throws SQLException {
        List<Enrollment> list = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, e.student_id, s.name AS student_name, e.course_id, c.course_code, c.course_name, " +
                     "e.semester, e.status, e.grade, e.enrolled_at " +
                     "FROM enrollment e " +
                     "JOIN student s ON s.student_id = e.student_id " +
                     "JOIN course c ON c.course_id = e.course_id " +
                     "ORDER BY e.enrollment_id DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Enrollment(
                        rs.getInt("enrollment_id"),
                        rs.getInt("student_id"),
                        rs.getString("student_name"),
                        rs.getInt("course_id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getString("semester"),
                        rs.getString("status"),
                        rs.getString("grade"),
                        rs.getTimestamp("enrolled_at")
                ));
            }
        }
        return list;
    }

    public List<TimetableSlot> getActiveSlotsForStudent(int studentId) throws SQLException {
        List<TimetableSlot> slots = new ArrayList<>();
        String sql = "SELECT t.slot_id, t.course_id, c.course_code, t.day_of_week, t.start_time, t.end_time, t.room_number, t.faculty_id, f.name AS faculty_name " +
                     "FROM timetable t " +
                     "JOIN enrollment e ON e.course_id = t.course_id " +
                     "JOIN course c ON c.course_id = t.course_id " +
                     "JOIN faculty f ON f.faculty_id = t.faculty_id " +
                     "WHERE e.student_id = ? AND e.status = 'ENROLLED'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slots.add(new TimetableSlot(
                            rs.getInt("slot_id"),
                            rs.getInt("course_id"),
                            rs.getString("course_code"),
                            rs.getString("day_of_week"),
                            rs.getString("start_time"),
                            rs.getString("end_time"),
                            rs.getString("room_number"),
                            rs.getInt("faculty_id"),
                            rs.getString("faculty_name")
                    ));
                }
            }
        }
        return slots;
    }

    // ------------------------------------------------------------------------
    // WRITE / UPDATE METHODS
    // ------------------------------------------------------------------------

    public synchronized int insertEnrollment(int studentId, int courseId, String semester) throws SQLException {
        int nextId = 1;
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(enrollment_id), 0) + 1 FROM enrollment")) {
            if (rs.next()) {
                nextId = rs.getInt(1);
            }
        }

        String sql = "INSERT INTO enrollment (enrollment_id, student_id, course_id, semester, status, grade) " +
                     "VALUES (?, ?, ?, ?, 'ENROLLED', NULL)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextId);
            ps.setInt(2, studentId);
            ps.setInt(3, courseId);
            ps.setString(4, semester);
            ps.executeUpdate();
        }
        return nextId;
    }

    public boolean dropEnrollment(int enrollmentId) throws SQLException {
        String sql = "DELETE FROM enrollment WHERE enrollment_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            return ps.executeUpdate() > 0;
        }
    }

    public Enrollment getEnrollmentById(int enrollmentId) throws SQLException {
        String sql = "SELECT e.enrollment_id, e.student_id, s.name AS student_name, e.course_id, " +
                     "c.course_code, c.course_name, e.semester, e.status, e.grade, e.enrolled_at " +
                     "FROM enrollment e " +
                     "JOIN student s ON s.student_id = e.student_id " +
                     "JOIN course c ON c.course_id = e.course_id " +
                     "WHERE e.enrollment_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Enrollment(
                            rs.getInt("enrollment_id"),
                            rs.getInt("student_id"),
                            rs.getString("student_name"),
                            rs.getInt("course_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("semester"),
                            rs.getString("status"),
                            rs.getString("grade"),
                            rs.getTimestamp("enrolled_at")
                    );
                }
            }
        }
        return null;
    }

    public String getNextStudentRollNumber() throws SQLException {
        String sql = "SELECT roll_number FROM student ORDER BY student_id DESC LIMIT 1";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String lastRoll = rs.getString("roll_number");
                if (lastRoll != null && lastRoll.startsWith("CS2023")) {
                    try {
                        int num = Integer.parseInt(lastRoll.substring(6)) + 1;
                        return String.format("CS2023%03d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return "CS2023017";
    }

    public int addStudent(String rollNumber, String name, String email, double gpa, double attendancePct, int failedCourses) throws SQLException {
        return addStudent(rollNumber, name, email, gpa, attendancePct, failedCourses, null);
    }

    public int addStudent(String rollNumber, String name, String email, double gpa, double attendancePct, int failedCourses, String rawPassword) throws SQLException {
        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            rollNumber = getNextStudentRollNumber();
        }
        String cleanRoll = rollNumber.trim().toUpperCase();
        if (email == null || email.trim().isEmpty()) {
            String first = name.trim().split("\\s+")[0].toLowerCase();
            email = first + "@univ.edu";
        }
        double z = 6.20 - (1.50 * gpa) - (0.055 * attendancePct) + (0.65 * failedCourses);
        z = Math.max(-15.0, Math.min(15.0, z));
        double prob = 1.0 / (1.0 + Math.exp(-z));
        prob = Math.round(prob * 1000.0) / 1000.0;
        String flag = (prob >= 0.55 || gpa < 2.20 || attendancePct < 65.0 || failedCourses >= 3) ? "HIGH" :
                      ((prob >= 0.25 || gpa < 2.90 || attendancePct < 78.0 || failedCourses >= 1) ? "MEDIUM" : "LOW");

        int nextId = 1;
        String idSql = "SELECT COALESCE(MAX(student_id), 0) + 1 FROM student";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(idSql)) {
            if (rs.next()) {
                nextId = rs.getInt(1);
            }
        }

        String sql = "INSERT INTO student (student_id, roll_number, name, email, gpa, attendance_pct, failed_courses, risk_score, risk_flag) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextId);
            ps.setString(2, cleanRoll);
            ps.setString(3, name.trim());
            ps.setString(4, email.trim());
            ps.setDouble(5, gpa);
            ps.setDouble(6, attendancePct);
            ps.setInt(7, failedCourses);
            ps.setDouble(8, prob);
            ps.setString(9, flag);
            ps.executeUpdate();
        }

        // Provision student login credentials so Staff or Admin can give ID & Password to the student
        String studentPass = (rawPassword != null && !rawPassword.trim().isEmpty()) ? rawPassword.trim() : "Student@2026";
        String hash = PasswordUtil.createSecureHash(studentPass);
        String sqlUser = "INSERT OR REPLACE INTO users (username, password_hash, role, student_id, is_active) VALUES (?, ?, 'STUDENT', ?, 1)";
        try (Connection conn = getConnection();
             PreparedStatement psUser = conn.prepareStatement(sqlUser)) {
            psUser.setString(1, cleanRoll);
            psUser.setString(2, hash);
            psUser.setInt(3, nextId);
            psUser.executeUpdate();
        }

        return nextId;
    }

    public boolean updateStudent(int studentId, String name, String email, double gpa, double attendancePct) throws SQLException {
        return updateStudent(studentId, name, email, gpa, attendancePct, 0);
    }

    public boolean updateStudent(int studentId, String name, String email, double gpa, double attendancePct, int failedCourses) throws SQLException {
        String sql = "UPDATE student SET name = ?, email = ?, gpa = ?, attendance_pct = ?, failed_courses = ?, updated_at = CURRENT_TIMESTAMP WHERE student_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setDouble(3, gpa);
            ps.setDouble(4, attendancePct);
            ps.setInt(5, failedCourses);
            ps.setInt(6, studentId);
            return ps.executeUpdate() > 0;
        }
    }

    public void updateRiskFlag(int studentId, double riskScore, String riskFlag) throws SQLException {
        String sql = "UPDATE student SET risk_score = ?, risk_flag = ?, updated_at = CURRENT_TIMESTAMP WHERE student_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, riskScore);
            ps.setString(2, riskFlag);
            ps.setInt(3, studentId);
            ps.executeUpdate();
        }
    }

    public List<Student> getAtRiskStudents() throws SQLException {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT student_id, roll_number, name, email, COALESCE(mobile, '') AS mobile, gpa, attendance_pct, COALESCE(failed_courses, 0) AS failed_courses, risk_score, risk_flag " +
                     "FROM student WHERE risk_flag IN ('MEDIUM', 'HIGH') " +
                     "ORDER BY risk_score DESC, gpa ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Student s = new Student(
                        rs.getInt("student_id"),
                        rs.getString("roll_number"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getDouble("gpa"),
                        rs.getDouble("attendance_pct"),
                        rs.getInt("failed_courses"),
                        rs.getDouble("risk_score"),
                        rs.getString("risk_flag")
                );
                s.setMobile(rs.getString("mobile"));
                students.add(s);
            }
        }
        return students;
    }

    public int getTotalCreditsForStudent(int studentId, String semester) throws SQLException {
        String sql = "SELECT COALESCE(SUM(c.credits), 0) FROM enrollment e " +
                     "JOIN course c ON c.course_id = e.course_id " +
                     "WHERE e.student_id = ? AND e.semester = ? AND e.status = 'ENROLLED'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setString(2, semester);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public synchronized int addCourse(String courseCode, String courseName, int credits, String department) throws SQLException {
        int nextId = 101;
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(course_id), 100) + 1 FROM course")) {
            if (rs.next()) {
                nextId = rs.getInt(1);
            }
        }
        String sql = "INSERT INTO course (course_id, course_code, course_name, credits, department) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextId);
            ps.setString(2, courseCode.trim().toUpperCase());
            ps.setString(3, courseName.trim());
            ps.setInt(4, credits);
            ps.setString(5, department.trim());
            ps.executeUpdate();
        }
        return nextId;
    }

    public boolean editCourse(int courseId, String courseCode, String courseName, int credits, String department) throws SQLException {
        String sql = "UPDATE course SET course_code = ?, course_name = ?, credits = ?, department = ? WHERE course_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode.trim().toUpperCase());
            ps.setString(2, courseName.trim());
            ps.setInt(3, credits);
            ps.setString(4, department.trim());
            ps.setInt(5, courseId);
            return ps.executeUpdate() > 0;
        }
    }

    public void addPrerequisite(int courseId, int prerequisiteId) throws SQLException {
        String sql = "INSERT INTO prerequisite (course_id, prerequisite_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setInt(2, prerequisiteId);
            ps.executeUpdate();
        }
    }

    public synchronized int addTimetableSlot(int courseId, String dayOfWeek, String startTime, String endTime, String roomNumber, int facultyId) throws SQLException {
        int nextId = 1;
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(slot_id), 0) + 1 FROM timetable")) {
            if (rs.next()) {
                nextId = rs.getInt(1);
            }
        }
        String sql = "INSERT INTO timetable (slot_id, course_id, day_of_week, start_time, end_time, room_number, faculty_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextId);
            ps.setInt(2, courseId);
            ps.setString(3, dayOfWeek.trim().toUpperCase());
            ps.setString(4, startTime.trim());
            ps.setString(5, endTime.trim());
            ps.setString(6, roomNumber.trim());
            ps.setInt(7, facultyId);
            ps.executeUpdate();
        }
        return nextId;
    }

    public List<Faculty> getAllFaculty() throws SQLException {
        List<Faculty> list = new ArrayList<>();
        String sql = "SELECT faculty_id, name, email, department FROM faculty ORDER BY faculty_id ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Faculty(
                        rs.getInt("faculty_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("department")
                ));
            }
        }
        return list;
    }

    // ========================================================================
    // AUTHENTICATION & USER MANAGEMENT (RBAC)
    // ========================================================================

    public void initUsersTableAndDemoAccounts() {
        String createSql = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'REGISTRAR', 'ADVISOR', 'ADMIN')), " +
                "student_id INT NULL, " +
                "is_active INT DEFAULT 1 CHECK (is_active IN (0, 1)), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE SET NULL)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);

            // Check if default users already exist
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println("[*] Seeding default development/demo authentication accounts...");
                    insertUser(conn, "admin01", PasswordUtil.createSecureHash("admin123"), "ADMIN", null);
                    insertUser(conn, "registrar01", PasswordUtil.createSecureHash("registrar123"), "REGISTRAR", null);
                    insertUser(conn, "advisor01", PasswordUtil.createSecureHash("advisor123"), "ADVISOR", null);
                    insertUser(conn, "student01", PasswordUtil.createSecureHash("student123"), "STUDENT", 1);
                    insertUser(conn, "student02", PasswordUtil.createSecureHash("student123"), "STUDENT", 2);
                    insertUser(conn, "student03", PasswordUtil.createSecureHash("student123"), "STUDENT", 3);
                    insertUser(conn, "student04", PasswordUtil.createSecureHash("student123"), "STUDENT", 4);
                    insertUser(conn, "student06", PasswordUtil.createSecureHash("student123"), "STUDENT", 6);
                    insertUser(conn, "student08", PasswordUtil.createSecureHash("student123"), "STUDENT", 8);
                    System.out.println("[+] Default authentication accounts initialized successfully.");
                }
            }
        } catch (SQLException e) {
            System.err.println("[-] Warning initializing users table: " + e.getMessage());
        }
    }

    private void insertUser(Connection conn, String username, String passHash, String role, Integer studentId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO users (username, password_hash, role, student_id, is_active) VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passHash);
            ps.setString(3, role);
            if (studentId != null) {
                ps.setInt(4, studentId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    public User authenticate(String usernameOrRoll, String rawPassword, String requestedRole) throws Exception {
        if (usernameOrRoll == null || usernameOrRoll.trim().isEmpty() || rawPassword == null) {
            String missingMsg = (requestedRole != null && "STUDENT".equalsIgnoreCase(requestedRole.trim()))
                    ? "Roll number and password are required."
                    : "Username and password are required.";
            throw new IllegalArgumentException(missingMsg);
        }

        String sql = "SELECT u.id, u.username, u.password_hash, u.role, u.student_id, u.is_active, u.created_at, s.name AS student_name, s.roll_number " +
                     "FROM users u " +
                     "LEFT JOIN student s ON u.student_id = s.student_id " +
                     "WHERE LOWER(u.username) = LOWER(?) OR (s.roll_number IS NOT NULL AND LOWER(s.roll_number) = LOWER(?)) " +
                     "OR (LOWER(?) = 'admin' AND LOWER(u.username) = 'admin01') " +
                     "OR (LOWER(?) = 'registrar' AND LOWER(u.username) = 'registrar01') " +
                     "OR (LOWER(?) = 'advisor' AND LOWER(u.username) = 'advisor01')";

        String defaultInvalidMsg = (requestedRole != null && "STUDENT".equalsIgnoreCase(requestedRole.trim()))
                ? "Invalid roll number or password."
                : "Invalid username or password.";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String trimmed = usernameOrRoll.trim();
            ps.setString(1, trimmed);
            ps.setString(2, trimmed);
            ps.setString(3, trimmed);
            ps.setString(4, trimmed);
            ps.setString(5, trimmed);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(defaultInvalidMsg);
                }

                int isActive = rs.getInt("is_active");
                if (isActive != 1) {
                    throw new IllegalStateException("Your account has been deactivated. Please contact an administrator.");
                }

                String storedHash = rs.getString("password_hash");
                if (!PasswordUtil.verifyPassword(rawPassword, storedHash)) {
                    throw new IllegalArgumentException(defaultInvalidMsg);
                }

                String userRole = rs.getString("role");
                if (requestedRole != null && !requestedRole.trim().isEmpty()) {
                    String req = requestedRole.trim().toUpperCase();
                    boolean match = userRole.equalsIgnoreCase(req) ||
                                    ((req.contains("REGISTRAR") || req.contains("ADVISOR")) &&
                                     ("REGISTRAR".equalsIgnoreCase(userRole) || "ADVISOR".equalsIgnoreCase(userRole)));
                    if (!match) {
                        throw new IllegalArgumentException("Role mismatch: account is registered as " + userRole + ", not " + req + ".");
                    }
                }

                Integer studentId = rs.getObject("student_id") != null ? rs.getInt("student_id") : null;
                String studentName = rs.getString("student_name");
                String rollNumber = rs.getString("roll_number");

                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        storedHash,
                        userRole,
                        studentId,
                        studentName,
                        rollNumber,
                        true,
                        rs.getString("created_at")
                );
            }
        }
    }

    public synchronized int registerStudentAccount(String name, String rollNumber, String rawPassword, String confirmPassword) throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Roll number is required.");
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (confirmPassword == null || !rawPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        String roll = rollNumber.trim().toUpperCase();
        String enteredName = name.trim();

        // 1. Verify student account does not already exist
        String sqlCheckExisting = "SELECT u.id FROM users u " +
                                  "LEFT JOIN student s ON u.student_id = s.student_id " +
                                  "WHERE LOWER(u.username) = LOWER(?) OR (s.roll_number IS NOT NULL AND LOWER(s.roll_number) = LOWER(?))";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlCheckExisting)) {
            ps.setString(1, roll);
            ps.setString(2, roll);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("An account already exists for this roll number. Please log in.");
                }
            }
        }

        // 2. Check if student already exists in student table
        int studentId = -1;
        String sqlFind = "SELECT student_id, roll_number, name FROM student WHERE LOWER(TRIM(roll_number)) = LOWER(TRIM(?))";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setString(1, roll);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    studentId = rs.getInt("student_id");
                }
            }
        }

        // 3. If student record does not exist in student table, create and store it now!
        if (studentId <= 0) {
            String email = roll.toLowerCase() + "@college.edu";
            studentId = addStudent(roll, enteredName, email, 3.50, 85.0, 0);
        }

        if (studentId <= 0) {
            throw new SQLException("Failed to create student database record.");
        }

        // 4. Create new user account with hashed password
        String hash = PasswordUtil.createSecureHash(rawPassword);
        String sqlInsert = "INSERT INTO users (username, password_hash, role, student_id, is_active) VALUES (?, ?, 'STUDENT', ?, 1)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, roll);
            ps.setString(2, hash);
            ps.setInt(3, studentId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.password_hash, u.role, u.student_id, u.is_active, u.created_at, s.name AS student_name, s.roll_number " +
                     "FROM users u " +
                     "LEFT JOIN student s ON u.student_id = s.student_id " +
                     "ORDER BY u.id ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer studentId = rs.getObject("student_id") != null ? rs.getInt("student_id") : null;
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        studentId,
                        rs.getString("student_name"),
                        rs.getString("roll_number"),
                        rs.getInt("is_active") == 1,
                        rs.getString("created_at")
                ));
            }
        }
        return list;
    }

    public int createUser(String username, String rawPassword, String role, Integer studentId) throws SQLException {
        String hash = PasswordUtil.createSecureHash(rawPassword);
        String sql = "INSERT INTO users (username, password_hash, role, student_id, is_active) VALUES (?, ?, ?, ?, 1)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.setString(3, role.toUpperCase());
            if (studentId != null && studentId > 0) {
                ps.setInt(4, studentId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public boolean toggleUserActive(int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resetUserPassword(int userId, String newRawPassword) throws SQLException {
        String hash = PasswordUtil.createSecureHash(newRawPassword);
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteUser(int userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Looks up student record strictly by Roll Number (case-insensitive).
     */
    public Student getStudentByRollNumber(String rollNumber) throws SQLException {
        if (rollNumber == null || rollNumber.trim().isEmpty()) return null;
        String sql = "SELECT student_id, roll_number, name, email, COALESCE(mobile, '') AS mobile, gpa, attendance_pct, COALESCE(failed_courses, 0) AS failed_courses, risk_score, risk_flag " +
                     "FROM student WHERE LOWER(TRIM(roll_number)) = LOWER(TRIM(?))";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student s = new Student(
                            rs.getInt("student_id"),
                            rs.getString("roll_number"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getDouble("gpa"),
                            rs.getDouble("attendance_pct"),
                            rs.getInt("failed_courses"),
                            rs.getDouble("risk_score"),
                            rs.getString("risk_flag")
                    );
                    s.setMobile(rs.getString("mobile"));
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Searches student account details for Registrar / Advisor lookup by Roll Number OR Student Name.
     * Checks both student table and users table to reflect true account status (Active, Inactive, Not Provisioned).
     */
    public Map<String, Object> getStudentAccountDetails(String query) throws SQLException {
        if (query == null || query.trim().isEmpty()) return null;
        String cleanQuery = query.trim();

        String sql = "SELECT s.student_id, s.roll_number, s.name, s.email, COALESCE(s.mobile, '') AS mobile, " +
                     "s.gpa, s.attendance_pct, s.risk_flag, s.risk_score, " +
                     "u.id AS user_id, u.username, u.role, u.is_active, u.created_at AS user_created_at " +
                     "FROM student s " +
                     "LEFT JOIN users u ON (u.student_id = s.student_id OR LOWER(TRIM(u.username)) = LOWER(TRIM(s.roll_number))) " +
                     "WHERE LOWER(TRIM(s.roll_number)) = LOWER(TRIM(?)) " +
                     "OR LOWER(TRIM(s.name)) LIKE LOWER(?) " +
                     "ORDER BY s.student_id ASC LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanQuery);
            ps.setString(2, "%" + cleanQuery + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("studentId", rs.getInt("student_id"));
                    map.put("rollNumber", rs.getString("roll_number"));
                    map.put("name", rs.getString("name"));
                    map.put("email", rs.getString("email"));
                    map.put("mobile", rs.getString("mobile"));
                    map.put("gpa", rs.getDouble("gpa"));
                    map.put("attendancePct", rs.getDouble("attendance_pct"));
                    map.put("riskFlag", rs.getString("risk_flag"));
                    map.put("riskScore", rs.getDouble("risk_score"));

                    int sId = rs.getInt("student_id");
                    int enrolledCount = 0;
                    try (PreparedStatement psCount = conn.prepareStatement("SELECT count(*) FROM enrollment WHERE student_id = ? AND status = 'ENROLLED'")) {
                        psCount.setInt(1, sId);
                        try (ResultSet rsCount = psCount.executeQuery()) {
                            if (rsCount.next()) enrolledCount = rsCount.getInt(1);
                        }
                    }
                    map.put("enrolledCoursesCount", enrolledCount);

                    Integer userId = rs.getObject("user_id") != null ? rs.getInt("user_id") : null;
                    map.put("hasAccount", userId != null);
                    if (userId != null) {
                        map.put("userId", userId);
                        map.put("username", rs.getString("username"));
                        map.put("role", rs.getString("role"));
                        int isActive = rs.getInt("is_active");
                        map.put("accountStatus", isActive == 1 ? "Active" : "Inactive");
                        map.put("createdAt", rs.getString("user_created_at"));
                    } else {
                        map.put("accountStatus", "No Login Account Provisioned");
                    }
                    return map;
                }
            }
        }
        return null;
    }

    /**
     * Resets a student's password in the users table using their Roll Number.
     * If user record doesn't exist yet for the student, creates it.
     */
    public boolean resetStudentPasswordByRollNumber(String rollNumber, String newRawPassword) throws SQLException {
        if (rollNumber == null || rollNumber.trim().isEmpty() || newRawPassword == null || newRawPassword.trim().isEmpty()) {
            return false;
        }
        String cleanRoll = rollNumber.trim().toUpperCase();
        Student s = getStudentByRollNumber(cleanRoll);
        if (s == null) {
            return false;
        }

        String hash = PasswordUtil.createSecureHash(newRawPassword.trim());

        // Check if user record already exists
        String sqlFindUser = "SELECT id FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(?)) OR student_id = ?";
        Integer userId = null;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFindUser)) {
            ps.setString(1, cleanRoll);
            ps.setInt(2, s.getStudentId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getInt("id");
                }
            }
        }

        if (userId != null) {
            // Update existing user credentials
            String sqlUpdate = "UPDATE users SET password_hash = ?, student_id = ?, is_active = 1 WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setString(1, hash);
                ps.setInt(2, s.getStudentId());
                ps.setInt(3, userId);
                return ps.executeUpdate() > 0;
            }
        } else {
            // Create user credentials for student
            String sqlInsert = "INSERT INTO users (username, password_hash, role, student_id, is_active) VALUES (?, ?, 'STUDENT', ?, 1)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setString(1, cleanRoll);
                ps.setString(2, hash);
                ps.setInt(3, s.getStudentId());
                return ps.executeUpdate() > 0;
            }
        }
    }

    // ========================================================================
    // ADMIN SYSTEM MANAGEMENT (STUDENT, COURSE, PREREQUISITE, TIMETABLE)
    // ========================================================================

    public boolean deleteStudent(int studentId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM enrollment WHERE student_id = ?")) {
                    ps.setInt(1, studentId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE student_id = ?")) {
                    ps.setInt(1, studentId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM student WHERE student_id = ?")) {
                    ps.setInt(1, studentId);
                    int aff = ps.executeUpdate();
                    conn.commit();
                    return aff > 0;
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public boolean deleteCourse(int courseId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM enrollment WHERE course_id = ?")) {
                    ps.setInt(1, courseId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM timetable WHERE course_id = ?")) {
                    ps.setInt(1, courseId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM prerequisite WHERE course_id = ? OR prerequisite_id = ?")) {
                    ps.setInt(1, courseId);
                    ps.setInt(2, courseId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM course WHERE course_id = ?")) {
                    ps.setInt(1, courseId);
                    int aff = ps.executeUpdate();
                    conn.commit();
                    return aff > 0;
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public boolean deletePrerequisite(int courseId, int prereqId) throws SQLException {
        String sql = "DELETE FROM prerequisite WHERE course_id = ? AND prerequisite_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setInt(2, prereqId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteTimetableSlot(int slotId) throws SQLException {
        String sql = "DELETE FROM timetable WHERE slot_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, slotId);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Integer> getAdminStats() throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM student")) {
                stats.put("totalStudents", rs.next() ? rs.getInt(1) : 0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM course")) {
                stats.put("totalCourses", rs.next() ? rs.getInt(1) : 0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(DISTINCT course_id) FROM timetable")) {
                stats.put("activeCourses", rs.next() ? rs.getInt(1) : 0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM enrollment WHERE status = 'ENROLLED'")) {
                stats.put("totalRegistrations", rs.next() ? rs.getInt(1) : 0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM student WHERE risk_flag = 'HIGH'")) {
                stats.put("highRiskStudents", rs.next() ? rs.getInt(1) : 0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM student WHERE risk_flag = 'MEDIUM'")) {
                stats.put("medRiskStudents", rs.next() ? rs.getInt(1) : 0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                stats.put("totalUsers", rs.next() ? rs.getInt(1) : 0);
            }
        }
        return stats;
    }

    public Map<String, Object> getAdminReportData() throws SQLException {
        Map<String, Object> reports = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Course Enrollment Distribution
            List<Map<String, Object>> courseStats = new ArrayList<>();
            String courseSql = "SELECT c.course_id, c.course_code, c.course_name, c.credits, c.department, " +
                               "COUNT(CASE WHEN e.status = 'ENROLLED' THEN 1 END) as active_enrolled " +
                               "FROM course c " +
                               "LEFT JOIN enrollment e ON c.course_id = e.course_id " +
                               "GROUP BY c.course_id, c.course_code, c.course_name, c.credits, c.department " +
                               "ORDER BY active_enrolled DESC, c.course_code ASC";
            try (ResultSet rs = stmt.executeQuery(courseSql)) {
                while (rs.next()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("courseId", rs.getInt("course_id"));
                    c.put("courseCode", rs.getString("course_code"));
                    c.put("courseName", rs.getString("course_name"));
                    c.put("credits", rs.getInt("credits"));
                    c.put("department", rs.getString("department"));
                    c.put("enrolledCount", rs.getInt("active_enrolled"));
                    courseStats.add(c);
                }
            }
            reports.put("courseEnrollments", courseStats);

            // 2. Risk Metrics & Breakdown
            Map<String, Object> risk = new LinkedHashMap<>();
            String riskSql = "SELECT " +
                             "SUM(CASE WHEN risk_flag = 'HIGH' THEN 1 ELSE 0 END) AS high_cnt, " +
                             "SUM(CASE WHEN risk_flag = 'MEDIUM' THEN 1 ELSE 0 END) AS med_cnt, " +
                             "SUM(CASE WHEN risk_flag = 'LOW' THEN 1 ELSE 0 END) AS low_cnt, " +
                             "AVG(gpa) AS avg_gpa, " +
                             "AVG(attendance_pct) AS avg_att " +
                             "FROM student";
            try (ResultSet rs = stmt.executeQuery(riskSql)) {
                if (rs.next()) {
                    risk.put("highCount", rs.getInt("high_cnt"));
                    risk.put("mediumCount", rs.getInt("med_cnt"));
                    risk.put("lowCount", rs.getInt("low_cnt"));
                    risk.put("avgGpa", Math.round(rs.getDouble("avg_gpa") * 100.0) / 100.0);
                    risk.put("avgAttendance", Math.round(rs.getDouble("avg_att") * 10.0) / 10.0);
                }
            }
            reports.put("riskBreakdown", risk);

            // 3. Department Breakdown
            List<Map<String, Object>> deptStats = new ArrayList<>();
            String deptSql = "SELECT department, COUNT(course_id) as total_courses FROM course GROUP BY department";
            try (ResultSet rs = stmt.executeQuery(deptSql)) {
                while (rs.next()) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("department", rs.getString("department"));
                    d.put("totalCourses", rs.getInt("total_courses"));
                    deptStats.add(d);
                }
            }
            reports.put("departmentBreakdown", deptStats);
        }
        return reports;
    }
}
