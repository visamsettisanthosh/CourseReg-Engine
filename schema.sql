-- ============================================================================
-- COURSE REGISTRATION & TIMETABLE SYSTEM
-- Layer 1: Database DDL, Constraints, Relational Algebra, and Seed Data
-- Compatible with PostgreSQL, MySQL, and SQLite
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. DROP EXISTING TABLES (In reverse dependency order)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS timetable;
DROP TABLE IF EXISTS prerequisite;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS faculty;
DROP TABLE IF EXISTS student;

-- ----------------------------------------------------------------------------
-- 2. CREATE ENTITY TABLES
-- ----------------------------------------------------------------------------

-- Faculty Entity
CREATE TABLE faculty (
    faculty_id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    department VARCHAR(50) NOT NULL
);

-- Course Entity
CREATE TABLE course (
    course_id INT PRIMARY KEY,
    course_code VARCHAR(15) UNIQUE NOT NULL,
    course_name VARCHAR(120) NOT NULL,
    credits INT NOT NULL CHECK (credits > 0 AND credits <= 6),
    department VARCHAR(50) NOT NULL
);

-- Student Entity
-- risk_flag: 'LOW', 'MEDIUM', 'HIGH' (color coded Green, Yellow, Red)
CREATE TABLE student (
    student_id INT PRIMARY KEY,
    roll_number VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    gpa DECIMAL(3, 2) NOT NULL CHECK (gpa >= 0.0 AND gpa <= 4.0),
    attendance_pct DECIMAL(5, 2) NOT NULL CHECK (attendance_pct >= 0.0 AND attendance_pct <= 100.0),
    failed_courses INT DEFAULT 0 CHECK (failed_courses >= 0),
    risk_score DECIMAL(4, 3) DEFAULT 0.000,
    risk_flag VARCHAR(10) DEFAULT 'LOW' CHECK (risk_flag IN ('LOW', 'MEDIUM', 'HIGH')),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Users Entity (Authentication & Role-Based Access Control)
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'REGISTRAR', 'ADVISOR', 'ADMIN')),
    student_id INT NULL,
    is_active INT DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE SET NULL
);

-- Prerequisite Relation (Forms a Directed Acyclic Graph: course_id -> prerequisite_id)
-- Meaning: To enroll in course_id, the student must have completed prerequisite_id.
CREATE TABLE prerequisite (
    course_id INT NOT NULL,
    prerequisite_id INT NOT NULL,
    PRIMARY KEY (course_id, prerequisite_id),
    FOREIGN KEY (course_id) REFERENCES course(course_id) ON DELETE CASCADE,
    FOREIGN KEY (prerequisite_id) REFERENCES course(course_id) ON DELETE CASCADE,
    CONSTRAINT chk_no_self_prereq CHECK (course_id <> prerequisite_id)
);

-- Timetable Entity (Course schedule intervals: Day, Start Time, End Time)
-- Times represented in HH:MM format (24-hour military format, e.g., '09:00', '10:30')
CREATE TABLE timetable (
    slot_id INT PRIMARY KEY,
    course_id INT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI')),
    start_time VARCHAR(5) NOT NULL,
    end_time VARCHAR(5) NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    faculty_id INT NOT NULL,
    FOREIGN KEY (course_id) REFERENCES course(course_id) ON DELETE CASCADE,
    FOREIGN KEY (faculty_id) REFERENCES faculty(faculty_id) ON DELETE RESTRICT,
    CONSTRAINT chk_valid_time_interval CHECK (start_time < end_time)
);

-- Enrollment Entity (Associative entity connecting Student and Course)
-- status: 'COMPLETED', 'ENROLLED', 'DROPPED', 'PENDING'
CREATE TABLE enrollment (
    enrollment_id INT PRIMARY KEY,
    student_id INT NOT NULL,
    course_id INT NOT NULL,
    semester VARCHAR(20) NOT NULL,
    status VARCHAR(15) NOT NULL CHECK (status IN ('COMPLETED', 'ENROLLED', 'DROPPED', 'PENDING')),
    grade VARCHAR(2), -- e.g., 'A', 'B', 'C', 'F', or NULL if currently enrolled
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES course(course_id) ON DELETE CASCADE,
    CONSTRAINT uq_student_course_sem UNIQUE (student_id, course_id, semester)
);

-- ----------------------------------------------------------------------------
-- 3. RELATIONAL ALGEBRA & CRITICAL SQL QUERIES
-- ----------------------------------------------------------------------------

/*
   A. TIMETABLE CLASH DETECTION
   -----------------------------------------------------------------------------
   Mathematical Formulation:
   Two intervals [s1, e1) and [s2, e2) overlap if and only if:
       s1 < e2  AND  s2 < e1
   Given a student S attempting to register for Course C_new with slot T_new:
   A clash exists if there exists an active enrollment for student S in Course C_existing
   with slot T_existing such that:
       T_new.day_of_week = T_existing.day_of_week
       AND T_new.start_time < T_existing.end_time
       AND T_existing.start_time < T_new.end_time

   Relational Algebra Expression:
   ActiveSlots_S  ← π_{slot_id, course_id, day_of_week, start_time, end_time}(
                         (σ_{student_id = S ∧ status = 'ENROLLED'}(Enrollment)) ⨝ Timetable
                      )
   CandidateSlots_C ← π_{slot_id, course_id, day_of_week, start_time, end_time}(
                         σ_{course_id = C_new}(Timetable)
                      )
   Clashes ← σ_{ActiveSlots_S.day_of_week = CandidateSlots_C.day_of_week
               ∧ ActiveSlots_S.start_time < CandidateSlots_C.end_time
               ∧ CandidateSlots_C.start_time < ActiveSlots_S.end_time}(
                 ActiveSlots_S ⨯ CandidateSlots_C
             )

   SQL Query for Clash Detection:
*/
-- Query to detect if student :target_student_id has a clash with course :candidate_course_id:
-- SELECT
--     c_existing.course_code AS conflicting_course,
--     t_existing.day_of_week,
--     t_existing.start_time AS existing_start,
--     t_existing.end_time AS existing_end,
--     t_cand.start_time AS candidate_start,
--     t_cand.end_time AS candidate_end
-- FROM timetable t_cand
-- JOIN timetable t_existing
--   ON t_cand.day_of_week = t_existing.day_of_week
--  AND t_cand.start_time < t_existing.end_time
--  AND t_existing.start_time < t_cand.end_time
-- JOIN enrollment e
--   ON e.course_id = t_existing.course_id
-- JOIN course c_existing
--   ON c_existing.course_id = e.course_id
-- WHERE t_cand.course_id = :candidate_course_id
--   AND e.student_id = :target_student_id
--   AND e.status = 'ENROLLED';

/*
   B. PREREQUISITE VALIDATION
   -----------------------------------------------------------------------------
   Mathematical Formulation:
   Let P(C) = { p | (C, p) ∈ Prerequisite } be the direct prerequisites of C.
   Let Comp(S) = { c | (S, c, 'COMPLETED') ∈ Enrollment } be the courses completed by S.
   Valid Enrollment Condition:
       P(C) ⊆ Comp(S)
   Equivalently, the set of missing prerequisites is:
       Missing(S, C) = P(C) - Comp(S)
   Enrollment is permitted if and only if Missing(S, C) = ∅.

   Relational Algebra Expression:
   ReqPrereqs   ← π_{prerequisite_id}(σ_{course_id = C_target}(Prerequisite))
   PassedCourses ← π_{course_id}(σ_{student_id = S_target ∧ status = 'COMPLETED'}(Enrollment))
   Missing      ← ReqPrereqs - PassedCourses

   SQL Query for Prerequisite Validation:
*/
-- Query to identify missing prerequisites for student :target_student_id and course :candidate_course_id:
-- SELECT p.prerequisite_id, c.course_code, c.course_name
-- FROM prerequisite p
-- JOIN course c ON c.course_id = p.prerequisite_id
-- WHERE p.course_id = :candidate_course_id
--   AND p.prerequisite_id NOT IN (
--       SELECT course_id
--       FROM enrollment
--       WHERE student_id = :target_student_id
--         AND status = 'COMPLETED'
--   );

/*
   C. DAG CYCLE DETECTION QUERY (Recursive CTE for transitive closure)
*/
-- WITH RECURSIVE PrereqPath(origin_course, curr_course, depth) AS (
--     SELECT course_id, prerequisite_id, 1
--     FROM prerequisite
--     UNION ALL
--     SELECT pp.origin_course, p.prerequisite_id, pp.depth + 1
--     FROM PrereqPath pp
--     JOIN prerequisite p ON pp.curr_course = p.course_id
--     WHERE pp.depth < 10 -- Guards against infinite recursion
-- )
-- SELECT origin_course, curr_course
-- FROM PrereqPath
-- WHERE origin_course = curr_course; -- Non-empty result implies a cycle exists!


-- ----------------------------------------------------------------------------
-- 4. SAMPLE SEED DATA
-- ----------------------------------------------------------------------------

-- Faculty Members (5 professors)
INSERT INTO faculty (faculty_id, name, email, department) VALUES
(1, 'Dr. Alan santhosh', 'turing@univ.edu', 'Computer Science'),
(2, 'Dr. Ada Lovelace', 'ada@univ.edu', 'Computer Science'),
(3, 'Dr. Claude Shannon', 'shannon@univ.edu', 'Computer Science'),
(4, 'Dr. Grace Hopper', 'hopper@univ.edu', 'Computer Science'),
(5, 'Dr. Carl Gauss', 'gauss@univ.edu', 'Mathematics');

-- Courses (11 Courses with hierarchical dependencies)
INSERT INTO course (course_id, course_code, course_name, credits, department) VALUES
(101, 'MATH101', 'Calculus I', 4, 'Mathematics'),
(102, 'CS101',   'Introduction to Programming', 4, 'Computer Science'),
(103, 'MATH201', 'Discrete Mathematics', 3, 'Mathematics'),
(104, 'CS102',   'Data Structures and OOP', 4, 'Computer Science'),
(105, 'CS201',   'Algorithms & Complexity', 4, 'Computer Science'),
(106, 'CS202',   'Database Management Systems', 3, 'Computer Science'),
(107, 'CS301',   'Operating Systems', 4, 'Computer Science'),
(108, 'CS302',   'Computer Networks', 3, 'Computer Science'),
(109, 'CS303',   'Software Engineering', 3, 'Computer Science'),
(110, 'CS401',   'Machine Learning & AI', 4, 'Computer Science'),
(111, 'CS402',   'Distributed Systems', 4, 'Computer Science');

-- Prerequisites DAG (course_id -> prerequisite_id)
-- Graph Structure:
-- MATH101 (Root)
-- CS101   (Root)
-- MATH201 -> MATH101
-- CS102   -> CS101
-- CS201   -> CS102, MATH201
-- CS202   -> CS102
-- CS301   -> CS102
-- CS302   -> CS102
-- CS303   -> CS202
-- CS401   -> CS201, MATH201
-- CS402   -> CS301, CS302
INSERT INTO prerequisite (course_id, prerequisite_id) VALUES
(103, 101), -- MATH201 requires MATH101
(104, 102), -- CS102 requires CS101
(105, 104), -- CS201 requires CS102
(105, 103), -- CS201 requires MATH201
(106, 104), -- CS202 requires CS102
(107, 104), -- CS301 requires CS102
(108, 104), -- CS302 requires CS102
(109, 106), -- CS303 requires CS202
(110, 105), -- CS401 requires CS201
(110, 103), -- CS401 requires MATH201
(111, 107), -- CS402 requires CS301
(111, 108); -- CS402 requires CS302

-- Timetable Slots (Weekly Schedule)
-- Notice deliberate overlaps for realistic clash testing:
-- Mon 09:00-10:30 (CS101, CS202 share same time! Clash if taking both)
-- Mon 10:00-11:30 (CS201 overlaps with CS101/CS202 from 10:00-10:30)
INSERT INTO timetable (slot_id, course_id, day_of_week, start_time, end_time, room_number, faculty_id) VALUES
(1,  101, 'MON', '08:00', '09:00', 'Hall-A', 5),
(2,  101, 'WED', '08:00', '09:00', 'Hall-A', 5),
(3,  102, 'MON', '09:00', '10:30', 'Lab-1',  1),
(4,  102, 'WED', '09:00', '10:30', 'Lab-1',  1),
(5,  103, 'TUE', '09:00', '10:30', 'Hall-B', 5),
(6,  103, 'THU', '09:00', '10:30', 'Hall-B', 5),
(7,  104, 'TUE', '11:00', '12:30', 'Lab-2',  2),
(8,  104, 'THU', '11:00', '12:30', 'Lab-2',  2),
(9,  105, 'MON', '10:00', '11:30', 'Hall-C', 3), -- Clashes with Mon 09:00-10:30 (10:00-10:30 overlap)
(10, 105, 'WED', '10:00', '11:30', 'Hall-C', 3),
(11, 106, 'MON', '09:00', '10:30', 'Lab-3',  2), -- Clashes directly with CS102/CS101 slots
(12, 106, 'FRI', '09:00', '10:30', 'Lab-3',  2),
(13, 107, 'TUE', '14:00', '15:30', 'Hall-A', 4),
(14, 107, 'THU', '14:00', '15:30', 'Hall-A', 4),
(15, 108, 'WED', '14:00', '15:30', 'Hall-B', 3),
(16, 108, 'FRI', '14:00', '15:30', 'Hall-B', 3),
(17, 109, 'MON', '14:00', '15:30', 'Hall-C', 2),
(18, 109, 'WED', '14:00', '15:30', 'Hall-C', 2),
(19, 110, 'TUE', '10:00', '11:30', 'Lab-1',  1), -- Overlaps with CS104 (11:00-11:30)
(20, 110, 'THU', '10:00', '11:30', 'Lab-1',  1),
(21, 111, 'FRI', '10:00', '11:30', 'Lab-2',  4);

-- Students (16 Students with various academic profiles)
-- Risk flag initially LOW, to be computed/updated by Python risk_score.py
INSERT INTO student (student_id, roll_number, name, email, gpa, attendance_pct, failed_courses, risk_score, risk_flag) VALUES
(1,  'CS2023001', 'Aarav Sharma',     'aarav@univ.edu',     3.85, 96.0, 0, 0.005, 'LOW'),
(2,  'CS2023002', 'Bhavin Patel',     'bhavin@univ.edu',    3.40, 88.5, 0, 0.015, 'LOW'),
(3,  'CS2023003', 'Chirag Gupta',     'chirag@univ.edu',    2.10, 62.0, 2, 0.427, 'HIGH'),
(4,  'CS2023004', 'Diya Reddy',       'diya@univ.edu',      3.92, 98.0, 0, 0.004, 'LOW'),
(5,  'CS2023005', 'Esha Mukherjee',   'esha@univ.edu',      2.45, 68.0, 1, 0.221, 'MEDIUM'),
(6,  'CS2023006', 'Farhan Khan',      'farhan@univ.edu',    1.80, 54.0, 3, 0.673, 'HIGH'),
(7,  'CS2023007', 'Gautam Nair',      'gautam@univ.edu',    3.10, 82.0, 0, 0.038, 'LOW'),
(8,  'CS2023008', 'Harini Sundaram',  'harini@univ.edu',    2.80, 74.0, 1, 0.097, 'MEDIUM'),
(9,  'CS2023009', 'Ishaan Malhotra',  'ishaan@univ.edu',    3.65, 91.0, 0, 0.009, 'LOW'),
(10, 'CS2023010', 'Jyoti Verma',      'jyoti@univ.edu',     1.65, 49.0, 3, 0.785, 'HIGH'),
(11, 'CS2023011', 'Karthik Menon',    'karthik@univ.edu',   2.95, 79.0, 0, 0.057, 'LOW'),
(12, 'CS2023012', 'Lavanya Joshi',    'lavanya@univ.edu',   3.75, 94.0, 0, 0.006, 'LOW'),
(13, 'CS2023013', 'Manish Iyer',      'manish@univ.edu',    2.30, 65.0, 2, 0.306, 'MEDIUM'),
(14, 'CS2023014', 'Neha Kapoor',      'neha@univ.edu',      3.20, 85.0, 0, 0.026, 'LOW'),
(15, 'CS2023015', 'Omkar Deshmukh',   'omkar@univ.edu',     2.60, 71.0, 1, 0.154, 'MEDIUM'),
(16, 'CS2023016', 'Priya Sharma',     'priya@univ.edu',     3.50, 89.0, 0, 0.013, 'LOW');

-- Prior Completed Enrollments (Transcript history) & Active Enrollments
-- Aarav (ID 1): Completed MATH101, CS101, MATH201, CS102, CS201. Eligible for CS401!
INSERT INTO enrollment (enrollment_id, student_id, course_id, semester, status, grade) VALUES
(1,  1, 101, 'Fall 2024',   'COMPLETED', 'A'),
(2,  1, 102, 'Fall 2024',   'COMPLETED', 'A'),
(3,  1, 103, 'Spring 2025', 'COMPLETED', 'A'),
(4,  1, 104, 'Spring 2025', 'COMPLETED', 'A'),
(5,  1, 105, 'Fall 2025',   'COMPLETED', 'A'),
(6,  1, 106, 'Spring 2026', 'ENROLLED',  NULL), -- Currently enrolled in CS202 (Mon 09:00-10:30)

-- Bhavin (ID 2): Completed MATH101, CS101, CS102. Has NOT completed MATH201 yet.
(7,  2, 101, 'Fall 2024',   'COMPLETED', 'B'),
(8,  2, 102, 'Fall 2024',   'COMPLETED', 'B'),
(9,  2, 104, 'Spring 2025', 'COMPLETED', 'B'),
(10, 2, 107, 'Spring 2026', 'ENROLLED',  NULL), -- Currently enrolled in CS301 (Tue/Thu 14:00-15:30)

-- Chirag (ID 3): Completed CS101 only. Low grades.
(11, 3, 102, 'Fall 2024',   'COMPLETED', 'C'),
(12, 3, 104, 'Spring 2026', 'ENROLLED',  NULL),

-- Diya (ID 4): Completed MATH101, CS101, MATH201, CS102, CS106
(13, 4, 101, 'Fall 2024',   'COMPLETED', 'A'),
(14, 4, 102, 'Fall 2024',   'COMPLETED', 'A'),
(15, 4, 103, 'Spring 2025', 'COMPLETED', 'A'),
(16, 4, 104, 'Spring 2025', 'COMPLETED', 'A'),
(17, 4, 106, 'Fall 2025',   'COMPLETED', 'A'),
(18, 4, 109, 'Spring 2026', 'ENROLLED',  NULL),

-- Esha (ID 5): Completed CS101 with C
(19, 5, 102, 'Fall 2024',   'COMPLETED', 'C'),

-- Farhan (ID 6): Struggling, incomplete basics
(20, 6, 101, 'Fall 2024',   'COMPLETED', 'D'),

-- Gautam (ID 7): Completed MATH101, CS101
(21, 7, 101, 'Fall 2024',   'COMPLETED', 'B'),
(22, 7, 102, 'Fall 2024',   'COMPLETED', 'B'),

-- Harini (ID 8): Completed CS101
(23, 8, 102, 'Fall 2024',   'COMPLETED', 'C'),

-- Ishaan (ID 9): Completed MATH101, CS101, CS102, CS107
(24, 9, 101, 'Fall 2024',   'COMPLETED', 'A'),
(25, 9, 102, 'Fall 2024',   'COMPLETED', 'A'),
(26, 9, 104, 'Spring 2025', 'COMPLETED', 'A'),
(27, 9, 107, 'Fall 2025',   'COMPLETED', 'B'),
(28, 9, 108, 'Spring 2026', 'ENROLLED',  NULL),

-- Jyoti (ID 10): High risk student, no prerequisites completed
(29, 10, 101, 'Fall 2024',  'DROPPED',   'F'),

-- Karthik (ID 11): Completed MATH101, CS101
(30, 11, 101, 'Fall 2024',  'COMPLETED', 'B'),
(31, 11, 102, 'Fall 2024',  'COMPLETED', 'B'),

-- Lavanya (ID 12): Completed MATH101, MATH201, CS101, CS102, CS201
(32, 12, 101, 'Fall 2024',  'COMPLETED', 'A'),
(33, 12, 102, 'Fall 2024',  'COMPLETED', 'A'),
(34, 12, 103, 'Spring 2025', 'COMPLETED', 'A'),
(35, 12, 104, 'Spring 2025', 'COMPLETED', 'A'),
(36, 12, 105, 'Fall 2025',   'COMPLETED', 'A'),
(37, 12, 110, 'Spring 2026', 'ENROLLED',  NULL),

-- Manish (ID 13): High risk, completed CS101 with D
(38, 13, 102, 'Fall 2024',  'COMPLETED', 'D'),

-- Neha (ID 14): Completed MATH101, CS101, CS102
(39, 14, 101, 'Fall 2024',  'COMPLETED', 'B'),
(40, 14, 102, 'Fall 2024',  'COMPLETED', 'B'),
(41, 14, 104, 'Spring 2025', 'COMPLETED', 'B'),

-- Oscar (ID 15) -> Omkar (ID 15): Medium risk, completed CS101
(42, 15, 102, 'Fall 2024',  'COMPLETED', 'C'),

-- Priya (ID 16): Completed MATH101, CS101, CS102
(43, 16, 101, 'Fall 2024',  'COMPLETED', 'A'),
(44, 16, 102, 'Fall 2024',  'COMPLETED', 'A'),
(45, 16, 104, 'Spring 2025', 'COMPLETED', 'B');
