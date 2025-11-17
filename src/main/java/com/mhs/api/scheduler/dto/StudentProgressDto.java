package com.mhs.api.scheduler.dto;

// Student Progress DTO
public record StudentProgressDto(
        int creditsEarned,
        int creditsRemaining,
        double gpa,
        int estimatedYearsToGraduate
) {}
