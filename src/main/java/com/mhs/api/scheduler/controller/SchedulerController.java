package com.mhs.api.scheduler.controller;

import com.mhs.api.scheduler.model.ScheduleSection;
import com.mhs.api.scheduler.model.TimeSlot;
import com.mhs.api.scheduler.service.DataService;
import com.mhs.api.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/schedule")
public class SchedulerController {

    private final SchedulerService schedulerService;
    private final DataService dataService;

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Integer> body) {
        Integer semesterId = body.get("semesterId");
        if (semesterId == null) throw new IllegalArgumentException("semesterId required");
        return schedulerService.generate(semesterId);
    }

    @GetMapping("/{semesterId}")
    public List<Map<String, Object>> getSchedule(@PathVariable int semesterId) {
        List<Map<String, Object>> rows = dataService.query(
                "SELECT sa.section_id, s.section_number, c.code as course_code, c.name as course_name, " +
                        "t.first_name || ' ' || t.last_name as teacher_name, r.name as room_name, ts.day, ts.start_time, ts.end_time, s.capacity " +
                        "FROM schedule_assignments sa " +
                        "JOIN sections s ON s.id = sa.section_id " +
                        "JOIN courses c ON c.id = s.course_id " +
                        "JOIN teachers t ON t.id = sa.teacher_id " +
                        "JOIN classrooms r ON r.id = sa.room_id " +
                        "JOIN timeslots ts ON ts.id = sa.timeslot_id " +
                        "WHERE s.semester_id = ? ORDER BY sa.section_id, ts.day, ts.start_time", semesterId);

        Map<Integer, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Integer sid = (Integer) r.get("section_id");
            Map<String, Object> bucket = out.computeIfAbsent(sid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("course", r.get("course_code"));
                m.put("course_name", r.get("course_name"));
                m.put("section", r.get("section_number"));
                m.put("teacher", r.get("teacher_name"));
                m.put("room", r.get("room_name"));
                m.put("capacity", r.get("capacity"));
                m.put("students_enrolled", getEnrollmentCountForSection(sid)); // helper below
                m.put("schedule", new ArrayList<String>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<String> sched = (List<String>) bucket.get("schedule");
            String timestr = String.format("%s %s-%s", r.get("day"), r.get("start_time"), r.get("end_time"));
            sched.add(timestr);
        }
        return new ArrayList<>(out.values());
    }

    // helper method (add to controller or a service)
    private int getEnrollmentCountForSection(int sectionId) {
        try {
            List<Map<String, Object>> res = dataService.query("SELECT COUNT(*) as cnt FROM student_enrollments WHERE section_id = ?", sectionId);
            if (!res.isEmpty()) {
                return ((Number) res.get(0).get("cnt")).intValue();
            }
        } catch (Exception ex) {
            // table may not exist yet; default to 0
        }
        return 0;
    }

    @GetMapping("/get")
    public List<Map<String, Object>> getScheduleByName(@RequestParam String semester) {
        // Step 1 — Find semester by name
        List<Map<String, Object>> res = dataService.query(
                "SELECT id FROM semesters WHERE name = ?",
                semester
        );

        if (res.isEmpty()) {
            throw new IllegalArgumentException("Semester not found: " + semester);
        }

        int semesterId = ((Number) res.get(0).get("id")).intValue();

        // Step 2 — Reuse existing logic by calling getSchedule(semesterId)
        return getSchedule(semesterId);
    }



    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "time", new Date().toString());
    }

}
