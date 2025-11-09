# 🏫 Maplewood High School - Digital Transformation Challenge

## **The Story**

Welcome to **Maplewood High School**, a progressive 4-year institution serving 400 students with a dedicated team of 50 specialized teachers across 60 classrooms. Known for its balanced approach to education, Maplewood offers a comprehensive curriculum combining rigorous core academics with diverse elective opportunities.

### **The Crisis**

After 15 years of using Excel spreadsheets and manual processes, Maplewood's scheduling system has reached its breaking point. The current manual approach takes the administrative team **3 weeks** every semester to create schedules, often resulting in:

- 🚫 **Teacher conflicts** (same teacher scheduled in multiple rooms simultaneously)
- 📚 **Prerequisite violations** (students enrolled in MAT201 without completing MAT101)
- 🗓️ **Semester chaos** (courses scheduled without logical Fall/Spring progression)
- 🏫 **Room mismatches** (Chemistry classes scheduled in art studios)
- ⏰ **Unbalanced workloads** (some teachers with 2 hours/day, others with 6)
- 😤 **Frustrated students** waiting weeks to know their class schedules

### **The Opportunity**

Principal Martinez has secured funding for a digital transformation and hired **you** as the lead developer to build Maplewood's first **automated scheduling system**. The system needs to be ready for the **upcoming semester registration** in just one week.

Your mission: Create a web-based platform that can intelligently generate conflict-free schedules while respecting all the school's complex constraints and providing an intuitive interface for both administrators and students.

### **The Stakes**

Success means Maplewood becomes a model for other schools in the district. Failure means another semester of manual chaos and potentially losing students to better-organized competing schools.

**Are you ready to revolutionize Maplewood High School's scheduling system?**

---

## 📋 **Challenge 1: Master Schedule Generator (Primary)**

### **Goal**
Create a **master weekly schedule** that assigns specific time slots, classrooms, and teachers to every course section for a given semester. Think of this as creating the school's complete curriculum timetable.

#### **Input**
- Semester (e.g., "Fall 2024")

#### **Output (Can look different on UI)**
A complete weekly schedule showing:
```
Course: MAT101 / Section - 1
Teacher: John Smith
Room: Classroom-205
Schedule: Monday 9-10AM, Wednesday 10-11AM, Friday 2-4PM
Students: 10 (capacity full)

Course: MAT101 / Section - 2
Teacher: Molly Hendrix
Room: Classroom-204
Schedule: Monday 9-10AM, Wednesday 10-11AM, Friday 2-4PM
Students: 5 (5 spots available)

Course: MAT101 / Section - 3
Teacher: John Smith
Room: Classroom-205
Schedule: Monday 10-11AM, Wednesday 11-12PM, Friday 12-2PM
Students: 9 (1 spot available)

Course: SCI201 / Section - 1
Teacher: Dr. Johnson
Room: Science-Lab-A
Schedule: Tuesday 9-11AM, Thursday 1-3PM
Students: 8 (2 spots available)
```

### Challenge Constraints

> #### **Time Constraints**
> - **School hours**: 9:00 AM - 5:00 PM (7 available hours per day)
> - **Lunch break**: 12:00 PM - 1:00 PM (no classes scheduled)
> - **Available time slots**: 7 hours/day × 5 days = 35 hours per week
> - **Maximum consecutive class time**: 2 hours per session
> - **Minimum class duration**: 1 hour

> #### **Teacher Constraints**
> - **Daily teaching limit**: Maximum 4 hours per day per teacher
> - **Specialization**: Teachers can only teach courses in their specialization area
> - **Sequential teaching**: Teachers can teach back-to-back classes (different courses/sections)
> - **Total teachers**: 50 with various specializations

> #### **Classroom Constraints**
> - **Total classrooms**: 60 with different specializations
> - **Room types**: Regular classrooms, science labs, art studios, gym, computer labs
> - **Course-room matching**: Science courses need labs, art courses need studios, etc.
> - **Capacity**: Each room accommodates **maximum 10 students**

> ### **Algorithm Challenge**
> - **Resource allocation**: Assign teachers and rooms to course sections
> - **Time optimization**: Distribute course hours across the week 
> - **Conflict resolution**: Ensure no double-booking of teachers or rooms
> - **Demand satisfaction**: Create enough sections to accommodate all students
> 
### **Frontend Requirements**
- Interface to generate master schedule for a semester
- Visual weekly calendar showing all course assignments
- Resource utilization statistics (teacher workload, room usage) **(Bonus)**

---

## 👨‍🎓 **Challenge 2: Student Course Planning (Secondary)**

### **Goal**
Allow individual students to select courses and build their personal schedule using the master schedule created in Challenge 1.

### Challenge Constraints

>#### **Academic Constraints**
>- **Graduation requirement**: 30 total credits over 4 years (8 semesters)
>- **Core courses**: 20 mandatory courses (all students must complete)
>- **Timeline**: Students should graduate in 4 years (extendable to 5 for failed courses)
>- **Grade levels**: 9th (freshman), 10th (sophomore), 11th (junior), 12th (senior) (100 students each = 400 total)

### **Frontend Requirements**
- Browse available course sections from the master schedule
- Real-time conflict detection when selecting courses
- Prerequisite validation before enrollment
- Personal schedule visualization
- Academic progress tracking toward graduation (GPA, credits earned, credits left etc.)

### **Business Logic**
- Validate time conflicts with already-selected courses
- Check prerequisite completion using student course history
- Calculate current GPA from passed/failed course records
- Enforce maximum 5 courses per semester limit
- Track credits earned vs. required for graduation
- Display graduation timeline based on current progress **(Bonus)**

### **Academic Data Available**
- **Complete academic history**: 6+ semesters of course completion data for upperclassmen
- **Prerequisite tracking**: Database-enforced prerequisite completion validation
- **GPA calculation**: Real course grades and credits for accurate GPA computation
- **Progress monitoring**: Credits earned, courses passed/failed, graduation requirements

---

## 🗄️ **Database Schema**

You will be provided with a pre-populated SQLite database containing realistic sample data. Design your own REST API structure and table relationships based on the requirements above. You can find further information in details on [DATABASE.md](./DATABASE.md) file.

**Note:** There are some tables missing on purpose and you're expected to create them for your challenges for intermediate and your final results.

### **Expected Data Volume**
- **400 students** across 4 grade levels with realistic academic history
- **50 teachers** with specialized subjects
- **60 classrooms** with various types and equipment
- **57 total courses** (20 core + 37 electives) with semester ordering
- **~6,700 student course records** with pass/fail history for prerequisite validation
- **9 semesters** (6 historical + current + 2 future) enabling realistic academic progression
- Complex prerequisite chains enforced by database triggers
- **Semester ordering system** ensuring logical academic progression

### **Sample Data Structure**
```sql
-- Core Tables (structure example)
students: id, first_name, last_name, grade_level, enrollment_year, expected_graduation_year
semesters: id, name, year, order_in_year, start_date, end_date, is_active
specializations: id, name, room_type_id
teachers: id, first_name, last_name, specialization_id
classrooms: id, name, room_type_id, capacity, equipment
courses: id, code, name, credits, hours_per_week, specialization_id,
         prerequisite_id, semester_order, grade_level_min, grade_level_max
student_course_history: id, student_id, course_id, semester_id, status
```

---

## 🚀 **Getting Started**

### **Project Setup**
1. **Database**: Use the provided `maplewood_school.sqlite` file (pre-populated with 6,700+ academic records)
2. **Technology Stack**: Choose your preferred technologies (suggestions below)
3. **Development**: Build both backend API and frontend interface

> 💡 **Database Ready**: The database includes complete student academic history with prerequisite validation, enabling you to immediately start building Challenge 2 (student course planning) with realistic data.

### **Recommended Tech Stack**
*Feel free to use any technologies you're comfortable with. Here are some suggestions:*

**Backend Options:**
- Java (Spring Boot) **(Preferable)**
- Node.js (Express, Fastify) + TypeScript
- Python (FastAPI, Django, Flask)
- C# (.NET Core)

**Frontend Options:**
- React + TypeScript **(Preferable)**
- Vue.js + TypeScript
- Angular

**Database:**
- SQLite (provided)
- You may add an ORM if preferred (Prisma, TypeORM, SQLAlchemy, etc.)
- You may (and probably will) create your own tables/relationships etc.

---

## 📋 **Submission Requirements**

### **Deliverables**
1. **Source code:** publicly available GitHub repository **(please name the repository with a random animal name)**
2. **Working application:** (instructions to run locally or Dockerfiles)
3. **Brief documentation:** explaining your approach **(optional)**

*Some things are optional but give you an advantage if you do them:*
- Creating Dockerfile(s)
- Creating a Dev Container configuration
- Creating a documentation for your approach
- And other industry standards or nice to haves that you can think of

After your submission, there will be a **1-hour** online interview with our technical staff for the demonstration of your solution. Here is the summary of it:
- You'll be given approximately 20 minutes to explain it
- Afterwards you'll be asked further small challenges based on this project, it's expected to be a verbal conversation so no need to code for these challenges. 
- At the end of the interview, there will be a 10 minutes Q&A session.

We expect to see your algorithmic problem solving skills with the expected technical expertise level of the position. So we'll be evaluating the solution based on code quality and industry standards. You're free to do vibe coding as much as you want but be mindful of the code quality standards, that includes writing tests when you need it, complying to SOLID principles, creating re-usable and maintainable components etc.

---

## 🤝 **Need Help?**

- **Questions?** Feel free to ask for clarification at <DL-eBay-Data-Productization@ebay.com>
- **Technical issues?** Document assumptions you made
- **Stuck on algorithms?** Focus on a working solution first, optimize later
- **Time running short?** Prioritize Challenge 1 (Master Schedule Generator)

---

**Good luck, and we look forward to seeing your innovative solution to Maplewood High School's scheduling challenge!** 🎓
