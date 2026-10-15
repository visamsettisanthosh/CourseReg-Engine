package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import db.DatabaseManager;
import exceptions.RegistrationException;
import models.Course;
import models.Enrollment;
import models.Student;
import models.TimetableSlot;
import models.User;
import registrar.Registrar;
import security.OtpManager;
import security.SessionManager;
import security.SessionManager.Session;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/**
 * Embedded HTTP server (built into Java Standard Library) providing:
 * 1. RESTful JSON API for Student registration, Course catalog, DAG validation, and Clash checks.
 * 2. Static file serving for the modern web UI frontend (HTML, CSS, JS).
 * 3. Role-based Authentication & Admin endpoints.
 */
public class RegistrarHttpServer {
    private final int port;
    private final Registrar registrar;
    private final DatabaseManager dbManager;
    private HttpServer server;

    public RegistrarHttpServer(int port, Registrar registrar) {
        this.port = port;
        this.registrar = registrar;
        this.dbManager = registrar.getDbManager();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Authentication Endpoints
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/me", new MeHandler());
        server.createContext("/api/auth/logout", new LogoutHandler());
        server.createContext("/api/auth/register-student", new RegisterStudentHandler());
        server.createContext("/api/auth/register", new RegisterStudentHandler());
        server.createContext("/api/auth/forgot-password/identify", new ForgotPasswordIdentifyHandler());
        server.createContext("/api/auth/forgot-password/send-otp", new ForgotPasswordSendOtpHandler());
        server.createContext("/api/auth/forgot-password/verify-otp", new ForgotPasswordVerifyOtpHandler());
        server.createContext("/api/auth/forgot-password/reset-password", new ForgotPasswordResetPasswordHandler());

        // Student and Course Core API Endpoints
        server.createContext("/api/students", new StudentsHandler());
        server.createContext("/api/student-details", new StudentDetailsHandler());
        server.createContext("/api/student", new StudentDetailsHandler());
        server.createContext("/api/student/update", new UpdateStudentHandler());
        server.createContext("/api/student/add", new AddStudentHandler());
        server.createContext("/api/courses", new CoursesHandler());
        server.createContext("/api/validate", new ValidateHandler());
        server.createContext("/api/enroll", new EnrollHandler());
        server.createContext("/api/drop", new DropHandler());
        server.createContext("/api/enrollments", new EnrollmentsHandler());
        server.createContext("/api/at-risk", new AtRiskHandler());
        server.createContext("/api/run-risk-pipeline", new RunRiskPipelineHandler());
        server.createContext("/api/dag", new DagHandler());
        server.createContext("/api/scenarios", new ScenariosHandler());

        // Registrar / Advisor Management Endpoints
        server.createContext("/api/registrar/course/add", new AddCourseHandler());
        server.createContext("/api/registrar/course/edit", new EditCourseHandler());
        server.createContext("/api/registrar/prerequisite/add", new AddPrerequisiteHandler());
        server.createContext("/api/registrar/slot/add", new AddSlotHandler());
        server.createContext("/api/registrar/faculty", new FacultyHandler());
        server.createContext("/api/registrar/student-lookup", new RegistrarStudentLookupHandler());

        // Dedicated Admin Management Endpoints
        server.createContext("/api/admin/overview", new AdminOverviewHandler());
        server.createContext("/api/admin/users", new AdminUsersHandler());
        server.createContext("/api/admin/users/add", new AdminAddUserHandler());
        server.createContext("/api/admin/users/status", new AdminUserStatusHandler());
        server.createContext("/api/admin/users/reset-password", new AdminResetPasswordHandler());
        server.createContext("/api/admin/users/delete", new AdminDeleteUserHandler());
        server.createContext("/api/admin/student/delete", new AdminDeleteStudentHandler());
        server.createContext("/api/admin/course/delete", new AdminDeleteCourseHandler());
        server.createContext("/api/admin/prerequisite/delete", new AdminDeletePrerequisiteHandler());
        server.createContext("/api/admin/slot/delete", new AdminDeleteSlotHandler());
        server.createContext("/api/admin/reports", new AdminReportsHandler());

        // Static Web UI Files Handler
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default executor
        server.start();
        System.out.println("[+] Web Server started successfully at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------------
    // API HANDLERS
    // ------------------------------------------------------------------------

    private class StudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    List<Student> students = dbManager.getAllStudents();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < students.size(); i++) {
                        Student s = students.get(i);
                        sb.append(String.format("{\"studentId\":%d,\"rollNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"gpa\":%.2f,\"attendancePct\":%.1f,\"riskScore\":%.3f,\"riskFlag\":\"%s\"}",
                                s.getStudentId(), escape(s.getRollNumber()), escape(s.getName()), escape(s.getEmail()),
                                s.getGpa(), s.getAttendancePct(), s.getRiskScore(), escape(s.getRiskFlag())));
                        if (i < students.size() - 1) sb.append(",");
                    }
                    sb.append("]");
                    sendResponse(exchange, 200, "application/json", sb.toString());
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class StudentDetailsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String rawId = params.containsKey("studentId") ? params.get("studentId") : params.getOrDefault("id", "1");
            int studentId = Integer.parseInt(rawId);

            // Route protection: If logged in as STUDENT, can only fetch own student details
            Session session = getAuthenticatedSession(exchange);
            if (session != null && "STUDENT".equalsIgnoreCase(session.getRole())) {
                if (session.getStudentId() != null && session.getStudentId() != studentId) {
                    sendForbidden(exchange, "Access Denied: You do not have permission to access another student's information.");
                    return;
                }
            }

            try {
                Student s = dbManager.getStudentById(studentId);
                if (s == null) {
                    sendError(exchange, 404, "Student not found");
                    return;
                }
                List<Enrollment> enrollments = dbManager.getEnrollmentsForStudent(studentId);
                List<TimetableSlot> activeSlots = dbManager.getActiveSlotsForStudent(studentId);
                int currentCredits = dbManager.getTotalCreditsForStudent(studentId, registrar.getCurrentSemester());

                StringBuilder sb = new StringBuilder("{");
                sb.append(String.format("\"studentId\":%d,\"rollNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"gpa\":%.2f,\"attendancePct\":%.1f,\"failedCourses\":%d,\"currentSemester\":\"%s\",\"currentCredits\":%d,\"riskScore\":%.3f,\"riskFlag\":\"%s\",",
                        s.getStudentId(), escape(s.getRollNumber()), escape(s.getName()), escape(s.getEmail()),
                        s.getGpa(), s.getAttendancePct(), s.getFailedCourses(), escape(registrar.getCurrentSemester()),
                        currentCredits, s.getRiskScore(), escape(s.getRiskFlag())));

                // Completed and enrolled courses
                sb.append("\"enrollments\":[");
                for (int i = 0; i < enrollments.size(); i++) {
                    Enrollment e = enrollments.get(i);
                    sb.append(String.format("{\"enrollmentId\":%d,\"courseId\":%d,\"courseCode\":\"%s\",\"courseName\":\"%s\",\"semester\":\"%s\",\"status\":\"%s\",\"grade\":\"%s\"}",
                            e.getEnrollmentId(), e.getCourseId(), escape(e.getCourseCode()), escape(e.getCourseName()),
                            escape(e.getSemester()), escape(e.getStatus()), e.getGrade() != null ? escape(e.getGrade()) : "-"));
                    if (i < enrollments.size() - 1) sb.append(",");
                }
                sb.append("],");

                // Active schedule slots
                sb.append("\"schedule\":[");
                for (int i = 0; i < activeSlots.size(); i++) {
                    TimetableSlot slot = activeSlots.get(i);
                    sb.append(String.format("{\"slotId\":%d,\"courseId\":%d,\"courseCode\":\"%s\",\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"roomNumber\":\"%s\",\"facultyName\":\"%s\"}",
                            slot.getSlotId(), slot.getCourseId(), escape(slot.getCourseCode()), escape(slot.getDayOfWeek()),
                            escape(slot.getStartTime()), escape(slot.getEndTime()), escape(slot.getRoomNumber()), escape(slot.getFacultyName())));
                    if (i < activeSlots.size() - 1) sb.append(",");
                }
                sb.append("]}");

                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class UpdateStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int studentId = Integer.parseInt(data.getOrDefault("studentId", "0"));
                String name = data.getOrDefault("name", "");
                String email = data.getOrDefault("email", "");
                double gpa = Double.parseDouble(data.getOrDefault("gpa", "0.0"));
                double attendancePct = Double.parseDouble(data.getOrDefault("attendancePct", "0.0"));
                int failedCourses = Integer.parseInt(data.getOrDefault("failedCourses", "0"));

                try {
                    boolean ok = dbManager.updateStudent(studentId, name, email, gpa, attendancePct, failedCourses);
                    if (ok) {
                        // Recalculate academic risk indicator score immediately
                        double z = 6.20 - (1.50 * gpa) - (0.055 * attendancePct) + (0.65 * failedCourses);
                        z = Math.max(-15.0, Math.min(15.0, z));
                        double prob = 1.0 / (1.0 + Math.exp(-z));
                        prob = Math.round(prob * 1000.0) / 1000.0;
                        String flag = (prob >= 0.55 || gpa < 2.20 || attendancePct < 65.0 || failedCourses >= 3) ? "HIGH" :
                                      ((prob >= 0.25 || gpa < 2.90 || attendancePct < 78.0 || failedCourses >= 1) ? "MEDIUM" : "LOW");
                        dbManager.updateRiskFlag(studentId, prob, flag);

                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Student record updated successfully.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Student not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AddStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "REGISTRAR", "ADVISOR", "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String name = data.getOrDefault("name", "").trim();
                String email = data.getOrDefault("email", "").trim();
                String roll = data.getOrDefault("rollNumber", "").trim();
                String password = data.getOrDefault("password", "").trim();
                if (password.isEmpty()) {
                    password = "Student@2026";
                }
                double gpa = Double.parseDouble(data.getOrDefault("gpa", "3.0"));
                double attendancePct = Double.parseDouble(data.getOrDefault("attendancePct", "85.0"));
                int failedCourses = Integer.parseInt(data.getOrDefault("failedCourses", "0"));

                if (name.isEmpty()) {
                    sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"Student name is required.\"}");
                    return;
                }

                try {
                    int newId = dbManager.addStudent(roll, name, email, gpa, attendancePct, failedCourses, password);
                    if (newId > 0) {
                        Student createdStudent = dbManager.getStudentById(newId);
                        String createdRoll = (createdStudent != null && createdStudent.getRollNumber() != null) ? createdStudent.getRollNumber() : roll;
                        sendResponse(exchange, 200, "application/json",
                                String.format("{\"success\":true,\"studentId\":%d,\"rollNumber\":\"%s\",\"password\":\"%s\",\"message\":\"Student %s created successfully. ID: %s, Password: %s\"}",
                                        newId, escape(createdRoll), escape(password), escape(name), escape(createdRoll), escape(password)));
                    } else {
                        sendResponse(exchange, 500, "application/json", "{\"success\":false,\"error\":\"Could not insert student.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class CoursesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<Course> courses = dbManager.getAllCourses();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < courses.size(); i++) {
                    Course c = courses.get(i);
                    sb.append(String.format("{\"courseId\":%d,\"courseCode\":\"%s\",\"courseName\":\"%s\",\"credits\":%d,\"department\":\"%s\",",
                            c.getCourseId(), escape(c.getCourseCode()), escape(c.getCourseName()), c.getCredits(), escape(c.getDepartment())));

                    // Prerequisites
                    sb.append("\"prerequisites\":[");
                    List<Integer> prereqs = c.getPrerequisiteIds();
                    for (int j = 0; j < prereqs.size(); j++) {
                        sb.append(prereqs.get(j));
                        if (j < prereqs.size() - 1) sb.append(",");
                    }
                    sb.append("],");

                    // Slots
                    sb.append("\"slots\":[");
                    List<TimetableSlot> slots = c.getTimetableSlots();
                    for (int j = 0; j < slots.size(); j++) {
                        TimetableSlot s = slots.get(j);
                        sb.append(String.format("{\"slotId\":%d,\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"roomNumber\":\"%s\",\"facultyName\":\"%s\"}",
                                s.getSlotId(), escape(s.getDayOfWeek()), escape(s.getStartTime()), escape(s.getEndTime()),
                                escape(s.getRoomNumber()), escape(s.getFacultyName())));
                        if (j < slots.size() - 1) sb.append(",");
                    }
                    sb.append("]}");
                    if (i < courses.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class ValidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            int studentId = Integer.parseInt(params.getOrDefault("studentId", "0"));
            int courseId = Integer.parseInt(params.getOrDefault("courseId", "0"));

            try {
                Registrar.ValidationResult res = registrar.validateEligibility(studentId, courseId);
                StringBuilder sb = new StringBuilder("{");
                sb.append(String.format("\"eligible\":%b,\"prerequisitesMet\":%b,\"alreadyEnrolled\":%b,\"hasTimetableClash\":%b,\"creditLimitExceeded\":%b,\"currentCredits\":%d,\"newTotalCredits\":%d,",
                        res.isEligible(), res.isPrerequisitesMet(), res.isAlreadyEnrolled(), res.hasTimetableClash(), res.isCreditLimitExceeded(), res.getCurrentCredits(), res.getNewTotalCredits()));

                sb.append("\"missingPrerequisites\":[");
                List<String> missing = res.getMissingPrerequisites();
                for (int i = 0; i < missing.size(); i++) {
                    sb.append("\"").append(escape(missing.get(i))).append("\"");
                    if (i < missing.size() - 1) sb.append(",");
                }
                sb.append("],");

                String statusLabel = res.isEligible() ? "ELIGIBLE" :
                                    (res.isAlreadyEnrolled() ? "ENROLLED" :
                                    (!res.isPrerequisitesMet() ? "PREREQ REQUIRED" :
                                    (res.hasTimetableClash() ? "TIMETABLE CLASH" : "REGISTRATION BLOCKED")));

                sb.append(String.format("\"statusLabel\":\"%s\",", statusLabel));
                sb.append(String.format("\"failureReason\":%s,",
                        res.getFailureReason() != null ? "\"" + escape(res.getFailureReason()) + "\"" : "null"));
                sb.append(String.format("\"clashDetails\":%s}",
                        res.getClashDetails() != null ? "\"" + escape(res.getClashDetails()) + "\"" : "null"));

                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
        }
    }

    private class EnrollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int studentId = Integer.parseInt(data.getOrDefault("studentId", "0"));
                int courseId = Integer.parseInt(data.getOrDefault("courseId", "0"));
                String semester = data.getOrDefault("semester", "Spring 2026");

                // Route protection: If logged in as STUDENT, cannot register for other students
                Session session = getAuthenticatedSession(exchange);
                if (session != null && "STUDENT".equalsIgnoreCase(session.getRole())) {
                    if (session.getStudentId() != studentId) {
                        sendForbidden(exchange, "Access Denied: You do not have permission to register courses for another student.");
                        return;
                    }
                }

                try {
                    // Pre-validate eligibility to extract precise structured error messages
                    Registrar.ValidationResult check = registrar.validateEligibility(studentId, courseId);
                    if (!check.isEligible()) {
                        String heading = "Registration blocked";
                        String reason;
                        String details;

                        if (!check.isPrerequisitesMet()) {
                            reason = "Prerequisite requirement not satisfied.";
                            details = "Missing prerequisite: " + String.join(", ", check.getMissingPrerequisites());
                        } else if (check.hasTimetableClash()) {
                            reason = "Timetable clash detected.";
                            details = check.getClashDetails();
                        } else if (check.isAlreadyEnrolled()) {
                            reason = "Already enrolled in course.";
                            details = check.getFailureReason();
                        } else if (check.isCreditLimitExceeded()) {
                            reason = "Credit limit exceeded (Max 18 credits).";
                            details = check.getFailureReason();
                        } else {
                            reason = check.getFailureReason() != null ? check.getFailureReason() : "Eligibility criteria not met.";
                            details = reason;
                        }

                        String json = String.format(
                                "{\"success\":false,\"status\":\"REGISTRATION BLOCKED\",\"heading\":\"%s\",\"reason\":\"%s\",\"details\":\"%s\",\"error\":\"%s\"}",
                                escape(heading), escape(reason), escape(details), escape(reason));
                        sendResponse(exchange, 400, "application/json", json);
                        return;
                    }

                    Enrollment e = registrar.enrollStudent(studentId, courseId, semester);
                    String resp = String.format("{\"success\":true,\"courseTitle\":\"%s - %s\",\"prereqStatus\":\"✓ Completed\",\"timetableStatus\":\"✓ No clash\",\"status\":\"ENROLLED\",\"enrollmentId\":%d,\"message\":\"✓ Registration Successful\\n\\nCourse:\\n%s - %s\\n\\nPrerequisites:\\n✓ Completed\\n\\nTimetable:\\n✓ No clash\\n\\nStatus:\\nENROLLED\"}",
                            escape(e.getCourseCode()), escape(e.getCourseName()),
                            e.getEnrollmentId(), escape(e.getCourseCode()), escape(e.getCourseName()));
                    sendResponse(exchange, 200, "application/json", resp);
                } catch (RegistrationException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"status\":\"REGISTRATION BLOCKED\",\"heading\":\"Registration blocked\",\"reason\":\"%s\",\"details\":\"%s\",\"error\":\"%s\"}",
                                    escape(e.getMessage()), escape(e.getMessage()), escape(e.getMessage())));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "application/json",
                            String.format("{\"success\":false,\"status\":\"DATABASE ERROR\",\"reason\":\"Database error occurred\",\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json",
                            String.format("{\"success\":false,\"error\":\"Internal error: %s\"}", escape(e.getMessage())));
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class DropHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int enrollmentId = Integer.parseInt(data.getOrDefault("enrollmentId", "0"));

                // Route protection: If logged in as STUDENT, cannot drop other students' enrollments
                Session session = getAuthenticatedSession(exchange);
                if (session != null && "STUDENT".equalsIgnoreCase(session.getRole())) {
                    try {
                        Enrollment enr = dbManager.getEnrollmentById(enrollmentId);
                        if (enr == null || enr.getStudentId() != session.getStudentId()) {
                            sendForbidden(exchange, "Access Denied: You do not have permission to drop courses for another student.");
                            return;
                        }
                    } catch (SQLException e) {
                        sendError(exchange, 500, e.getMessage());
                        return;
                    }
                }

                try {
                    boolean dropped = dbManager.dropEnrollment(enrollmentId);
                    if (dropped) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Enrollment successfully dropped.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Enrollment record not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                } catch (Exception e) {
                    sendError(exchange, 500, "Internal error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class EnrollmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<Enrollment> enrollments = dbManager.getAllEnrollments();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < enrollments.size(); i++) {
                    Enrollment e = enrollments.get(i);
                    sb.append(String.format("{\"enrollmentId\":%d,\"studentId\":%d,\"studentName\":\"%s\",\"courseId\":%d,\"courseCode\":\"%s\",\"courseName\":\"%s\",\"semester\":\"%s\",\"status\":\"%s\",\"grade\":\"%s\"}",
                            e.getEnrollmentId(), e.getStudentId(), escape(e.getStudentName()), e.getCourseId(),
                            escape(e.getCourseCode()), escape(e.getCourseName()), escape(e.getSemester()),
                            escape(e.getStatus()), e.getGrade() != null ? escape(e.getGrade()) : "-"));
                    if (i < enrollments.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class AtRiskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "REGISTRAR", "ADVISOR", "ADMIN");
            if (session == null) return;

            try {
                List<Student> students = dbManager.getAtRiskStudents();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < students.size(); i++) {
                    Student s = students.get(i);
                    sb.append(String.format("{\"studentId\":%d,\"rollNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"gpa\":%.2f,\"attendancePct\":%.1f,\"riskScore\":%.3f,\"riskFlag\":\"%s\"}",
                            s.getStudentId(), escape(s.getRollNumber()), escape(s.getName()), escape(s.getEmail()),
                            s.getGpa(), s.getAttendancePct(), s.getRiskScore(), escape(s.getRiskFlag())));
                    if (i < students.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class RunRiskPipelineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "REGISTRAR", "ADVISOR", "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    // Execute python risk_score.py as external process
                    ProcessBuilder pb = new ProcessBuilder("python", "risk_score.py");
                    pb.directory(new File("."));
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();

                    StringBuilder output = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    }
                    int exitCode = proc.waitFor();

                    if (exitCode == 0) {
                        sendResponse(exchange, 200, "application/json",
                                String.format("{\"success\":true,\"message\":\"ML Risk Pipeline executed successfully.\",\"output\":\"%s\"}",
                                        escape(output.toString())));
                    } else {
                        sendResponse(exchange, 500, "application/json",
                                String.format("{\"success\":false,\"error\":\"Pipeline returned exit code %d\",\"output\":\"%s\"}",
                                        exitCode, escape(output.toString())));
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json",
                            String.format("{\"success\":false,\"error\":\"Execution error: %s\"}", escape(e.getMessage())));
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class DagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                int studentId = Integer.parseInt(params.getOrDefault("studentId", "0"));

                List<Course> courses = dbManager.getAllCourses();
                Map<Integer, List<Integer>> prereqs = dbManager.getAllPrerequisitePairs();

                Set<Integer> completedIds = new HashSet<>();
                Set<Integer> enrolledIds = new HashSet<>();
                if (studentId > 0) {
                    List<Enrollment> enrollments = dbManager.getEnrollmentsForStudent(studentId);
                    for (Enrollment e : enrollments) {
                        if ("COMPLETED".equalsIgnoreCase(e.getStatus())) {
                            completedIds.add(e.getCourseId());
                        } else if ("ENROLLED".equalsIgnoreCase(e.getStatus())) {
                            enrolledIds.add(e.getCourseId());
                        }
                    }
                }

                String cycleMessage = registrar.getPrerequisiteDAG().detectCycleDetails();
                boolean hasCycle = cycleMessage != null;

                List<Integer> topoOrder = Collections.emptyList();
                if (!hasCycle) {
                    topoOrder = registrar.getPrerequisiteDAG().getTopologicalSort();
                }

                StringBuilder sb = new StringBuilder("{");
                sb.append(String.format("\"hasCycle\":%b,", hasCycle));
                sb.append(String.format("\"cycleDetails\":%s,", cycleMessage != null ? "\"" + escape(cycleMessage) + "\"" : "null"));

                sb.append("\"topologicalSort\":[");
                for (int i = 0; i < topoOrder.size(); i++) {
                    sb.append(topoOrder.get(i));
                    if (i < topoOrder.size() - 1) sb.append(",");
                }
                sb.append("],");

                sb.append("\"nodes\":[");
                for (int i = 0; i < courses.size(); i++) {
                    Course c = courses.get(i);
                    String status = "UNCHECKED";
                    if (studentId > 0) {
                        status = registrar.getPrerequisiteDAG().getNodeStatusForStudent(c.getCourseId(), completedIds, enrolledIds);
                    }
                    sb.append(String.format("{\"id\":%d,\"label\":\"%s\",\"title\":\"%s (%d cr)\",\"credits\":%d,\"status\":\"%s\"}",
                            c.getCourseId(), escape(c.getCourseCode()), escape(c.getCourseName()), c.getCredits(), c.getCredits(), status));
                    if (i < courses.size() - 1) sb.append(",");
                }
                sb.append("],\"edges\":[");

                List<String> edgeList = new ArrayList<>();
                for (Map.Entry<Integer, List<Integer>> entry : prereqs.entrySet()) {
                    int courseId = entry.getKey();
                    for (int pId : entry.getValue()) {
                        edgeList.add(String.format("{\"from\":%d,\"to\":%d}", pId, courseId));
                    }
                }
                sb.append(String.join(",", edgeList));
                sb.append("]}");

                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class ScenariosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String scenariosJson = "[" +
                    "{\"id\":1,\"name\":\"Scenario 1: Standard Valid Enrollment\",\"studentId\":1,\"courseId\":110,\"type\":\"REGISTRATION\",\"badge\":\"VALID\",\"description\":\"Aarav Sharma (GPA 3.85, high standing) registers for CS401 (Machine Learning & AI). Prerequisites MATH201 and CS201 are verified complete; timetable has no conflict; credit limit is respected.\"}," +
                    "{\"id\":2,\"name\":\"Scenario 2: Prerequisite Violation\",\"studentId\":2,\"courseId\":105,\"type\":\"REGISTRATION\",\"badge\":\"PREREQ_VIOLATION\",\"description\":\"Bhavin Patel registers for CS201 (Algorithms). Prerequisite verification fails because MATH201 is missing from his academic record. System blocks registration immediately.\"}," +
                    "{\"id\":3,\"name\":\"Scenario 3: Timetable Clash\",\"studentId\":4,\"courseId\":108,\"type\":\"REGISTRATION\",\"badge\":\"TIMETABLE_CLASH\",\"description\":\"Diya Reddy (enrolled in CS303: Wed 14:00-15:30) registers for CS302 (Computer Networks: Wed 14:00-15:30). ADSA Interval Tree detects collision and blocks registration with exact overlap.\"}," +
                    "{\"id\":4,\"name\":\"Scenario 4: High Academic Risk Student\",\"studentId\":6,\"courseId\":0,\"type\":\"RISK\",\"badge\":\"HIGH_RISK\",\"description\":\"Farhan Khan (GPA 1.80, Attendance 54%, 3 Failed Courses). Statistical risk scoring flags student as HIGH risk (0.85+) with root cause breakdown for proactive counseling.\"}," +
                    "{\"id\":5,\"name\":\"Scenario 5: Medium Academic Risk Student\",\"studentId\":8,\"courseId\":0,\"type\":\"RISK\",\"badge\":\"MEDIUM_RISK\",\"description\":\"Harini Sundaram (GPA 2.80, Attendance 74%, 1 Failed Course). Labeled as MEDIUM risk indicator for monitor and peer tutoring intervention.\"}," +
                    "{\"id\":6,\"name\":\"Scenario 6: Low Academic Risk Student\",\"studentId\":1,\"courseId\":0,\"type\":\"RISK\",\"badge\":\"LOW_RISK\",\"description\":\"Aarav Sharma (GPA 3.85, Attendance 96%, 0 Failed Courses). Labeled as LOW risk in excellent academic standing.\"}" +
                    "]";
            sendResponse(exchange, 200, "application/json", scenariosJson);
        }
    }

    private class AddCourseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String code = data.getOrDefault("courseCode", "");
                String name = data.getOrDefault("courseName", "");
                int credits = Integer.parseInt(data.getOrDefault("credits", "3"));
                String department = data.getOrDefault("department", "Computer Science");

                try {
                    int newId = registrar.addCourse(code, name, credits, department);
                    sendResponse(exchange, 200, "application/json",
                            String.format("{\"success\":true,\"courseId\":%d,\"message\":\"Course %s created successfully.\"}", newId, escape(code)));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class EditCourseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int courseId = Integer.parseInt(data.getOrDefault("courseId", "0"));
                String code = data.getOrDefault("courseCode", "");
                String name = data.getOrDefault("courseName", "");
                int credits = Integer.parseInt(data.getOrDefault("credits", "3"));
                String department = data.getOrDefault("department", "Computer Science");

                try {
                    boolean ok = registrar.editCourse(courseId, code, name, credits, department);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json",
                                String.format("{\"success\":true,\"message\":\"Course %s updated successfully.\"}", escape(code)));
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Course not found.\"}");
                    }
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AddPrerequisiteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int courseId = Integer.parseInt(data.getOrDefault("courseId", "0"));
                int prerequisiteId = Integer.parseInt(data.getOrDefault("prerequisiteId", "0"));

                try {
                    registrar.addPrerequisite(courseId, prerequisiteId);
                    sendResponse(exchange, 200, "application/json",
                            "{\"success\":true,\"message\":\"Prerequisite link registered and DAG verified acyclic.\"}");
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (SQLException e) {
                    sendError(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AddSlotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int courseId = Integer.parseInt(data.getOrDefault("courseId", "0"));
                String day = data.getOrDefault("dayOfWeek", "MON");
                String start = data.getOrDefault("startTime", "09:00");
                String end = data.getOrDefault("endTime", "10:30");
                String room = data.getOrDefault("roomNumber", "Lab-1");
                int facultyId = Integer.parseInt(data.getOrDefault("facultyId", "1"));

                try {
                    int slotId = registrar.addTimetableSlot(courseId, day, start, end, room, facultyId);
                    sendResponse(exchange, 200, "application/json",
                            String.format("{\"success\":true,\"slotId\":%d,\"message\":\"Timetable slot added successfully.\"}", slotId));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (SQLException e) {
                    sendError(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class FacultyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    List<models.Faculty> list = dbManager.getAllFaculty();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < list.size(); i++) {
                        models.Faculty f = list.get(i);
                        sb.append(String.format("{\"facultyId\":%d,\"name\":\"%s\",\"email\":\"%s\",\"department\":\"%s\"}",
                                f.getFacultyId(), escape(f.getName()), escape(f.getEmail()), escape(f.getDepartment())));
                        if (i < list.size() - 1) sb.append(",");
                    }
                    sb.append("]");
                    sendResponse(exchange, 200, "application/json", sb.toString());
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // ========================================================================
    // AUTHENTICATION HANDLERS
    // ========================================================================

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String username = data.getOrDefault("username", "").trim();
                String password = data.getOrDefault("password", "").trim();
                String requestedRole = data.getOrDefault("requestedRole", "").trim();

                if (username.isEmpty() || password.isEmpty()) {
                    String missingMsg = "STUDENT".equalsIgnoreCase(requestedRole)
                            ? "Roll number and password are required."
                            : "Username and password are required.";
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(missingMsg)));
                    return;
                }

                try {
                    User user = dbManager.authenticate(username, password, requestedRole.isEmpty() ? null : requestedRole);
                    if (user == null) {
                        String invalidMsg = "STUDENT".equalsIgnoreCase(requestedRole)
                                ? "Invalid roll number or password."
                                : "Invalid username or password.";
                        sendResponse(exchange, 401, "application/json",
                                String.format("{\"success\":false,\"error\":\"%s\"}", escape(invalidMsg)));
                        return;
                    }
                    if (!user.isActive()) {
                        sendResponse(exchange, 403, "application/json",
                                "{\"success\":false,\"error\":\"Your account has been deactivated. Please contact an administrator.\"}");
                        return;
                    }

                    Session session = SessionManager.createSession(
                            user.getId(), user.getUsername(), user.getRole(), user.getStudentId(), user.getStudentName(), user.getRollNumber()
                    );
                    String token = session.getToken();

                    String json = String.format(
                            "{\"success\":true,\"token\":\"%s\",\"user\":{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"studentId\":%d,\"studentName\":\"%s\",\"rollNumber\":\"%s\"}}",
                            token, user.getId(), escape(user.getUsername()), escape(user.getRole()),
                            user.getStudentId() != null ? user.getStudentId() : 0,
                            user.getStudentName() != null ? escape(user.getStudentName()) : "",
                            user.getRollNumber() != null ? escape(user.getRollNumber()) : (user.getUsername() != null ? escape(user.getUsername()) : ""));
                    sendResponse(exchange, 200, "application/json", json);
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 401, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (IllegalStateException e) {
                    sendResponse(exchange, 403, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json",
                            "{\"success\":false,\"error\":\"Authentication service error. Please try again.\"}");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class RegisterStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = getAuthenticatedSession(exchange);
            if (session == null || (!"REGISTRAR".equalsIgnoreCase(session.getRole()) && !"ADVISOR".equalsIgnoreCase(session.getRole()) && !"ADMIN".equalsIgnoreCase(session.getRole()))) {
                sendResponse(exchange, 403, "application/json",
                        "{\"success\":false,\"error\":\"Public student registration is disabled. Student accounts can only be provisioned by college staff or administration.\"}");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String name = data.getOrDefault("fullName", data.getOrDefault("name", "")).trim();
                String rollNumber = data.getOrDefault("rollNumber", "").trim();
                String password = data.getOrDefault("password", "").trim();
                String confirmPassword = data.getOrDefault("confirmPassword", "").trim();

                try {
                    int newUserId = dbManager.registerStudentAccount(name, rollNumber, password, confirmPassword);
                    if (newUserId > 0) {
                        sendResponse(exchange, 200, "application/json",
                                "{\"success\":true,\"message\":\"Account created successfully.\"}");
                    } else {
                        sendResponse(exchange, 500, "application/json",
                                "{\"success\":false,\"error\":\"Could not create student account. Please try again.\"}");
                    }
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(e.getMessage())));
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "application/json",
                            "{\"success\":false,\"error\":\"Database error while creating student account.\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json",
                            "{\"success\":false,\"error\":\"An unexpected error occurred. Please try again.\"}");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class MeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Session s = getAuthenticatedSession(exchange);
                if (s == null) {
                    sendResponse(exchange, 401, "application/json", "{\"authenticated\":false,\"error\":\"Session expired or invalid.\"}");
                    return;
                }
                String json = String.format(
                        "{\"authenticated\":true,\"user\":{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"studentId\":%d,\"studentName\":\"%s\",\"rollNumber\":\"%s\"}}",
                        s.getUserId(), escape(s.getUsername()), escape(s.getRole()),
                        s.getStudentId() != null ? s.getStudentId() : 0,
                        s.getDisplayName() != null ? escape(s.getDisplayName()) : "",
                        s.getRollNumber() != null ? escape(s.getRollNumber()) : (s.getUsername() != null ? escape(s.getUsername()) : ""));
                sendResponse(exchange, 200, "application/json", json);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    String token = auth.substring(7).trim();
                    SessionManager.destroySession(token);
                }
                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Logged out successfully.\"}");
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // ========================================================================
    // STUDENT FORGOT PASSWORD & OTP RECOVERY HANDLERS
    // ========================================================================

    private class ForgotPasswordIdentifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String rollNumber = data.getOrDefault("rollNumber", "").trim();

                if (rollNumber.isEmpty()) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"Roll Number is required to initiate password reset.\"}");
                    return;
                }

                try {
                    Student s = dbManager.getStudentByRollNumber(rollNumber);
                    if (s == null) {
                        sendResponse(exchange, 404, "application/json",
                                "{\"success\":false,\"error\":\"Student account not found. Please contact the Registrar/Administrator.\"}");
                        return;
                    }

                    String maskedEmail = OtpManager.maskEmail(s.getEmail());
                    String maskedMobile = OtpManager.maskMobile(s.getMobile());

                    String json = String.format(
                            "{\"success\":true,\"studentId\":%d,\"rollNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"maskedEmail\":\"%s\",\"maskedMobile\":\"%s\"}",
                            s.getStudentId(), escape(s.getRollNumber()), escape(s.getName()),
                            escape(s.getEmail()), escape(s.getMobile()), escape(maskedEmail), escape(maskedMobile)
                    );
                    sendResponse(exchange, 200, "application/json", json);
                } catch (SQLException e) {
                    sendError(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class ForgotPasswordSendOtpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String rollNumber = data.getOrDefault("rollNumber", "").trim();
                String channel = data.getOrDefault("channel", "EMAIL").trim().toUpperCase();

                if (rollNumber.isEmpty()) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"Roll Number is required.\"}");
                    return;
                }

                try {
                    Student s = dbManager.getStudentByRollNumber(rollNumber);
                    if (s == null) {
                        sendResponse(exchange, 404, "application/json",
                                "{\"success\":false,\"error\":\"Student account not found. Please contact the Registrar/Administrator.\"}");
                        return;
                    }

                    // Check rate limiting / resend cooldown
                    String rateError = OtpManager.checkRequestRateLimit(rollNumber);
                    if (rateError != null) {
                        sendResponse(exchange, 429, "application/json",
                                String.format("{\"success\":false,\"error\":\"%s\"}", escape(rateError)));
                        return;
                    }

                    String targetChannel = "MOBILE".equalsIgnoreCase(channel) ? "MOBILE" : "EMAIL";
                    String maskedDest = "MOBILE".equalsIgnoreCase(targetChannel)
                            ? OtpManager.maskMobile(s.getMobile())
                            : OtpManager.maskEmail(s.getEmail());

                    String plainOtp = OtpManager.generateAndStoreOtp(rollNumber, targetChannel, maskedDest);
                    System.out.println(String.format("[OTP DISPATCH] Dispatched OTP [%s] via %s (%s) for student %s (%s)",
                            plainOtp, targetChannel, maskedDest, s.getName(), s.getRollNumber()));

                    String json = String.format(
                            "{\"success\":true,\"message\":\"OTP sent successfully to %s.\",\"channel\":\"%s\",\"maskedDestination\":\"%s\",\"cooldownSeconds\":%d,\"expiresInMinutes\":%d,\"demoOtp\":\"%s\"}",
                            escape(maskedDest), escape(targetChannel), escape(maskedDest), 45, 5, escape(plainOtp)
                    );
                    sendResponse(exchange, 200, "application/json", json);
                } catch (SQLException e) {
                    sendError(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class ForgotPasswordVerifyOtpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String rollNumber = data.getOrDefault("rollNumber", "").trim();
                String otp = data.getOrDefault("otp", "").trim();

                if (rollNumber.isEmpty() || otp.isEmpty()) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"Roll Number and OTP are required.\"}");
                    return;
                }

                String verifyError = OtpManager.verifyOtp(rollNumber, otp);
                if (verifyError != null) {
                    sendResponse(exchange, 400, "application/json",
                            String.format("{\"success\":false,\"error\":\"%s\"}", escape(verifyError)));
                    return;
                }

                sendResponse(exchange, 200, "application/json",
                        "{\"success\":true,\"message\":\"OTP verified successfully. You may now create a new password.\"}");
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class ForgotPasswordResetPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String rollNumber = data.getOrDefault("rollNumber", "").trim();
                String newPassword = data.getOrDefault("newPassword", "").trim();
                String confirmPassword = data.getOrDefault("confirmPassword", "").trim();

                if (rollNumber.isEmpty()) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"Roll Number is required.\"}");
                    return;
                }

                if (!OtpManager.isVerifiedForReset(rollNumber)) {
                    sendResponse(exchange, 403, "application/json",
                            "{\"success\":false,\"error\":\"OTP verification is required before resetting password.\"}");
                    return;
                }

                if (newPassword.length() < 8) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"Password must be at least 8 characters long.\"}");
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"New password and confirm password do not match.\"}");
                    return;
                }

                try {
                    boolean success = dbManager.resetStudentPasswordByRollNumber(rollNumber, newPassword);
                    if (success) {
                        OtpManager.invalidateOtp(rollNumber);
                        System.out.println("[AUTH] Password reset successfully for student roll number: " + rollNumber);
                        sendResponse(exchange, 200, "application/json",
                                "{\"success\":true,\"message\":\"Password reset successful. Please login with your new password.\"}");
                    } else {
                        sendResponse(exchange, 500, "application/json",
                                "{\"success\":false,\"error\":\"Failed to update student password in database.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // ========================================================================
    // REGISTRAR / ADVISOR STUDENT LOOKUP HANDLER
    // ========================================================================

    private class RegistrarStudentLookupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "REGISTRAR", "ADVISOR", "ADMIN");
            if (session == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String query = params.getOrDefault("query", params.getOrDefault("q", params.getOrDefault("rollNumber", ""))).trim();

                if (query.isEmpty()) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"success\":false,\"error\":\"Please enter a Roll Number or Student Name to search.\"}");
                    return;
                }

                try {
                    Map<String, Object> details = dbManager.getStudentAccountDetails(query);
                    if (details == null) {
                        sendResponse(exchange, 404, "application/json",
                                "{\"success\":false,\"error\":\"No student account found. Please verify the roll number or name.\"}");
                        return;
                    }

                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"success\":true,\"student\":{");
                    int i = 0;
                    for (Map.Entry<String, Object> entry : details.entrySet()) {
                        if (i++ > 0) sb.append(",");
                        sb.append(String.format("\"%s\":", entry.getKey()));
                        Object val = entry.getValue();
                        if (val == null) {
                            sb.append("null");
                        } else if (val instanceof Number || val instanceof Boolean) {
                            sb.append(val);
                        } else {
                            sb.append(String.format("\"%s\"", escape(val.toString())));
                        }
                    }
                    sb.append("}}");
                    sendResponse(exchange, 200, "application/json", sb.toString());
                } catch (SQLException e) {
                    sendError(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // ========================================================================
    // ADMIN DASHBOARD HANDLERS
    // ========================================================================

    private class AdminOverviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            try {
                Map<String, Integer> stats = dbManager.getAdminStats();
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    if (i++ > 0) sb.append(",");
                    sb.append(String.format("\"%s\":%d", entry.getKey(), entry.getValue()));
                }
                sb.append("}");
                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class AdminUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            try {
                List<User> users = dbManager.getAllUsers();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < users.size(); i++) {
                    User u = users.get(i);
                    sb.append(String.format(
                            "{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"studentId\":%s,\"studentName\":\"%s\",\"isActive\":%b,\"createdAt\":\"%s\"}",
                            u.getId(), escape(u.getUsername()), escape(u.getRole()),
                            u.getStudentId() != null ? u.getStudentId().toString() : "null",
                            u.getStudentName() != null ? escape(u.getStudentName()) : "",
                            u.isActive(),
                            u.getCreatedAt() != null ? escape(u.getCreatedAt()) : ""));
                    if (i < users.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    private class AdminAddUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String username = data.getOrDefault("username", "").trim();
                String password = data.getOrDefault("password", "").trim();
                String role = data.getOrDefault("role", "STUDENT").trim();
                String sIdStr = data.getOrDefault("studentId", "");
                Integer studentId = null;
                if (!sIdStr.isEmpty()) {
                    try { studentId = Integer.parseInt(sIdStr); } catch (NumberFormatException ignored) {}
                }

                if (username.isEmpty() || password.isEmpty()) {
                    sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"Username and password are required.\"}");
                    return;
                }

                try {
                    int newId = dbManager.createUser(username, password, role, studentId);
                    if (newId > 0) {
                        sendResponse(exchange, 200, "application/json",
                                String.format("{\"success\":true,\"userId\":%d,\"message\":\"User '%s' created successfully.\"}", newId, escape(username)));
                    } else {
                        sendResponse(exchange, 500, "application/json", "{\"success\":false,\"error\":\"Failed to create user.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminUserStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int userId = Integer.parseInt(data.getOrDefault("userId", "0"));
                boolean active = Boolean.parseBoolean(data.getOrDefault("active", "true"));

                try {
                    boolean ok = dbManager.toggleUserActive(userId, active);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"User status updated.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"User not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminResetPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int userId = Integer.parseInt(data.getOrDefault("userId", "0"));
                String newPassword = data.getOrDefault("newPassword", "").trim();

                if (newPassword.isEmpty()) {
                    sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"New password cannot be empty.\"}");
                    return;
                }

                try {
                    boolean ok = dbManager.resetUserPassword(userId, newPassword);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Password reset successfully.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"User not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminDeleteUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int userId = Integer.parseInt(data.getOrDefault("userId", "0"));

                try {
                    boolean ok = dbManager.deleteUser(userId);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"User deleted successfully.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"User not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminDeleteStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int studentId = Integer.parseInt(data.getOrDefault("studentId", "0"));

                try {
                    boolean ok = dbManager.deleteStudent(studentId);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Student record, enrollments, and linked user deleted.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Student not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminDeleteCourseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int courseId = Integer.parseInt(data.getOrDefault("courseId", "0"));

                try {
                    boolean ok = dbManager.deleteCourse(courseId);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Course, timetable slots, prerequisites, and enrollments deleted.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Course not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminDeletePrerequisiteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int courseId = Integer.parseInt(data.getOrDefault("courseId", "0"));
                int prereqId = Integer.parseInt(data.getOrDefault("prerequisiteId", "0"));

                try {
                    boolean ok = dbManager.deletePrerequisite(courseId, prereqId);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Prerequisite link removed successfully.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Prerequisite link not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminDeleteSlotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                int slotId = Integer.parseInt(data.getOrDefault("slotId", "0"));

                try {
                    boolean ok = dbManager.deleteTimetableSlot(slotId);
                    if (ok) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Timetable slot deleted.\"}");
                    } else {
                        sendResponse(exchange, 404, "application/json", "{\"success\":false,\"error\":\"Slot not found.\"}");
                    }
                } catch (SQLException e) {
                    sendError(exchange, 500, e.getMessage());
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private class AdminReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Session session = checkAuthorized(exchange, "ADMIN");
            if (session == null) return;

            try {
                Map<String, Object> data = dbManager.getAdminReportData();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> courseStats = (List<Map<String, Object>>) data.get("courseEnrollments");
                @SuppressWarnings("unchecked")
                Map<String, Object> risk = (Map<String, Object>) data.get("riskBreakdown");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> deptStats = (List<Map<String, Object>>) data.get("departmentBreakdown");

                StringBuilder sb = new StringBuilder("{");
                sb.append("\"courseEnrollments\":[");
                for (int i = 0; i < courseStats.size(); i++) {
                    Map<String, Object> c = courseStats.get(i);
                    sb.append(String.format("{\"courseId\":%d,\"courseCode\":\"%s\",\"courseName\":\"%s\",\"credits\":%d,\"department\":\"%s\",\"enrolledCount\":%d}",
                            (Integer) c.get("courseId"), escape((String) c.get("courseCode")), escape((String) c.get("courseName")),
                            (Integer) c.get("credits"), escape((String) c.get("department")), (Integer) c.get("enrolledCount")));
                    if (i < courseStats.size() - 1) sb.append(",");
                }
                sb.append("],");

                sb.append("\"riskBreakdown\":{");
                sb.append(String.format("\"highCount\":%d,\"mediumCount\":%d,\"lowCount\":%d,\"avgGpa\":%.2f,\"avgAttendance\":%.1f",
                        (Integer) risk.get("highCount"), (Integer) risk.get("mediumCount"), (Integer) risk.get("lowCount"),
                        (Double) risk.get("avgGpa"), (Double) risk.get("avgAttendance")));
                sb.append("},");

                sb.append("\"departmentBreakdown\":[");
                for (int i = 0; i < deptStats.size(); i++) {
                    Map<String, Object> d = deptStats.get(i);
                    sb.append(String.format("{\"department\":\"%s\",\"totalCourses\":%d}",
                            escape((String) d.get("department")), (Integer) d.get("totalCourses")));
                    if (i < deptStats.size() - 1) sb.append(",");
                }
                sb.append("]}");

                sendResponse(exchange, 200, "application/json", sb.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------
    // STATIC FILE HANDLER
    // ------------------------------------------------------------------------

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            File webDir = new File("web");
            File file = new File(webDir, path.substring(1));

            if (!file.exists() || file.isDirectory()) {
                sendError(exchange, 404, "File Not Found: " + path);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";

            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ------------------------------------------------------------------------
    // UTILITY METHODS & SECURITY
    // ------------------------------------------------------------------------

    private boolean handleCors(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE, PUT");
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE, PUT");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String error) throws IOException {
        String json = String.format("{\"success\":false,\"error\":\"%s\"}", escape(error));
        sendResponse(exchange, status, "application/json", json);
    }

    private void sendForbidden(HttpExchange exchange, String message) throws IOException {
        String json = String.format("{\"success\":false,\"error\":\"%s\",\"code\":\"FORBIDDEN\"}", escape(message));
        sendResponse(exchange, 403, "application/json", json);
    }

    private Session getAuthenticatedSession(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            return SessionManager.getSession(token);
        }
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String token = params.get("token");
        if (token != null && !token.isEmpty()) {
            return SessionManager.getSession(token);
        }
        return null;
    }

    private Session checkAuthorized(HttpExchange exchange, String... allowedRoles) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE, PUT");
            exchange.sendResponseHeaders(204, -1);
            return null;
        }

        Session session = getAuthenticatedSession(exchange);
        if (session == null) {
            String json = "{\"success\":false,\"error\":\"Authentication required. Please log in.\",\"code\":\"UNAUTHORIZED\"}";
            sendResponse(exchange, 401, "application/json", json);
            return null;
        }

        if (allowedRoles != null && allowedRoles.length > 0) {
            boolean allowed = false;
            for (String role : allowedRoles) {
                if (role.equalsIgnoreCase(session.getRole()) ||
                    ("REGISTRAR".equalsIgnoreCase(role) && "ADVISOR".equalsIgnoreCase(session.getRole())) ||
                    ("ADVISOR".equalsIgnoreCase(role) && "REGISTRAR".equalsIgnoreCase(session.getRole()))) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                sendForbidden(exchange, "Access Denied: You do not have permission to access this page.");
                return null;
            }
        }
        return session;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                params.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            } else {
                params.put(pair[0], "");
            }
        }
        return params;
    }

    private Map<String, String> parseJsonSimple(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([a-zA-Z0-9.-]+))");
        java.util.regex.Matcher m = p.matcher(json);
        while (m.find()) {
            String key = m.group(1).trim();
            String val = m.group(2) != null ? m.group(2).trim() : (m.group(3) != null ? m.group(3).trim() : "");
            map.put(key, val);
        }
        return map;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
