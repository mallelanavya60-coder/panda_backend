# 🗄️ Maplewood High School Database Documentation

## Database Overview

The `maplewood_school.sqlite` database contains realistic sample data for Maplewood High School's scheduling system. This SQLite database is pre-populated with core foundational data, providing you with the entities needed to implement the scheduling challenges.

## Database Statistics

| Entity | Count | Description |
|--------|-------|-------------|
| **Teachers** | 50 | Distributed across 9 specializations |
| **Students** | 400 | 100 students per grade level (9-12) |
| **Courses** | 57 | 20 core courses + 37 elective courses with semester ordering |
| **Classrooms** | 60 | Various types: classrooms, labs, studios, gym |
| **Semesters** | 9 | 6 historical + current + 2 future semesters with ordering |
| **Student Course History** | ~6,700 | Realistic academic records with pass/fail status |
| **Specializations** | 9 | Subject areas for teachers and courses |
| **Room Types** | 8 | Different classroom types and equipment |

## Key Tables and Relationships

### **Core Entities**

#### `teachers`
- 50 teachers with realistic names and email addresses
- Each teacher has one specialization (Mathematics, English, Science, etc.)
- All teachers limited to 4 hours of teaching per day

#### `students`
- 400 students evenly distributed across grades 9-12
- Each student has enrollment year and expected graduation year
- Realistic email addresses following school naming convention
- GPA can be calculated from their course history records

#### `courses`
- **20 core courses**: Required for all students (English, Math, Science, Social Studies)
- **37 elective courses**: Optional courses (Arts, Music, PE, Computer Science, Languages)
- **Semester ordering**: Each course assigned to Fall (1) or Spring (2) semester
- Proper prerequisite chains (e.g., MAT101 → MAT102 → MAT201)
- Logical academic progression enforced by database triggers
- Varying credit values and weekly hour requirements

#### `classrooms`
- 60 rooms across 3 floors with different types and equipment
- Room types include: classroom, science_lab, art_studio, gym, computer_lab, music_room
- Each room has a capacity limit of 10 students

#### `semesters`
- 9 configured semesters: 6 historical + current + 2 future
- **Order-in-year**: Fall=1, Spring=2 for logical academic progression
- Database constraints ensure proper semester naming and ordering
- Historical semesters enable realistic student academic progression tracking

#### `student_course_history`
- **~6,700 records**: Realistic academic history for prerequisite validation
- **Pass/fail tracking**: Only 'passed' and 'failed' statuses (85% pass rate)
- **Prerequisite enforcement**: Database triggers prevent enrollment without prerequisites
- **Duplicate prevention**: Students cannot retake courses they've already passed
- **GPA calculation**: Course credits and pass/fail status enable GPA computation

### **Configuration Tables**

#### `specializations`
- Links teachers to their subject areas
- Maps to required room types (e.g., Science → science_lab)
- 9 specializations covering all academic areas

#### `room_types`
- Defines different classroom types and their capabilities
- Used to match courses with appropriate rooms

### **Additional Tables**

The provided database contains the foundational entities needed to get started. You can create additional tables as needed for your implementation of the scheduling challenges. This allows you the flexibility to design the optimal data structure for your solution approach.

## Sample Data Examples

### **Semester Ordering System**

All courses are assigned to either Fall (semester_order=1) or Spring (semester_order=2) semesters:

```sql
-- Example: English sequence with semester ordering
ENG101 (Fall) → ENG102 (Spring) → ENG201 (Fall) → ENG202 (Spring)

-- Mathematics sequence respects prerequisites and semesters
MAT101 (Fall) → MAT102 (Spring) → MAT201 (Fall) → MAT202 (Spring)

-- Language progressions (Spanish, French, German)
SPAN101 (Fall) → SPAN201 (Spring) → SPAN301 (Fall)
```

### **Prerequisite Chains**
```sql
-- Mathematics sequence
MAT101 (Algebra I) → MAT102 (Geometry) → MAT201 (Algebra II) → MAT202 (Pre-Calculus) → MAT301 (Calculus)

-- English sequence
ENG101 → ENG102 → ENG201 → ENG202 → ENG301 → ENG302 → ENG401 → ENG402

-- Science progression
SCI101 (Biology) → SCI201 (Chemistry) → SCI301 (Physics)
```

### **Teacher Distribution**
- **Mathematics**: 8 teachers
- **English**: 8 teachers
- **Science**: 10 teachers
- **Social Studies**: 6 teachers
- **Arts**: 4 teachers
- **Music**: 4 teachers
- **Physical Education**: 4 teachers
- **Computer Science**: 3 teachers
- **Foreign Language**: 3 teachers

### **Classroom Types**
- **Standard Classrooms**: 30 rooms (Room-101 to Room-130)
- **Science Labs**: 10 labs (Lab-1 to Lab-10)
- **Art Studios**: 6 studios (Art-1 to Art-6)
- **Gyms**: 3 gymnasiums (Gym-1 to Gym-3)
- **Computer Labs**: 6 labs (CompLab-1 to CompLab-6)
- **Music Rooms**: 5 rooms (Music-1 to Music-5)

## Database Constraints

### **Semester Ordering Constraints**
- **Database triggers** enforce logical prerequisite progression
- Prerequisites must be from same or earlier semester within the same grade level
- Cross-grade prerequisites allowed (e.g., ENG102 Spring → ENG201 Fall next year)
- Prevents illogical scheduling (e.g., Fall course requiring Spring prerequisite in same year)

### **Built-In Constraints**
- Students limited to 10 per classroom (capacity constraint)
- Teachers limited to 4 hours per day (database constraint)
- Course prerequisites enforced via foreign keys and triggers
- Semester ordering enforced: Fall=1, Spring=2
- Student course history validation via database triggers

### **Database Triggers**
- **Prerequisite completion enforcement**: Students cannot enroll in courses without passing prerequisites
- **Duplicate course prevention**: Students cannot retake courses they've already passed
- **Semester ordering validation**: Course prerequisites must follow logical academic progression

### **Academic Rules**
- Core courses: 4-6 hours per week each
- Elective courses: 2-4 hours per week each
- Students need 30 total credits to graduate
- Maximum 5 courses per semester per student
- No classes during lunch hour (12-1 PM)

## Getting Started

### **Connecting to the Database**
```python
import sqlite3
conn = sqlite3.connect('maplewood_school.sqlite')
```

```javascript
// Node.js with sqlite3
const sqlite3 = require('sqlite3').verbose();
const db = new sqlite3.Database('maplewood_school.sqlite');
```

```java
// Java with JDBC
String url = "jdbc:sqlite:maplewood_school.sqlite";
Connection conn = DriverManager.getConnection(url);
```

### **Useful Sample Queries**

```sql
-- Get all Fall semester courses for 10th graders
SELECT code, name, semester_order, credits, hours_per_week
FROM courses
WHERE grade_level_min <= 10 AND grade_level_max >= 10
AND semester_order = 1;

-- Find all Science teachers
SELECT t.*, s.name as specialization
FROM teachers t
JOIN specializations s ON t.specialization_id = s.id
WHERE s.name = 'Science';

-- Check prerequisite chain with semester ordering
SELECT c1.code, c1.name, c1.semester_order,
       c2.code as prerequisite, c2.semester_order as prereq_order
FROM courses c1
LEFT JOIN courses c2 ON c1.prerequisite_id = c2.id
WHERE c1.code = 'MAT201';

-- See all semesters with ordering
SELECT name, year, order_in_year, is_active
FROM semesters
ORDER BY year, order_in_year;

-- Get student's completed courses and calculate GPA
SELECT s.first_name, s.last_name, s.grade_level,
       COUNT(sch.course_id) as courses_taken,
       COUNT(CASE WHEN sch.status = 'passed' THEN 1 END) as courses_passed,
       SUM(CASE WHEN sch.status = 'passed' THEN c.credits ELSE 0 END) as credits_earned,
       IFNULL(ROUND(
         SUM(CASE WHEN sch.status = 'passed' THEN c.credits ELSE 0 END) / SUM(c.credits) * 4.0, -- Assuming a 4.0 scale for GPA calculation is "(credits earned / total credits taken) * 4"
         2
       ), 0) as calculated_gpa
FROM students s
LEFT JOIN student_course_history sch ON s.id = sch.student_id
LEFT JOIN courses c ON sch.course_id = c.id
WHERE s.id = 100
GROUP BY s.id;

-- Check if student can enroll in a course (prerequisites met)
SELECT c.code, c.name,
       CASE
         WHEN c.prerequisite_id IS NULL THEN 'No prerequisite required'
         WHEN EXISTS (
           SELECT 1 FROM student_course_history sch
           WHERE sch.student_id = 100
           AND sch.course_id = c.prerequisite_id
           AND sch.status = 'passed'
         ) THEN 'Prerequisites met'
         ELSE 'Missing prerequisite: ' || prereq.code
       END as enrollment_status
FROM courses c
LEFT JOIN courses prereq ON c.prerequisite_id = prereq.id
WHERE c.code = 'MAT201';
```

## Your Tasks

1. **Challenge 1**: Create and populate tables for a complete master schedule that respects semester ordering
2. **Challenge 2**: Allow students to select courses with proper prerequisite and semester validation

The database provides all the foundational data with built-in semester ordering constraints. Focus your time on the algorithmic challenges of constraint satisfaction and user interface development!

---

*Generated for Maplewood High School Fullstack Coding Challenge*