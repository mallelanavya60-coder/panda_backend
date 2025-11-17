package com.mhs.api.scheduler.dto;

// Section DTO
public record SectionDto(
        int sectionId,
        String courseCode,
        String courseName,
        String schedule,  // e.g., "Mon 10:00-11:30"
        int seatsLeft,
        String teacher,
        boolean prereqsMet,
        boolean timeConflict
) {}
