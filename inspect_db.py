import sqlite3

conn = sqlite3.connect('registration.db')
cursor = conn.cursor()

cursor.execute("SELECT COUNT(*) FROM student;")
print(f"Total student: {cursor.fetchone()[0]}")

cursor.execute("SELECT COUNT(*) FROM course;")
print(f"Total course: {cursor.fetchone()[0]}")

cursor.execute("SELECT COUNT(*) FROM enrollment;")
print(f"Total enrollment: {cursor.fetchone()[0]}")

cursor.execute("SELECT status, count(*) FROM enrollment GROUP BY status;")
print("Enrollments by status:", cursor.fetchall())

cursor.execute("SELECT COUNT(*) FROM timetable;")
print(f"Total timetable slots: {cursor.fetchone()[0]}")

cursor.execute("SELECT * FROM student WHERE roll_number LIKE '%266Q5A4508%' OR student_id=25 OR student_id=1;")
for row in cursor.fetchall():
    print("Student:", row)

cursor.execute("""
SELECT e.enrollment_id, e.student_id, e.course_id, c.course_code, c.course_name, e.semester, e.status, e.grade
FROM enrollment e
JOIN course c ON e.course_id = c.course_id
WHERE e.student_id = 1
""")
print("\nEnrollments for student 1:")
for r in cursor.fetchall():
    print(r)

cursor.execute("""
SELECT t.slot_id, c.course_code, c.course_name, t.day_of_week, t.start_time, t.end_time, t.room_number, f.name
FROM timetable t
JOIN course c ON t.course_id = c.course_id
JOIN faculty f ON t.faculty_id = f.faculty_id
""")
print("\nTimetable slots sample:")
for r in cursor.fetchall()[:8]:
    print(r)
