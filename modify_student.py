"""
CLI Utility to inspect and modify student records, GPAs, attendance,
and course enrollment histories in the registration database.

Usage examples:
    python modify_student.py --list
    python modify_student.py --add-student --name "Vikram Singh" --email "vikram@univ.edu" --gpa 3.65 --attendance 91.0 --fails 0
    python modify_student.py --id 3 --gpa 3.65 --attendance 92.0
    python modify_student.py --id 3 --name "Chirag Gupta" --email "chirag@univ.edu"
    python modify_student.py --complete-course --student 3 --course 103 --grade A --sem "Fall 2025"
    python modify_student.py --reset-all
"""

import sys
import os
import argparse
import sqlite3

DB_PATH = "registration.db"

def get_connection():
    if not os.path.exists(DB_PATH):
        alt = os.path.join(os.path.dirname(__file__), "registration.db")
        if os.path.exists(alt):
            return sqlite3.connect(alt)
    return sqlite3.connect(DB_PATH)

def list_students():
    conn = get_connection()
    c = conn.cursor()
    c.execute("""
        SELECT student_id, roll_number, name, email, gpa, attendance_pct, COALESCE(failed_courses, 0), risk_flag, risk_score
        FROM student ORDER BY student_id ASC
    """)
    rows = c.fetchall()
    print("=" * 90)
    print(f"{'ID':<4} {'Roll No':<12} {'Name':<18} {'GPA':<6} {'Attend%':<8} {'Fails':<6} {'Risk':<8} {'Score':<8} {'Email'}")
    print("-" * 90)
    for r in rows:
        flag = r[7] if r[7] else "LOW"
        score = float(r[8]) if r[8] is not None else 0.0
        print(f"{r[0]:<4} {r[1]:<12} {r[2]:<18} {r[4]:<6.2f} {r[5]:<7.1f}% {r[6]:<6} {flag:<8} {score:<8.3f} {r[3]}")
    print("=" * 90)
    conn.close()

def update_student(student_id: int, name=None, email=None, gpa=None, attendance=None, fails=None):
    conn = get_connection()
    c = conn.cursor()
    c.execute("SELECT student_id, name, email, gpa, attendance_pct, COALESCE(failed_courses, 0) FROM student WHERE student_id = ?", (student_id,))
    row = c.fetchone()
    if not row:
        print(f"[-] Student ID {student_id} not found.")
        conn.close()
        return

    new_name = name if name is not None else row[1]
    new_email = email if email is not None else row[2]
    new_gpa = float(gpa) if gpa is not None else float(row[3])
    new_att = float(attendance) if attendance is not None else float(row[4])
    new_fails = int(fails) if fails is not None else int(row[5])

    c.execute("""
        UPDATE student
        SET name = ?, email = ?, gpa = ?, attendance_pct = ?, failed_courses = ?, updated_at = datetime('now')
        WHERE student_id = ?
    """, (new_name, new_email, new_gpa, new_att, new_fails, student_id))
    conn.commit()
    conn.close()
    print(f"[+] Successfully updated Student #{student_id} ({new_name}): GPA={new_gpa:.2f}, Attendance={new_att:.1f}%, Fails={new_fails}")

    # Automatically recompute risk score
    try:
        from risk_score import run_risk_assessment
        run_risk_assessment(DB_PATH)
    except Exception as e:
        print(f"[!] Run 'python risk_score.py' to refresh risk scores: {e}")

def add_completed_course(student_id: int, course_id: int, grade: str = "A", semester: str = "Fall 2025"):
    conn = get_connection()
    c = conn.cursor()
    c.execute("SELECT COALESCE(MAX(enrollment_id), 0) + 1 FROM enrollment")
    next_id = c.fetchone()[0]

    try:
        c.execute("""
            INSERT INTO enrollment (enrollment_id, student_id, course_id, semester, status, grade)
            VALUES (?, ?, ?, ?, 'COMPLETED', ?)
        """, (next_id, student_id, course_id, semester, grade.upper()))
        conn.commit()
        print(f"[+] Added completed course {course_id} for Student {student_id} (Grade {grade.upper()}, {semester}).")
    except sqlite3.IntegrityError as e:
        print(f"[-] Could not add completed course: {e}")
    finally:
        conn.close()

def reset_all_from_schema():
    schema_path = "schema.sql"
    if not os.path.exists(schema_path):
        schema_path = os.path.join(os.path.dirname(__file__), "schema.sql")

    conn = get_connection()
    with open(schema_path, "r", encoding="utf-8") as f:
        sql = f.read()
    conn.executescript(sql)
    conn.commit()
    conn.close()
    print("[+] Database reset and reseeded from schema.sql.")
    try:
        from risk_score import run_risk_assessment
        run_risk_assessment(DB_PATH)
    except Exception:
        pass

def main():
    parser = argparse.ArgumentParser(description="Modify Student Records Utility")
    parser.add_argument("--list", action="store_true", help="List all students")
    parser.add_argument("--id", type=int, help="Student ID to modify")
    parser.add_argument("--name", type=str, help="New student name")
    parser.add_argument("--email", type=str, help="New student email")
    parser.add_argument("--gpa", type=float, help="New GPA (0.00 - 4.00)")
    parser.add_argument("--attendance", type=float, help="New attendance percentage (0.0 - 100.0)")
    parser.add_argument("--fails", type=int, help="Failed courses count (0 - 10)")
    parser.add_argument("--complete-course", action="store_true", help="Grant a completed prerequisite course")
    parser.add_argument("--student", type=int, help="Student ID for course completion")
    parser.add_argument("--course", type=int, help="Course ID completed")
    parser.add_argument("--grade", type=str, default="A", help="Grade received (e.g. A, B, C)")
    parser.add_argument("--sem", type=str, default="Fall 2025", help="Semester taken")
    parser.add_argument("--add-student", action="store_true", help="Add a new student using --name, --email, --gpa, --attendance, --fails, --roll")
    parser.add_argument("--roll", type=str, help="Custom roll number for new student")
    parser.add_argument("--reset-all", action="store_true", help="Reset all tables and seed data from schema.sql")

    args = parser.parse_args()

    if args.reset_all:
        reset_all_from_schema()
    elif args.add_student:
        if not args.name:
            print("[-] Please specify --name for the new student.")
        else:
            from add_student import add_new_student
            gpa = args.gpa if args.gpa is not None else 3.00
            att = args.attendance if args.attendance is not None else 85.0
            fails = args.fails if args.fails is not None else 0
            rec = add_new_student(args.name, args.email, gpa, att, fails, args.roll)
            print(f"[+] Successfully registered new student #{rec['student_id']}: {rec['name']} ({rec['roll_number']})")
            print(f"    GPA={rec['gpa']:.2f}, Attend={rec['attendance_pct']:.1f}%, Fails={rec['failed_courses']}, Risk={rec['risk_flag']} ({rec['risk_score']:.3f})")
    elif args.list or len(sys.argv) == 1:
        list_students()
    elif args.complete_course:
        if not args.student or not args.course:
            print("[-] Please specify --student <id> and --course <id>")
        else:
            add_completed_course(args.student, args.course, args.grade, args.sem)
    elif args.id:
        update_student(args.id, args.name, args.email, args.gpa, args.attendance, args.fails)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
