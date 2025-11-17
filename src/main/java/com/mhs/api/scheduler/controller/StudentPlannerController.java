package com.mhs.api.scheduler.controller;

import com.mhs.api.scheduler.dto.*;
import com.mhs.api.scheduler.service.StudentPlannerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentPlannerController {

    private final StudentPlannerService plannerService;

    public StudentPlannerController(StudentPlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @GetMapping("/semesters")
    public List<SemesterDto> getSemesters() {
        return plannerService.getAllSemesters();
    }

    @GetMapping("/{studentId}/available-sections")
    public List<SectionDto> getAvailableSections(
            @PathVariable int studentId,
            @RequestParam int semesterId
    ) {
        return plannerService.listAvailableSections(studentId, semesterId);
    }

    @GetMapping("/{studentId}/schedule")
    public List<StudentScheduleItemDto> getSchedule(
            @PathVariable int studentId,
            @RequestParam int semesterId
    ) {
        return plannerService.getStudentSchedule(studentId, semesterId);
    }

    @GetMapping("/{studentId}/progress")
    public StudentProgressDto getProgress(@PathVariable int studentId) {
        return plannerService.getProgress(studentId);
    }

    @PostMapping("/enroll")
    public boolean enroll(@RequestBody EnrollRequest request) {
        return plannerService.enrollStudent(request.studentId(), request.sectionId());
    }

    @PostMapping("/drop")
    public boolean drop(@RequestBody DropRequest request) {
        return plannerService.dropSection(request.studentId(), request.sectionId());
    }

    // Request DTOs
    public record EnrollRequest(int studentId, int sectionId, int semesterId) {}
    public record DropRequest(int studentId, int sectionId) {}
}
