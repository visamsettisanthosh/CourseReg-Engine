"""
Script to update existing student names in registration.db to realistic Indian names
while strictly preserving all student_ids, roll numbers, GPAs, attendance, failed courses,
risk scores, and enrollment histories.
"""

import sqlite3
import os

DB_PATH = "registration.db"

INDIAN_STUDENTS = [
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
    (16, 'CS2023016', 'Priya Sharma',     'priya@univ.edu',     3.50, 89.0, 0, 0.013, 'LOW'),
]

def update_students():
    if not os.path.exists(DB_PATH):
        print(f"[-] Database {DB_PATH} not found.")
        return

    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()

    print("[*] Updating existing student records to Indian names...")
    for s in INDIAN_STUDENTS:
        student_id, roll, name, email, gpa, att, fails, score, flag = s
        c.execute("""
            UPDATE student
            SET name = ?, email = ?
            WHERE student_id = ?
        """, (name, email, student_id))
        print(f"  [+] ID {student_id:2d} ({roll}): Updated to '{name}' <{email}>")

    conn.commit()
    conn.close()
    print("[+] All 16 student records updated successfully in registration.db.")

if __name__ == "__main__":
    update_students()
