package com.mhs.api.scheduler.dto;

// Section DTO
public record SectionDto(
        int sectionId,
        String courseCode,
        String courseName,
        int sectionNumber,
        String schedule,    // "Mon 09:00-10:00" or "TBA"
        String roomName,
        String teacherName,
        int capacity,
        int seatsLeft,
        boolean prereqsMet,
        boolean timeConflict,
        boolean canEnroll
) {}
