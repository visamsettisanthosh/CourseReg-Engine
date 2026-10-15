"""
Course Registration & Timetable System - Layer 3: Academic Risk Scoring Module
Computes an academic risk indicator score from student historical data:
  - Cumulative GPA (0.00 - 4.00)
  - Attendance Percentage (0.0% - 100.0%)
  - Number of Failed / Repeat Courses

Methodology Note for Academic Viva:
  - Default Model: Calibrated Statistical Logistic Model (Multivariate Sigmoid)
  - Optional Model: scikit-learn LogisticRegression (via --sklearn flag)
  - The calculated score represents an 'Academic Risk Indicator' (probability between 0.0 and 1.0)
    used to flag students who may benefit from academic advising or tutoring.
"""

import sys
import os
import math
import sqlite3
from datetime import datetime

def calculate_academic_risk(gpa: float, attendance_pct: float, failed_courses: int) -> tuple[float, str, list[str]]:
    """
    Multivariate Logistic Formulation:
    P(Risk Indicator = 1 | x) = 1 / (1 + exp(-z))
    where:
      z = beta_0 + (beta_1 * GPA) + (beta_2 * Attendance) + (beta_3 * FailedCourses)
    
    Calibrated Weights:
      beta_0 (Base Intercept)       = +6.20
      beta_1 (GPA weight)           = -1.50 (Lower GPA increases risk)
      beta_2 (Attendance weight)    = -0.055 (Lower attendance increases risk)
      beta_3 (Failed courses wt)    = +0.65 (Each failed course significantly elevates risk)
    """
    z = 6.20 - (1.50 * gpa) - (0.055 * attendance_pct) + (0.65 * failed_courses)
    # Clamp z to avoid numerical overflow in exp
    z = max(-15.0, min(15.0, z))
    prob = 1.0 / (1.0 + math.exp(-z))
    prob = round(prob, 3)

    # Identify specific contributing factors
    factors = []
    if attendance_pct < 65.0:
        factors.append(f"Critical attendance deficit ({attendance_pct:.1f}% < 65%)")
    elif attendance_pct < 75.0:
        factors.append(f"Below average attendance ({attendance_pct:.1f}%)")

    if gpa < 2.20:
        factors.append(f"Critical GPA deficit ({gpa:.2f} < 2.20)")
    elif gpa < 2.85:
        factors.append(f"Sub-optimal GPA ({gpa:.2f} < 2.85)")

    if failed_courses >= 2:
        factors.append(f"Multiple failed courses ({failed_courses} subjects)")
    elif failed_courses == 1:
        factors.append("1 prior failed course on record")

    if not factors:
        factors.append("Consistent attendance & strong GPA")

    # Determine risk level
    if prob >= 0.55 or gpa < 2.20 or attendance_pct < 65.0 or failed_courses >= 3:
        flag = "HIGH"
    elif prob >= 0.25 or gpa < 2.90 or attendance_pct < 78.0 or failed_courses >= 1:
        flag = "MEDIUM"
    else:
        flag = "LOW"

    return prob, flag, factors


def run_sklearn_pipeline(students_data: list[dict]) -> list[tuple[float, str, list[str]]]:
    """
    Optional scikit-learn classifier pipeline for systems where scipy/sklearn
    binary extensions are allowed by OS policies.
    """
    try:
        from sklearn.linear_model import LogisticRegression
        import numpy as np

        # Training set with features: [GPA, Attendance, FailedCourses]
        X_train = np.array([
            [3.9, 98.0, 0], [3.8, 95.0, 0], [3.5, 90.0, 0], [3.2, 85.0, 0], [3.0, 80.0, 0],
            [2.8, 76.0, 1], [2.6, 72.0, 1], [2.4, 68.0, 1], [2.2, 63.0, 2], [1.9, 55.0, 2],
            [1.7, 50.0, 3], [1.5, 45.0, 3], [2.9, 82.0, 0], [3.4, 88.0, 0], [2.1, 60.0, 2]
        ])
        y_train = np.array([0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1])

        clf = LogisticRegression(solver='lbfgs', random_state=42)
        clf.fit(X_train, y_train)

        results = []
        for s in students_data:
            x_val = np.array([[s['gpa'], s['attendance_pct'], s['failed_courses']]])
            prob = clf.predict_proba(x_val)[0][1]
            prob = round(float(prob), 3)
            _, flag, factors = calculate_academic_risk(s['gpa'], s['attendance_pct'], s['failed_courses'])
            results.append((prob, flag, factors))
        return results
    except Exception as ex:
        print(f"[!] Note: scikit-learn pipeline unavailable ({ex}). Using calibrated statistical model.")
        return [calculate_academic_risk(s['gpa'], s['attendance_pct'], s['failed_courses']) for s in students_data]


def run_risk_assessment(db_path: str = "registration.db", force_sklearn: bool = False):
    if not os.path.exists(db_path):
        print(f"[-] Database file '{db_path}' not found! Creating from schema.sql...")
        init_db_from_schema(db_path)

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    method_name = "Scikit-Learn Logistic Regression" if force_sklearn else "Academic Risk Scoring (Calibrated Statistical Model)"
    print("=" * 78)
    print("LAYER 3: ACADEMIC RISK ASSESSMENT MODULE")
    print(f"Database:    {os.path.abspath(db_path)}")
    print(f"Methodology: {method_name}")
    print("=" * 78)

    # Ensure failed_courses column exists if older schema was loaded
    try:
        cursor.execute("SELECT failed_courses FROM student LIMIT 1")
    except sqlite3.OperationalError:
        cursor.execute("ALTER TABLE student ADD COLUMN failed_courses INT DEFAULT 0")
        conn.commit()

    cursor.execute("""
        SELECT student_id, roll_number, name, gpa, attendance_pct, COALESCE(failed_courses, 0)
        FROM student
        ORDER BY student_id ASC
    """)
    rows = cursor.fetchall()
    students = [
        {
            "student_id": r[0],
            "roll_number": r[1],
            "name": r[2],
            "gpa": float(r[3]),
            "attendance_pct": float(r[4]),
            "failed_courses": int(r[5])
        }
        for r in rows
    ]

    print(f"[*] Retrieved {len(students)} students for academic risk evaluation.\n")
    print(f"{'ID':<4} {'Roll No':<11} {'Name':<17} {'GPA':<5} {'Attend':<7} {'Fails':<6} {'Score':<7} {'Risk Flag':<8} {'Contributing Factors'}")
    print("-" * 78)

    if force_sklearn:
        predictions = run_sklearn_pipeline(students)
    else:
        predictions = [calculate_academic_risk(s['gpa'], s['attendance_pct'], s['failed_courses']) for s in students]

    counts = {"LOW": 0, "MEDIUM": 0, "HIGH": 0}

    for s, (prob, flag, factors) in zip(students, predictions):
        counts[flag] += 1
        factors_summary = "; ".join(factors)
        if len(factors_summary) > 36:
            factors_summary = factors_summary[:33] + "..."

        color = "\033[92m" if flag == "LOW" else ("\033[93m" if flag == "MEDIUM" else "\033[91m")
        reset = "\033[0m"

        print(f"{s['student_id']:<4} {s['roll_number']:<11} {s['name']:<17} {s['gpa']:<5.2f} {s['attendance_pct']:<6.1f}% {s['failed_courses']:<6} {prob:<7.3f} {color}{flag:<8}{reset} {factors_summary}")

        cursor.execute("""
            UPDATE student
            SET risk_score = ?, risk_flag = ?, updated_at = CURRENT_TIMESTAMP
            WHERE student_id = ?
        """, (prob, flag, s['student_id']))

    conn.commit()
    conn.close()

    print("-" * 78)
    print(f"[+] Risk scoring completed successfully and persisted to database.")
    print(f"    - LOW Risk (Green):     {counts['LOW']} students (Normal monitoring)")
    print(f"    - MEDIUM Risk (Yellow): {counts['MEDIUM']} students (Weekly monitoring recommended)")
    print(f"    - HIGH Risk (Red):      {counts['HIGH']} students (Academic counseling recommended)")
    print("=" * 78)


def init_db_from_schema(db_path: str = "registration.db", schema_path: str = "schema.sql"):
    if not os.path.exists(schema_path):
        schema_path = os.path.join(os.path.dirname(__file__), "schema.sql")

    conn = sqlite3.connect(db_path)
    with open(schema_path, "r", encoding="utf-8") as f:
        sql_script = f.read()

    cursor = conn.cursor()
    cursor.executescript(sql_script)
    conn.commit()
    conn.close()
    print(f"[+] Initialized database '{db_path}' from '{schema_path}'.")


if __name__ == "__main__":
    db_file = "registration.db"
    use_sklearn = False
    for arg in sys.argv[1:]:
        if arg == "--sklearn":
            use_sklearn = True
        elif not arg.startswith("-"):
            db_file = arg

    if not os.path.isabs(db_file):
        script_dir = os.path.dirname(os.path.abspath(__file__))
        candidate = os.path.join(script_dir, db_file)
        if os.path.exists(candidate) or not os.path.exists(db_file):
            db_file = candidate

    run_risk_assessment(db_file, force_sklearn=use_sklearn)
