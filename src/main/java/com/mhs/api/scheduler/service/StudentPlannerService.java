package com.mhs.api.scheduler.service;

import com.mhs.api.scheduler.dto.SectionDto;
import com.mhs.api.scheduler.dto.SemesterDto;
import com.mhs.api.scheduler.dto.StudentProgressDto;
import com.mhs.api.scheduler.dto.StudentScheduleItemDto;
import com.mhs.api.scheduler.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StudentPlannerService {

    private final DataService dataService;

    public StudentPlannerService(DataService dataService) {
        this.dataService = dataService;
    }

    // ---------------------------------------------------------
    // 1. GET ALL SEMESTERS
    // ---------------------------------------------------------
    public List<SemesterDto> getAllSemesters() {
        List<Map<String, Object>> rows = dataService.query("""
        SELECT id, name, year, order_in_year, start_date, end_date, is_active
        FROM semesters
        ORDER BY year, order_in_year
    """);

        List<SemesterDto> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            result.add(new SemesterDto(
                    ((Number) row.get("id")).intValue(),
                    (String) row.get("name"),
                    ((Number) row.get("year")).intValue(),
                    ((Number) row.get("order_in_year")).intValue(),
                    row.get("start_date") != null ? row.get("start_date").toString() : null,
                    row.get("end_date") != null ? row.get("end_date").toString() : null,
                    ((Number) row.get("is_active")).intValue() == 1
            ));
        }

        return result;
    }

    // ---------------------------------------------------------
    // 2. LIST AVAILABLE SECTIONS FOR STUDENT + SEMESTER
    // ---------------------------------------------------------
    public List<SectionDto> listAvailableSections(int studentId, int semesterId) {

        List<Map<String, Object>> rows = dataService.query(
                "SELECT s.id AS section_id, c.code AS course_code, c.name AS course_name, " +
                        "s.section_number, s.schedule, s.seats_left, s.teacher " +
                        "FROM sections s " +
                        "JOIN courses c ON c.id = s.course_id " +
                        "WHERE s.semester_id = ?",
                semesterId
        );

        List<SectionDto> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            result.add(new SectionDto(
                    ((Number) row.get("section_id")).intValue(),
                    (String) row.get("course_code"),
                    (String) row.get("course_name"),
                    (String) row.get("schedule"),
                    ((Number) row.get("seats_left")).intValue(),
                    (String) row.get("teacher"),
                    (boolean) row.get("prereqsMet"),
                    (boolean) row.get("timeConflict")
            ));
        }

        return result;
    }

    // ---------------------------------------------------------
    // 3. GET STUDENT SCHEDULE
    // ---------------------------------------------------------
    public List<StudentScheduleItemDto> getStudentSchedule(int studentId, int semesterId) {

        List<Map<String, Object>> rows = dataService.query(
                "SELECT c.code AS course_code, c.name AS course_name, " +
                        "s.section_number, s.schedule, s.teacher " +
                        "FROM student_schedule ss " +
                        "JOIN sections s ON ss.section_id = s.id " +
                        "JOIN courses c ON s.course_id = c.id " +
                        "WHERE ss.student_id = ? AND s.semester_id = ?",
                studentId, semesterId
        );

        List<StudentScheduleItemDto> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            result.add(new StudentScheduleItemDto(
                    ((Number) row.get("section_id")).intValue(),
                    (String) row.get("course_code"),
                    (String) row.get("course_name"),
                    (String) row.get("schedule"),
                    (String) row.get("teacher"),
                    (boolean) row.get("timeConflict")
            ));
        }

        return result;
    }

    // ---------------------------------------------------------
    // 4. ENROLL STUDENT IN SECTION
    // ---------------------------------------------------------
    public boolean enrollStudent(int studentId, int sectionId) {

        // check seats
        List<Map<String, Object>> seatRows = dataService.query(
                "SELECT seats_left FROM sections WHERE id = ?",
                sectionId
        );

        int seatsLeft = 0;
        if (!seatRows.isEmpty()) {
            seatsLeft = ((Number) seatRows.get(0).get("seats_left")).intValue();
        }

        if (seatsLeft <= 0) return false;

        // enroll
        dataService.update(
                "INSERT INTO student_schedule (student_id, section_id) VALUES (?, ?)",
                studentId, sectionId
        );

        // decrement seats
        dataService.update(
                "UPDATE sections SET seats_left = seats_left - 1 WHERE id = ?",
                sectionId
        );

        return true;
    }

    // ---------------------------------------------------------
    // 5. STUDENT PROGRESS (credits, gpa, remaining)
    // ---------------------------------------------------------
    public StudentProgressDto getProgress(int studentId) {

        List<Map<String, Object>> rows = dataService.query(
                "SELECT COALESCE(SUM(credits), 0) AS total " +
                        "FROM student_course_history " +
                        "WHERE student_id = ? AND status = 'passed'",
                studentId
        );

        int creditsEarned = 0;
        if (!rows.isEmpty() && rows.get(0).get("total") != null) {
            creditsEarned = ((Number) rows.get(0).get("total")).intValue();
        }

        int totalRequiredCredits = 30;  // as per challenge definition
        int creditsRemaining = Math.max(totalRequiredCredits - creditsEarned, 0);

        // GPA (if you store grades later)
        double gpa = 0.0;

        int estimatedYears = (int) Math.ceil(creditsRemaining / 30.0);

        return new StudentProgressDto(
                creditsEarned,
                creditsRemaining,
                gpa,
                estimatedYears
        );
    }

    // ---------------------------------------------------------
// 6. DROP A SECTION
// ---------------------------------------------------------
    public boolean dropSection(int studentId, int sectionId) {

        // Was the student enrolled?
        List<Map<String, Object>> rows = dataService.query(
                "SELECT id FROM student_schedule WHERE student_id = ? AND section_id = ?",
                studentId, sectionId
        );

        if (rows.isEmpty()) {
            return false; // Not enrolled
        }

        // Remove enrollment
        dataService.update(
                "DELETE FROM student_schedule WHERE student_id = ? AND section_id = ?",
                studentId, sectionId
        );

        // Increase seats
        dataService.update(
                "UPDATE sections SET seats_left = seats_left + 1 WHERE id = ?",
                sectionId
        );

        return true;
    }

}
