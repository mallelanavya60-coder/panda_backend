package com.mhs.api.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;

@Service
public class DataService {

    private final DataSource dataSource;
    public final JdbcTemplate jdbcTemplate;

    public DataService(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbc; }

    public List<Map<String, Object>> query(String sql, Object... params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    public int update(String sql, Object... params) {
        return jdbcTemplate.update(sql, params);
    }

    public int queryForInt(String sql, Object... params) {
        Integer v = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return v == null ? 0 : v;
    }

    public Integer insertAndReturnKey(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    return null;
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

}
