package com.mhs.api.scheduler.service;

import com.mhs.api.scheduler.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentPlannerService {
    private final DataService dataService;

    // --- Semesters
    public List<SemesterDto> getAllSemesters() {
        List<Map<String,Object>> rows = dataService.query(
                "SELECT id, name, year, order_in_year, start_date, end_date, is_active " +
                        "FROM semesters ORDER BY year, order_in_year"
        );
        var out = new ArrayList<SemesterDto>();
        for (var r : rows) {
            out.add(new SemesterDto(
                    ((Number) r.get("id")).intValue(),
                    r.get("name") == null ? "" : r.get("name").toString(),
                    r.get("year") == null ? 0 : ((Number) r.get("year")).intValue(),
                    r.get("order_in_year") == null ? 0 : ((Number) r.get("order_in_year")).intValue(),
                    r.get("start_date") == null ? null : r.get("start_date").toString(),
                    r.get("end_date") == null ? null : r.get("end_date").toString(),
                    r.get("is_active") == null ? false : (((Number) r.get("is_active")).intValue() == 1)
            ));
        }
        return out;
    }

    // --- List available sections (student-specific checks)
    public List<SectionDto> listAvailableSections(int studentId, int semesterId) {
        // 1) sections + timeslot/room/teacher (some fields may be null)
        List<Map<String,Object>> rows = dataService.query(
                "SELECT s.id AS section_id, c.code AS course_code, c.name AS course_name, " +
                        "s.section_number, s.capacity, " +
                        "ts.day, ts.start_time, ts.end_time, " +
                        "cl.name AS room_name, t.first_name || ' ' || t.last_name AS teacher_name, " +
                        "c.prerequisite_id " +
                        "FROM sections s " +
                        "JOIN courses c ON c.id = s.course_id " +
                        "LEFT JOIN schedule_assignments sa ON sa.section_id = s.id " +
                        "LEFT JOIN timeslots ts ON ts.id = sa.timeslot_id " +
                        "LEFT JOIN classrooms cl ON cl.id = sa.room_id " +
                        "LEFT JOIN teachers t ON t.id = sa.teacher_id " +
                        "WHERE s.semester_id = ?",
                semesterId
        );

        // 2) passed courses set (for prereqs)
        Set<Integer> passed = dataService.query(
                "SELECT course_id FROM student_course_history WHERE student_id = ? AND status = 'passed'",
                studentId
        ).stream().map(m -> ((Number)m.get("course_id")).intValue()).collect(Collectors.toSet());

        // 3) student's occupied time tokens this semester
        Set<String> occupied = dataService.query(
                "SELECT ts.day || ' ' || ts.start_time || '-' || ts.end_time AS tok " +
                        "FROM student_enrollments se " +
                        "JOIN sections s ON s.id = se.section_id " +
                        "JOIN schedule_assignments sa ON sa.section_id = s.id " +
                        "JOIN timeslots ts ON ts.id = sa.timeslot_id " +
                        "WHERE se.student_id = ? AND s.semester_id = ? AND se.status = 'enrolled'",
                studentId, semesterId
        ).stream().map(m -> m.get("tok").toString()).collect(Collectors.toSet());

        // 4) number courses currently enrolled this semester
        int currentCount = dataService.queryForInt(
                "SELECT COUNT(DISTINCT se.section_id) AS c FROM student_enrollments se " +
                        "JOIN sections s ON s.id = se.section_id " +
                        "WHERE se.student_id = ? AND s.semester_id = ? AND se.status = 'enrolled'",
                studentId, semesterId
        );

        List<SectionDto> out = new ArrayList<>();
        for (var r : rows) {
            int sectionId = r.get("section_id") == null ? 0 : ((Number) r.get("section_id")).intValue();
            int sectionNumber = r.get("section_number") == null ? 0 : ((Number) r.get("section_number")).intValue();
            int capacity = r.get("capacity") == null ? 10 : ((Number) r.get("capacity")).intValue();
            String day = r.get("day") == null ? "" : r.get("day").toString();
            String start = r.get("start_time") == null ? "" : r.get("start_time").toString();
            String end = r.get("end_time") == null ? "" : r.get("end_time").toString();
            String schedule = (day.isEmpty() || start.isEmpty() || end.isEmpty()) ? "TBA" : day + " " + start + "-" + end;
            String room = r.get("room_name") == null ? "TBA" : r.get("room_name").toString();
            String teacher = r.get("teacher_name") == null ? "TBA" : r.get("teacher_name").toString();
            Integer prereq = r.get("prerequisite_id") == null ? null : ((Number) r.get("prerequisite_id")).intValue();

            // seats left (compute from student_enrollments)
            int enrolled = dataService.queryForInt(
                    "SELECT COUNT(*) FROM student_enrollments WHERE section_id = ? AND status = 'enrolled'",
                    sectionId
            );
            int seatsLeft = Math.max(0, capacity - enrolled);

            // time conflict check (token-level)
            boolean timeConflict = false;
            if (!"TBA".equals(schedule)) {
                String tok = schedule;
                if (occupied.contains(tok)) timeConflict = true;
            }

            boolean prereqOk = prereq == null || passed.contains(prereq);
            boolean canEnroll = !timeConflict && prereqOk && seatsLeft > 0 && currentCount < 5;

            out.add(new SectionDto(
                    sectionId,
                    r.get("course_code") == null ? "" : r.get("course_code").toString(),
                    r.get("course_name") == null ? "" : r.get("course_name").toString(),
                    sectionNumber,
                    schedule,
                    room,
                    teacher,
                    capacity,
                    seatsLeft,
                    prereqOk,
                    timeConflict,
                    canEnroll
            ));
        }

        return out;
    }

    // --- getStudentSchedule
    public List<StudentScheduleItemDto> getStudentSchedule(int studentId, int semesterId) {
        List<Map<String,Object>> rows = dataService.query(
                "SELECT s.id AS section_id, c.code AS course_code, c.name AS course_name, s.section_number, " +
                        "ts.day, ts.start_time, ts.end_time, cl.name AS room_name, t.first_name || ' ' || t.last_name AS teacher_name " +
                        "FROM student_enrollments se " +
                        "JOIN sections s ON s.id = se.section_id " +
                        "JOIN courses c ON c.id = s.course_id " +
                        "LEFT JOIN schedule_assignments sa ON sa.section_id = s.id " +
                        "LEFT JOIN timeslots ts ON ts.id = sa.timeslot_id " +
                        "LEFT JOIN classrooms cl ON cl.id = sa.room_id " +
                        "LEFT JOIN teachers t ON t.id = sa.teacher_id " +
                        "WHERE se.student_id = ? AND s.semester_id = ? AND se.status = 'enrolled' " +
                        "ORDER BY ts.day, ts.start_time",
                studentId, semesterId
        );

        var out = new ArrayList<StudentScheduleItemDto>();
        for (var r : rows) {
            out.add(new StudentScheduleItemDto(
                    r.get("section_id") == null ? 0 : ((Number) r.get("section_id")).intValue(),
                    r.get("course_code") == null ? "" : r.get("course_code").toString(),
                    r.get("course_name") == null ? "" : r.get("course_name").toString(),
                    r.get("section_number") == null ? 0 : ((Number) r.get("section_number")).intValue(),
                    r.get("day") == null ? "" : r.get("day").toString(),
                    r.get("start_time") == null ? "" : r.get("start_time").toString(),
                    r.get("end_time") == null ? "" : r.get("end_time").toString(),
                    r.get("room_name") == null ? "TBA" : r.get("room_name").toString(),
                    r.get("teacher_name") == null ? "TBA" : r.get("teacher_name").toString()
            ));
        }
        return out;
    }

    // --- progress
    // --- progress (Adjusted for Pass/Fail only)
    public StudentProgressDto getProgress(int studentId) {
        // 1. Calculate Credits Earned (Passed courses)
        int creditsEarned = dataService.queryForInt(
                "SELECT COALESCE(SUM(c.credits),0) AS total " +
                        "FROM student_course_history sch JOIN courses c ON c.id = sch.course_id " +
                        "WHERE sch.student_id = ? AND sch.status = 'passed'",
                studentId
        );

        int required = 30; // Graduation requirement
        int remaining = Math.max(0, required - creditsEarned);

        // 2. Calculate Pass/Fail Ratio (Metric to replace GPA)

        // Total credits attempted (passed and failed)
        List<Map<String,Object>> attemptedHistory = dataService.query(
                "SELECT sch.status, c.credits FROM student_course_history sch JOIN courses c ON c.id = sch.course_id " +
                        "WHERE sch.student_id = ?",
                studentId
        );

        int totalAttemptedCredits = 0;
        int passedCredits = 0;

        for (var row : attemptedHistory) {
            String status = row.get("status") != null ? row.get("status").toString().toLowerCase() : "";
            int credits = row.get("credits") instanceof Number ? ((Number) row.get("credits")).intValue() : 0;

            totalAttemptedCredits += credits;
            if (status.equals("passed")) {
                passedCredits += credits;
            }
        }

        // Pass/Fail Ratio (e.g., 92.5%)
        double passFailRatio = (totalAttemptedCredits > 0)
                ? ((double) passedCredits / totalAttemptedCredits) * 100.0
                : 0.0;

        // Round to two decimal places
        passFailRatio = Math.round(passFailRatio * 10.0) / 10.0;

        // 3. Estimated Graduation Timeline
        double expectedCreditsPerYear = 14.0;
        double estYears = (expectedCreditsPerYear > 0) ? Math.ceil(remaining / expectedCreditsPerYear) : 0.0;
        estYears = Math.max(0.0, estYears);

        // We temporarily pass the Pass/Fail Ratio in the 'gpa' field of the DTO
        // since we cannot change the DTO signature here.
        return new StudentProgressDto(creditsEarned, required, remaining, passFailRatio, estYears);
    }

    // --- attempt enroll
    public Map<String,Object> attemptEnroll(int studentId, int sectionId, int semesterId) {
        try {
            // already enrolled?
            if (!dataService.query("SELECT 1 FROM student_enrollments WHERE student_id = ? AND section_id = ? AND status = 'enrolled'", studentId, sectionId).isEmpty())
                return Map.of("ok", false, "message", "Already enrolled");

            // capacity check
            Map<String,Object> sec = dataService.query("SELECT capacity, course_id FROM sections WHERE id = ?", sectionId).stream().findFirst().orElse(null);
            if (sec == null) return Map.of("ok", false, "message", "Section not found");
            int capacity = sec.get("capacity") == null ? 10 : ((Number)sec.get("capacity")).intValue();
            int enrolled = dataService.queryForInt("SELECT COUNT(*) FROM student_enrollments WHERE section_id = ? AND status = 'enrolled'", sectionId);
            if (enrolled >= capacity) return Map.of("ok", false, "message", "Section full");

            // max 5
            int currentCount = dataService.queryForInt(
                    "SELECT COUNT(DISTINCT se.section_id) FROM student_enrollments se JOIN sections s ON s.id = se.section_id WHERE se.student_id = ? AND s.semester_id = ? AND se.status = 'enrolled'",
                    studentId, semesterId
            );
            if (currentCount >= 5) return Map.of("ok", false, "message", "Max 5 courses per semester");

            // time conflict (token)
            Set<String> myTimes = dataService.query(
                    "SELECT ts.day || ' ' || ts.start_time || '-' || ts.end_time AS tok " +
                            "FROM student_enrollments se JOIN sections s ON s.id = se.section_id " +
                            "JOIN schedule_assignments sa ON sa.section_id = s.id JOIN timeslots ts ON ts.id = sa.timeslot_id " +
                            "WHERE se.student_id = ? AND s.semester_id = ? AND se.status = 'enrolled'",
                    studentId, semesterId
            ).stream().map(m -> m.get("tok").toString()).collect(Collectors.toSet());

            Set<String> targetTimes = dataService.query(
                    "SELECT ts.day || ' ' || ts.start_time || '-' || ts.end_time AS tok FROM schedule_assignments sa JOIN timeslots ts ON ts.id = sa.timeslot_id WHERE sa.section_id = ?",
                    sectionId
            ).stream().map(m -> m.get("tok").toString()).collect(Collectors.toSet());

            for (String t : targetTimes) if (myTimes.contains(t)) return Map.of("ok", false, "message", "Time conflict: " + t);

            // prereq
            Integer courseId = sec.get("course_id") == null ? null : ((Number)sec.get("course_id")).intValue();
            if (courseId != null) {
                List<Map<String,Object>> p = dataService.query("SELECT prerequisite_id FROM courses WHERE id = ?", courseId);
                Integer prereq = p.isEmpty() || p.get(0).get("prerequisite_id") == null ? null : ((Number)p.get(0).get("prerequisite_id")).intValue();
                if (prereq != null) {
                    Set<Integer> passed = dataService.query("SELECT course_id FROM student_course_history WHERE student_id = ? AND status = 'passed'", studentId)
                            .stream().map(m -> ((Number)m.get("course_id")).intValue()).collect(Collectors.toSet());
                    if (!passed.contains(prereq)) return Map.of("ok", false, "message", "Prerequisite not satisfied");
                }
            }

            // insert enrollment
            dataService.update("INSERT INTO student_enrollments (student_id, section_id, status) VALUES (?,?,?)", studentId, sectionId, "enrolled");
            return Map.of("ok", true, "message", "Enrolled");
        } catch (Exception ex) {
            ex.printStackTrace();
            return Map.of("ok", false, "message", ex.getMessage());
        }
    }

    // --- drop
    public Map<String,Object> dropEnrollment(int studentId, int sectionId) {
        try {
            int updated = dataService.update("UPDATE student_enrollments SET status='dropped' WHERE student_id = ? AND section_id = ? AND status = 'enrolled'",
                    studentId, sectionId);
            if (updated == 0) return Map.of("ok", false, "message", "Not enrolled");
            return Map.of("ok", true, "message", "Dropped");
        } catch (Exception ex) {
            ex.printStackTrace();
            return Map.of("ok", false, "message", ex.getMessage());
        }
    }
}
