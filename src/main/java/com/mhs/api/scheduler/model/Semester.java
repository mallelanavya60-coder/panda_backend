package com.mhs.api.scheduler.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Semester {
    private int id;
    private String name;
    private int year;
    private int orderInYear;
    private String startDate; // or LocalDate if you prefer
    private String endDate;   // or LocalDate if you prefer
    private boolean isActive;
}

