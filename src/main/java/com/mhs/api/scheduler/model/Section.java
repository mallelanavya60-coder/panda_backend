package com.mhs.api.scheduler.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Section {
    private int sectionId;
    private String courseName;
    private int credits;
    private String teacher;
    private String dayOfWeek;
    private String startTime;
    private String endTime;

    public static class Mapper implements RowMapper<Section> {
        @Override
        public Section mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Section(
                    rs.getInt("section_id"),
                    rs.getString("course_name"),
                    rs.getInt("credits"),
                    rs.getString("teacher"),
                    rs.getString("day_of_week"),
                    rs.getString("start_time"),
                    rs.getString("end_time")
            );
        }
    }
}

