package com.mhs.api.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataService {

    public final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> query(String sql, Object... params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    public int update(String sql, Object... params) {
        return jdbcTemplate.update(sql, params);
    }

    public void insertAndReturnKey(String sql, Object... params) {
        // using simple update; for SQLite we can run a separate SELECT last_insert_rowid()
        jdbcTemplate.update(sql, params);
        jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Number.class);
    }
}
