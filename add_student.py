"""
CourseReg Engine - Add New Student Utility
Provides standalone and programmatic capabilities to register new students
into the course registration database, maintaining exact schema formatting
and auto-calculating academic risk indicators.
"""

import os
import sys
import math
import sqlite3
import argparse

DB_PATH = "registration.db"

def get_connection():
    if not os.path.exists(DB_PATH):
        alt = os.path.join(os.path.dirname(__file__), "registration.db")
        if os.path.exists(alt):
            return sqlite3.connect(alt)
    return sqlite3.connect(DB_PATH)

def calculate_risk(gpa: float, attendance_pct: float, failed_courses: int):
    """
    Computes calibrated statistical risk score and classification.
    z = 6.20 - (1.50 * GPA) - (0.055 * Attendance%) + (0.65 * FailedCourses)
    P = 1 / (1 + e^(-z))
    """
    z = 6.20 - (1.50 * gpa) - (0.055 * attendance_pct) + (0.65 * failed_courses)
    z = max(-15.0, min(15.0, z))
    prob = 1.0 / (1.0 + math.exp(-z))
    prob = round(prob, 3)

    if prob >= 0.55 or gpa < 2.20 or attendance_pct < 65.0 or failed_courses >= 3:
        flag = "HIGH"
    elif prob >= 0.25 or gpa < 2.90 or attendance_pct < 78.0 or failed_courses >= 1:
        flag = "MEDIUM"
    else:
        flag = "LOW"

    return prob, flag

def get_next_student_id_and_roll(conn):
    c = conn.cursor()
    c.execute("SELECT COALESCE(MAX(student_id), 0) + 1 FROM student")
    next_id = c.fetchone()[0]

    # Look for roll number pattern CS2023xxx
    c.execute("SELECT roll_number FROM student ORDER BY student_id DESC LIMIT 1")
    last_roll_row = c.fetchone()
    if last_roll_row and last_roll_row[0].startswith("CS2023"):
        try:
            num_part = int(last_roll_row[0][6:]) + 1
            next_roll = f"CS2023{num_part:03d}"
        except ValueError:
            next_roll = f"CS2023{next_id:03d}"
    else:
        next_roll = f"CS2023{next_id:03d}"

    return next_id, next_roll

def add_new_student(name: str, email: str = None, gpa: float = 3.00,
                    attendance_pct: float = 85.0, failed_courses: int = 0,
                    roll_number: str = None):
    """
    Adds a new student to the database.
    
    Parameters:
        name (str): Full student name (e.g. 'Rohan Sharma')
        email (str): Student email (e.g. 'rohan@univ.edu')
        gpa (float): Grade point average (0.00 - 4.00)
        attendance_pct (float): Overall attendance percentage (0.0 - 100.0)
        failed_courses (int): Number of failed courses (0 - 10)
        roll_number (str): Optional custom roll number (e.g. 'CS2023017')
    
    Returns:
        dict: Newly inserted student record
    """
    conn = get_connection()
    c = conn.cursor()

    next_id, auto_roll = get_next_student_id_and_roll(conn)
    roll = roll_number.strip() if roll_number else auto_roll

    if not email or not email.strip():
        # Generate standard email: firstname@univ.edu
        first_word = name.strip().split()[0].lower()
        email = f"{first_word}@univ.edu"

    # Validate ranges
    gpa = max(0.0, min(4.0, float(gpa)))
    attendance_pct = max(0.0, min(100.0, float(attendance_pct)))
    failed_courses = max(0, int(failed_courses))

    risk_score, risk_flag = calculate_risk(gpa, attendance_pct, failed_courses)

    try:
        c.execute("""
            INSERT INTO student (
                student_id, roll_number, name, email, gpa,
                attendance_pct, failed_courses, risk_score, risk_flag
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (next_id, roll, name.strip(), email.strip(), gpa, attendance_pct, failed_courses, risk_score, risk_flag))
        conn.commit()
    except sqlite3.IntegrityError as e:
        conn.close()
        raise ValueError(f"Database error inserting student: {e}")

    conn.close()

    record = {
        "student_id": next_id,
        "roll_number": roll,
        "name": name.strip(),
        "email": email.strip(),
        "gpa": gpa,
        "attendance_pct": attendance_pct,
        "failed_courses": failed_courses,
        "risk_score": risk_score,
        "risk_flag": risk_flag
    }

    return record

def print_student_list():
    conn = get_connection()
    c = conn.cursor()
    c.execute("""
        SELECT student_id, roll_number, name, email, gpa, attendance_pct,
               COALESCE(failed_courses, 0), risk_score, risk_flag
        FROM student ORDER BY student_id ASC
    """)
    rows = c.fetchall()
    print("\n" + "=" * 95)
    print(f"{'ID':<4} {'Roll No':<12} {'Student Name':<20} {'GPA':<6} {'Attend%':<8} {'Fails':<6} {'Risk':<8} {'Score':<8} {'Email'}")
    print("-" * 95)
    for r in rows:
        print(f"{r[0]:<4} {r[1]:<12} {r[2]:<20} {r[4]:<6.2f} {r[5]:<7.1f}% {r[6]:<6} {r[8]:<8} {r[7]:<8.3f} {r[3]}")
    print("=" * 95)
    conn.close()

def interactive_mode():
    print("==============================================================================")
    print("  🎓 COURSEREG ENGINE: ADD NEW STUDENT (INTERACTIVE MODE)")
    print("==============================================================================")
    
    conn = get_connection()
    next_id, auto_roll = get_next_student_id_and_roll(conn)
    conn.close()

    print(f"[*] Next sequential Student ID : {next_id}")
    print(f"[*] Default Roll Number        : {auto_roll}")
    print("-" * 78)

    name = input("Enter Student Full Name (e.g. Ananya Sen)      : ").strip()
    if not name:
        print("[-] Student name is required.")
        return

    roll_input = input(f"Enter Roll Number [Press Enter for {auto_roll}]   : ").strip()
    roll = roll_input if roll_input else auto_roll

    default_email = f"{name.split()[0].lower()}@univ.edu"
    email_input = input(f"Enter Email [Press Enter for {default_email}]     : ").strip()
    email = email_input if email_input else default_email

    gpa_input = input("Enter GPA (0.00 - 4.00) [Default 3.50]         : ").strip()
    gpa = float(gpa_input) if gpa_input else 3.50

    att_input = input("Enter Attendance % (0 - 100) [Default 90.0]    : ").strip()
    att = float(att_input) if att_input else 90.0

    fails_input = input("Enter Failed Courses Count [Default 0]         : ").strip()
    fails = int(fails_input) if fails_input else 0

    try:
        rec = add_new_student(name, email, gpa, att, fails, roll)
        print("\n✅ STUDENT SUCCESSFULLY REGISTERED!")
        print(f"   • Student ID    : #{rec['student_id']}")
        print(f"   • Roll Number   : {rec['roll_number']}")
        print(f"   • Full Name     : {rec['name']}")
        print(f"   • Email Address : {rec['email']}")
        print(f"   • GPA           : {rec['gpa']:.2f}")
        print(f"   • Attendance    : {rec['attendance_pct']:.1f}%")
        print(f"   • Failed Courses: {rec['failed_courses']}")
        print(f"   • Risk Score    : {rec['risk_score']:.3f} [{rec['risk_flag']}]")
        print_student_list()
    except Exception as e:
        print(f"[-] Failed to add student: {e}")

def main():
    parser = argparse.ArgumentParser(description="Add New Student to CourseReg Database")
    parser.add_argument("--name", type=str, help="Student Full Name (e.g. 'Aarav Sharma')")
    parser.add_argument("--email", type=str, help="Student Email (e.g. 'aarav@univ.edu')")
    parser.add_argument("--roll", type=str, help="Custom Roll Number (default: next auto-sequence CS2023xxx)")
    parser.add_argument("--gpa", type=float, default=3.00, help="GPA (0.00 - 4.00, default 3.00)")
    parser.add_argument("--attendance", type=float, default=85.0, help="Attendance percentage (0.0 - 100.0, default 85.0)")
    parser.add_argument("--fails", type=int, default=0, help="Failed courses count (default 0)")
    parser.add_argument("--list", action="store_true", help="List all registered students")

    args = parser.parse_args()

    if args.list:
        print_student_list()
        return

    if args.name:
        # Command line creation
        try:
            rec = add_new_student(args.name, args.email, args.gpa, args.attendance, args.fails, args.roll)
            print(f"[+] Successfully added Student #{rec['student_id']}: {rec['name']} ({rec['roll_number']})")
            print(f"    GPA={rec['gpa']:.2f}, Attend={rec['attendance_pct']:.1f}%, Fails={rec['failed_courses']}, Risk={rec['risk_flag']} ({rec['risk_score']:.3f})")
            print_student_list()
        except Exception as e:
            print(f"[-] Error: {e}", file=sys.stderr)
            sys.exit(1)
    else:
        # Interactive mode
        interactive_mode()

if __name__ == "__main__":
    main()
