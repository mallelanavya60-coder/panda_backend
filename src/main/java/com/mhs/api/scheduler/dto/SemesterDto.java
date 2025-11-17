package com.mhs.api.scheduler.dto;

public record SemesterDto(
    int id,
    String name,
    int year,
    int order_in_year,
    String start_date,
    String end_date,
    boolean is_active
) {}


