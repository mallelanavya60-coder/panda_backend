package com.mhs.api.scheduler.model;

public class EnrollDropRequest {
    public record EnrollRequest(int studentId, int sectionId) {}
    public record DropRequest(int studentId, int sectionId) {}

}
