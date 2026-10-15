import db.DatabaseManager;
import exceptions.PrerequisiteNotMetException;
import exceptions.RegistrationException;
import exceptions.TimetableClashException;
import models.Course;
import models.Enrollment;
import models.Student;
import registrar.Registrar;
import server.RegistrarHttpServer;

import java.util.List;

/**
 * Main Application Runner.
 * Coordinates:
 * - Layer 1 (Database & JDBC)
 * - Layer 2 (Registrar, Prerequisite DAG, Interval Tree clash detector)
 * - Layer 3 (Academic Advisor risk alerts from Python ML classifier)
 * - Modern Web Server (REST API + Single-page interactive frontend)
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("  COLLEGE COURSE REGISTRATION & TIMETABLE SYSTEM (OOPJ + ADSA + DBMS + ML)");
        System.out.println("==========================================================================");

        // 1. Initialize Database Manager (Layer 1)
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.initializeSchemaIfEmpty("schema.sql");

        // 2. Initialize Registrar Module (Layer 2)
        Registrar registrar = new Registrar(dbManager, "Spring 2026");

        // 3. Print Demonstration Verification
        runDemonstrationTests(registrar);

        // 4. Display Advisor Alerts from Layer 3 Risk Model
        registrar.printAdvisorRiskAlerts();

        // 5. Start Modern Web Server
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        RegistrarHttpServer httpServer = null;
        for (int p = port; p <= port + 5; p++) {
            try {
                httpServer = new RegistrarHttpServer(p, registrar);
                httpServer.start();
                port = p;
                break;
            } catch (java.net.BindException e) {
                System.out.println("[-] Port " + p + " is already in use. Trying next port " + (p + 1) + "...");
            } catch (Exception e) {
                System.err.println("[-] Failed to start Web Server on port " + p + ": " + e.getMessage());
                break;
            }
        }

        if (httpServer != null) {
            System.out.println("==========================================================================");
            System.out.println("  SYSTEM ONLINE & READY!");
            System.out.println("  Open the Modern Web UI in your browser:");
            System.out.println("  >>> http://localhost:" + port + " <<<");
            System.out.println("==========================================================================");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException ignored) {}
        } else {
            System.err.println("[-] Could not bind web server. Tip: Check ports with 'netstat -ano | findstr :8080'.");
        }
    }

    private static void runDemonstrationTests(Registrar registrar) {
        System.out.println("\n--- RUNNING AUTOMATED CORE VALIDATION TESTS ---");

        try {
            // Test Case 1: Prerequisite Validation Failure
            // Student 3 (Chirag) has NOT completed CS102 or MATH201, trying to enroll in CS201 (Algorithms)
            System.out.println("\n[Test 1] Testing Prerequisite Validation on Chirag Gupta (ID 3) for CS201 (Algorithms)...");
            Registrar.ValidationResult test1 = registrar.validateEligibility(3, 105);
            if (!test1.isPrerequisitesMet()) {
                System.out.println("  [PASS] Correctly blocked missing prerequisites: " + test1.getMissingPrerequisites());
            } else {
                System.out.println("  [FAIL] Prerequisite check failed to block enrollment!");
            }

            // Test Case 2: Timetable Clash Detection Failure
            // Student 1 (Aarav) is enrolled in CS202 (Mon 09:00-10:30).
            // Trying to enroll in CS101 (Intro to Prog), which also runs on Mon 09:00-10:30.
            System.out.println("\n[Test 2] Testing Timetable Clash Detection on Aarav Sharma (ID 1) for CS101 (Mon 09:00-10:30)...");
            Registrar.ValidationResult test2 = registrar.validateEligibility(1, 102);
            if (test2.hasTimetableClash()) {
                System.out.println("  [PASS] Correctly detected timetable clash: " + test2.getClashDetails());
            } else {
                System.out.println("  [FAIL] Clash check failed to detect overlapping interval!");
            }

            // Test Case 3: Successful Registration
            // Student 4 (Diya) has completed MATH101, CS101, MATH201, CS102, CS202.
            // Eligible for CS303 (Software Engineering) without clash.
            System.out.println("\n[Test 3] Testing Valid Registration on Diya Reddy (ID 4) for CS303 (Software Engineering)...");
            Registrar.ValidationResult test3 = registrar.validateEligibility(4, 109);
            System.out.printf("  [RESULT] Eligible: %b, Prereqs Met: %b, Clash: %b%n",
                    test3.isEligible(), test3.isPrerequisitesMet(), test3.hasTimetableClash());

            System.out.println("--- CORE VALIDATION TESTS COMPLETED ---\n");
        } catch (Exception e) {
            System.err.println("[-] Error running demonstration tests: " + e.getMessage());
        }
    }
}
