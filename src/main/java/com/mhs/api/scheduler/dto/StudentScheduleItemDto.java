package com.mhs.api.scheduler.dto;

// Student Schedule Item DTO
public record StudentScheduleItemDto(
        int sectionId,
        String courseCode,
        String courseName,
        String schedule, // e.g., "Tue 14:00-15:30"
        String teacher,
        boolean timeConflict
) {}
