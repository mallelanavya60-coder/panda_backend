package com.mhs.api.scheduler.dto;

// Student Progress DTO
public record StudentProgressDto(
        int creditsEarned,
        int creditsRequired,
        int creditsRemaining,
        double gpa,
        double estimatedYears
) {

    public StudentProgressDto(int creditsEarned, int creditsRequired, int creditsRemaining,
                           double gpa, double estimatedYears) {
        this.creditsEarned = creditsEarned;
        this.creditsRequired = creditsRequired;
        this.creditsRemaining = creditsRemaining;
        this.gpa = gpa;
        this.estimatedYears = estimatedYears;
    }

}


