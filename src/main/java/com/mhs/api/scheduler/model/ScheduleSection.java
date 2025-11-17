package com.mhs.api.scheduler.model;

import java.util.ArrayList;
import java.util.List;

public class ScheduleSection {

    public int sectionId;

    public int courseId;

    public String courseCode;

    public String courseName;

    public String teacher;

    public String room;

    public List<TimeSlot> times = new ArrayList<>();

}
