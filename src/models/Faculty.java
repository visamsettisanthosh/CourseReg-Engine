package models;

/**
 * Faculty entity model demonstrating OOP encapsulation.
 */
public class Faculty {
    private int facultyId;
    private String name;
    private String email;
    private String department;

    // Default constructor
    public Faculty() {}

    // Overloaded parameterized constructor
    public Faculty(int facultyId, String name, String email, String department) {
        this.facultyId = facultyId;
        this.name = name;
        this.email = email;
        this.department = department;
    }

    public int getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(int facultyId) {
        this.facultyId = facultyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s, %s)", facultyId, name, department, email);
    }
}
