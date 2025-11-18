package com.mhs.api.scheduler.controller;

import com.mhs.api.scheduler.dto.*;
import com.mhs.api.scheduler.service.StudentPlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentPlannerController {
    private final StudentPlannerService plannerService;

    @GetMapping("/semesters")
    public List<SemesterDto> getSemesters() { return plannerService.getAllSemesters(); }

    @GetMapping("/{studentId}/available-sections")
    public List<SectionDto> getAvailableSections(@PathVariable int studentId, @RequestParam int semesterId) {
        return plannerService.listAvailableSections(studentId, semesterId);
    }

    @GetMapping("/{studentId}/schedule")
    public List<StudentScheduleItemDto> getSchedule(@PathVariable int studentId, @RequestParam int semesterId) {
        return plannerService.getStudentSchedule(studentId, semesterId);
    }

    @GetMapping("/{studentId}/progress")
    public StudentProgressDto getProgress(@PathVariable int studentId) {
        return plannerService.getProgress(studentId);
    }

    public record EnrollRequest(int studentId, int sectionId, int semesterId) {}
    public record DropRequest(int studentId, int sectionId) {}

    @PostMapping("/enroll")
    public Map<String,Object> enroll(@RequestBody EnrollRequest req) {
        return plannerService.attemptEnroll(req.studentId(), req.sectionId(), req.semesterId());
    }

    @PostMapping("/drop")
    public Map<String,Object> drop(@RequestBody DropRequest req) {
        return plannerService.dropEnrollment(req.studentId(), req.sectionId());
    }
}
