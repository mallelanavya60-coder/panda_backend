package com.mhs.api.scheduler.dto;

// Student Schedule Item DTO
public record StudentScheduleItemDto(
        int sectionId,
        String courseCode,
        String courseName,
        int sectionNumber,
        String day,
        String startTime,
        String endTime,
        String roomName,
        String teacherName
) {}
