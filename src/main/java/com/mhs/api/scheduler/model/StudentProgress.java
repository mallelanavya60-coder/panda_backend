package com.mhs.api.scheduler.model;

import lombok.*;

@Getter
@Setter
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentProgress {
    private int creditsEarned;
    private int creditsRequired;
    private int creditsRemaining;
    private double gpa;
    private double estimatedYears;
}

