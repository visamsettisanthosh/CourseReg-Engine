# CampusNexus: College Course Registration & Timetable System
### Multilayer Enterprise System: DBMS + DMGT • OOPJ + ADSA • Python ML Dropout Risk Engine

A full-stack, enterprise-grade course registration platform built to resolve course prerequisite dependencies, detect timetable schedule conflicts in $O(\log n)$ time, and predict student academic dropout risk.

---

## 🏛️ System Architecture

The system is architected across three synchronized layers:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Layer 3: Python Dropout-Risk Intelligence Module (risk_score.py)           │
│  - Logistic Regression (scikit-learn & Calibrated Math Formulation)         │
│  - Features: Cumulative GPA, Attendance % → Risk Score [0.0 - 1.0]          │
│  - Writes risk_score & risk_flag ('LOW', 'MEDIUM', 'HIGH') to DB           │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Reads history / Updates risk_flag
┌──────────────────────────────────────▼──────────────────────────────────────┐
│  Layer 2: Application Logic (Java 21 SE - OOPJ + ADSA)                      │
│  - Models: Student, Course, Faculty, Enrollment, TimetableSlot              │
│  - PrerequisiteDAG: Adjacency list DAG, 3-color DFS cycle check, TopoSort   │
│  - IntervalTree: Augmented BST for O(log n) timetable clash detection       │
│  - Registrar: Encapsulation, constructor/method overloading, JDBC transactions│
│  - RegistrarHttpServer: Embedded REST API & Static UI Web Server (Port 8080)│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ PreparedStatements & Schema DDL
┌──────────────────────────────────────▼──────────────────────────────────────┐
│  Layer 1: Database Management (DBMS + DMGT - schema.sql)                    │
│  - PostgreSQL / MySQL / SQLite relational schema with integrity constraints │
│  - Mathematical Relational Algebra for Clash Detection & Prereq Validation  │
│  - Seed Data: 16 Students, 11 Courses, Faculty, Slots, Transcripts          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📐 Layer 1: Database & Discrete Mathematics (DBMS + DMGT)

### 1. Entity-Relationship (ER) Model
- **Student**: `(student_id [PK], roll_number [UQ], name, email [UQ], gpa, attendance_pct, risk_score, risk_flag)`
- **Course**: `(course_id [PK], course_code [UQ], course_name, credits, department)`
- **Faculty**: `(faculty_id [PK], name, email [UQ], department)`
- **Prerequisite**: `(course_id [FK], prerequisite_id [FK])` with PK `(course_id, prerequisite_id)` and constraint `course_id <> prerequisite_id`
- **Timetable**: `(slot_id [PK], course_id [FK], day_of_week, start_time, end_time, room_number, faculty_id [FK])`
- **Enrollment**: `(enrollment_id [PK], student_id [FK], course_id [FK], semester, status, grade)` with unique key `(student_id, course_id, semester)`

### 2. Relational Algebra Formulation

#### A. Timetable Clash Detection
Two intervals $[s_1, e_1)$ and $[s_2, e_2)$ overlap if and only if:
$$\text{Overlap}(I_1, I_2) \iff s_1 < e_2 \land s_2 < e_1$$
Given student $S$ attempting candidate course $C_{cand}$:
$$\text{ActiveSlots}_S \leftarrow \pi_{\text{slot\_id, day\_of\_week, start\_time, end\_time}}\left(\sigma_{\text{student\_id}=S \land \text{status}='ENROLLED'}(\text{Enrollment}) \bowtie \text{Timetable}\right)$$
$$\text{CandidateSlots}_C \leftarrow \pi_{\text{slot\_id, day\_of\_week, start\_time, end\_time}}\left(\sigma_{\text{course\_id}=C_{cand}}(\text{Timetable})\right)$$
$$\text{Clashes} \leftarrow \sigma_{\text{ActiveSlots.day} = \text{CandidateSlots.day} \;\land\; \text{ActiveSlots.start} < \text{CandidateSlots.end} \;\land\; \text{CandidateSlots.start} < \text{ActiveSlots.end}}\left(\text{ActiveSlots}_S \times \text{CandidateSlots}_C\right)$$

#### B. Prerequisite Validation
Let $P(C) = \{ p \mid (C, p) \in \text{Prerequisite} \}$ be the direct prerequisites of $C$.  
Let $\text{Comp}(S) = \{ c \mid (S, c, \text{'COMPLETED'}) \in \text{Enrollment} \}$ be courses passed by student $S$.
$$\text{Missing}(S, C) = P(C) - \text{Comp}(S)$$
$$\text{Valid Enrollment} \iff \text{Missing}(S, C) = \emptyset$$

### 3. DMGT Prerequisite DAG Properties
The prerequisite relationship forms a **strict partial order** relation $R$ on courses:
1. **Irreflexive**: $\forall c \in V, (c, c) \notin R$ (enforced by SQL CHECK constraint and Java graph checks).
2. **Asymmetric**: $(c_1, c_2) \in R \implies (c_2, c_1) \notin R$.
3. **Transitive**: $(c_1, c_2) \in R \land (c_2, c_3) \in R \implies (c_1, c_3) \in R$.
By the **Topological Ordering Theorem**, a directed graph $G = (V, E)$ has a valid topological sorting if and only if $G$ is acyclic (contains no directed cycles).

---

## 💻 Layer 2: Application Logic (OOPJ + ADSA)

### OOP Design Principles Applied
- **Encapsulation**: Private state fields across `Student`, `Course`, `Faculty`, `Enrollment`, `TimetableSlot`, and `Registrar`, accessible exclusively through validated getters, setters, and operational methods.
- **Constructor Overloading**:
  - `Registrar()` (Default constructor pointing to local DB and current term).
  - `Registrar(DatabaseManager db)` (Custom database).
  - `Registrar(DatabaseManager db, String term)` (Configurable semester).
- **Method Overloading**:
  - `enrollStudent(int studentId, int courseId)`
  - `enrollStudent(int studentId, int courseId, String semester)`
  - `validateEligibility(int studentId, int courseId)`
  - `validateEligibility(Student s, Course c)`
- **Exception Hierarchy**:
  - `RegistrationException` (Base checked exception)
    - `PrerequisiteNotMetException` (Carries list of missing prerequisites)
    - `TimetableClashException` (Carries candidate and conflicting interval slots)

### ADSA Algorithms Applied
1. **Prerequisite DAG Topological Traversal (`adsa.PrerequisiteDAG`)**:
   - Implemented using Adjacency List `Map<Integer, List<Integer>>`.
   - **3-Color DFS Cycle Detection**: State 0 (WHITE / unvisited), State 1 (GRAY / visiting in recursion stack), State 2 (BLACK / completed). Encountering a GRAY node confirms a back-edge (cycle).
   - **Kahn's In-Degree Reduction Algorithm**: Computes linear study order in $O(|V| + |E|)$.
2. **Augmented Interval Tree Clash Detection (`adsa.IntervalTree`)**:
   - Binary Search Tree partitioned per weekday (Monday–Friday).
   - Each node maintains interval $[low, high]$ sorted by $low$, and augmented field $maxHigh = \max(high, left.maxHigh, right.maxHigh)$.
   - Searches for overlapping time slots in $O(\log N)$ average time.

---

## 🤖 Layer 3: Python Statistical Academic Risk Intelligence

- File: `risk_score.py`
- Evaluates student academic risk indicators using a multivariate logistic regression statistical model:
  $$z = 6.20 - (1.50 \times \text{GPA}) - (0.055 \times \text{Attendance}) + (0.65 \times \text{FailedCourses})$$
  $$P(\text{Risk} = 1 \mid x) = \sigma(z) = \frac{1}{1 + e^{-z}}$$
- Risk Classification & Interventions:
  - **HIGH RISK (Red)**: $P \ge 0.55 \lor \text{GPA} < 2.20 \lor \text{Attendance} < 65\% \lor \text{FailedCourses} \ge 3$ (Advisor Action: Mandatory Academic Counseling & Credit Load Review)
  - **MEDIUM RISK (Yellow)**: $P \ge 0.25 \lor \text{GPA} < 2.90 \lor \text{Attendance} < 78\% \lor \text{FailedCourses} \ge 1$ (Advisor Action: Bi-weekly Tutoring & Attendance Monitoring)
  - **LOW RISK (Green)**: Good academic standing
- Transparently labeled as a calibrated statistical risk indicator; writes `risk_score`, `risk_flag`, and `updated_at` directly back to the database.

---

## 🖥️ Modern Web UI & Viva Examination Features

Hosted on `http://localhost:8080/`:
1. **Interactive Viva Demonstration Scenarios Toolbar**:
   - 1-click execution for all 6 Viva defense scenarios:
     - **Scenario 1**: Standard Valid Enrollment (Aarav Sharma registers for CS401)
     - **Scenario 2**: Prerequisite Dependency Violation (Bhavin Patel registers for CS201 missing MATH201)
     - **Scenario 3**: Timetable Schedule Conflict (Diya Reddy registers for CS302 with clash on WED 14:00-15:30)
     - **Scenario 4**: High Academic Risk Student (Farhan Khan: GPA 1.80, Attendance 54%, 3 failed courses)
     - **Scenario 5**: Medium Academic Risk Student (Harini Sundaram: GPA 2.80, Attendance 74%, 1 failed course)
     - **Scenario 6**: Low Academic Risk Student (Aarav Sharma: GPA 3.85, Attendance 96%, 0 failed courses)
2. **Student Registration Portal**:
   - Student selector (choose among 16 students).
   - Live GPA, Attendance %, Failed Courses, and Semester Credit Counter (`X / 18 Max`).
   - Standardized status badges: `ELIGIBLE`, `COMPLETED`, `PREREQ REQUIRED`, `TIMETABLE CLASH`, `ENROLLED`, `REGISTRATION BLOCKED`.
   - Explicit 5-step registration validation audit checklist modal.
3. **Registrar & Advisor Dashboard**:
   - Real-time KPI counters: Total Students, Active Courses, High and Medium Risk tallies.
   - Background execution trigger for Python risk scoring pipeline (`/api/run-risk-pipeline`).
   - Advisor Alert Table with conservative phrasing and root-cause breakdown.
   - Master Enrollment Registry with instantaneous drop actions.
   - **Registrar Curriculum Management**: Forms to Add Courses, Link Prerequisites (cycle protected), and Schedule Timetable Slots.
4. **Prerequisite DAG Visualizer**:
   - Visual dependency hierarchy organized by topological tiers (Tier 0 through Tier 3).
   - Student eligibility color highlighting (Green: Completed, Blue: Enrolled, Cyan: Eligible, Amber: Missing Prereq).
   - Kahn's algorithm topological study sequence display.
   - Real-time cycle alert banner (`❌ Invalid prerequisite graph: Cycle detected`).
5. **System Architecture & Algorithm Viva Defense Tab**:
   - In-app technical explanations of the 3-layer architecture, Interval Tree overlap theorem, 3-color DFS cycle detection, and logistic formulation.

---

## 🚀 Quickstart & Execution Guide

### Prerequisites
- **Java 21+** (JDK)
- **Python 3.10+**

### Step 1: Navigate to Directory
```powershell
cd C:\Users\SANTHOSH\.gemini\antigravity\scratch\course_registration_system
```

### Step 2: Run Python Risk Assessment (Populates / Updates DB)
```powershell
python risk_score.py
```

### Step 3: Compile Java Application & Test Suite
```powershell
javac -cp ".;lib/*;src" -d bin src/models/*.java src/adsa/*.java src/exceptions/*.java src/db/*.java src/registrar/*.java src/server/*.java src/test/*.java src/Main.java
```

### Step 4: Run Automated Viva Test Suite (24 / 24 Tests)
```powershell
java -cp ".;lib/*;bin" test.VivaTestSuite
```

### Step 5: Launch Application Server
```powershell
java -cp "bin;lib/*" Main
```

### Step 6: Open Web Interface
Open your web browser and navigate to:
```
http://localhost:8080
```

---

## 🧪 Comprehensive Viva Defense Verification Matrix

| Scenario / Test Case | Subject | Target / Feature | Algorithm / Defense | Verified Result |
| :--- | :--- | :--- | :--- | :--- |
| **Scenario 1: Valid Enrollment** | Aarav Sharma (ID 1) | CS401 (Machine Learning) | 5-Step Validation Pipeline | **PASS** (Approved & Enrolled) |
| **Scenario 2: Prereq Violation** | Bhavin Patel (ID 2) | CS201 (Algorithms) | Topological DAG Check | **PASS** (Blocked: Missing MATH201) |
| **Scenario 3: Timetable Clash** | Diya Reddy (ID 4) | CS302 (Networks) | Augmented Interval Tree | **PASS** (Blocked: WED [14:00 - 15:30]) |
| **Scenario 4: High Academic Risk** | Farhan Khan (ID 6) | Risk Pipeline | Logistic Formulation ($z \ge 0.55$) | **PASS** (Flagged HIGH, score 0.923) |
| **Scenario 5: Medium Risk** | Harini Sundaram (ID 8) | Risk Pipeline | Logistic Formulation ($z \ge 0.25$) | **PASS** (Flagged MEDIUM, score 0.195) |
| **Scenario 6: Low Academic Risk** | Aarav Sharma (ID 1) | Risk Pipeline | Logistic Formulation ($z < 0.25$) | **PASS** (Flagged LOW, score 0.008) |
| **DAG Cycle Prevention** | Registrar Admin | CS101 $\to$ CS401 $\to$ CS101 | 3-Color DFS Back-Edge Check | **PASS** (Cycle Rejected & Graph Intact) |
| **Credit Limit Guard** | Student Registration | Candidate credits $> 18$ | Strict Semester Max Check | **PASS** (Blocked: Exceeds 18 credits) |
| **Interval Tree Performance** | Schedule Queries | $O(\log n)$ Augmented BST | Overlap Theorem: $s_1 < e_2 \land s_2 < e_1$ | **PASS** (Exact interval calculated) |

---

## ✏️ Modifying Student Records (Where & How)

You can modify student records (GPA, attendance percentage, names, emails, and completed courses) using any of the following 4 methods:

### Method 1: Directly via the Web UI (Fastest)
1. Navigate to **`http://localhost:8080`**.
2. Select any student from the top dropdown in the **Student Portal**.
3. Click the **`✏️ Edit Record`** button in the profile header card.
4. Update the **Name**, **Email**, **Cumulative GPA** (e.g., from `2.10` to `3.80`), and **Attendance %** (e.g., from `62%` to `95%`).
5. Click **"Save Changes"**.
   - The database (`registration.db`) is immediately updated via JDBC.
   - The student's ML dropout risk score and risk level (Green/Yellow/Red) recalculate automatically in real time.

### Method 2: Using the Python CLI Tool (`modify_student.py`)
Run commands directly in PowerShell / terminal:
```powershell
# 1. View all student records, GPAs, and risk scores:
python modify_student.py --list

# 2. Update GPA and attendance for a student (e.g. Student ID 3):
python modify_student.py --id 3 --gpa 3.75 --attendance 94.0

# 3. Update student name or email:
python modify_student.py --id 3 --name "Charlie B. Brown" --email "cbrown@univ.edu"

# 4. Grant a completed prerequisite course to a student:
python modify_student.py --complete-course --student 3 --course 103 --grade A --sem "Fall 2025"

# 5. Reset all student records back to default sample data:
python modify_student.py --reset-all
```

### Method 3: Using Direct SQL on `registration.db`
You can execute standard SQL statements using Python's SQLite shell or any SQLite client:
```powershell
python -c "import sqlite3; conn = sqlite3.connect('registration.db'); conn.execute('UPDATE student SET gpa = 3.90, attendance_pct = 95.0 WHERE student_id = 3'); conn.commit()"
python risk_score.py
```

### Method 4: Modifying Permanent Seed Data in `schema.sql`
To permanently change the initial students, open [`schema.sql`](file:///C:/Users/SANTHOSH/.gemini/antigravity/scratch/course_registration_system/schema.sql) and modify the `INSERT INTO student` statements starting at line 160. Then re-run `python modify_student.py --reset-all` or `python risk_score.py`.
