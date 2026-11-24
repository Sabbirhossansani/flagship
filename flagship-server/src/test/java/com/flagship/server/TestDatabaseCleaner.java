package com.flagship.server;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cleans all FlagShip database tables in child-before-parent foreign-key order
 * so integration and JPA slice tests remain strictly isolated across H2 and PostgreSQL.
 */
public final class TestDatabaseCleaner {

    private TestDatabaseCleaner() {
    }

    public static void cleanAllTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM experiment_events");
        jdbcTemplate.execute("DELETE FROM experiments");
        jdbcTemplate.execute("DELETE FROM scheduled_flag_changes");
        jdbcTemplate.execute("DELETE FROM change_requests");
        jdbcTemplate.execute("DELETE FROM flag_change_history");
        jdbcTemplate.execute("DELETE FROM project_role_assignments");
        jdbcTemplate.execute("DELETE FROM flags");
        jdbcTemplate.execute("DELETE FROM segments");
        jdbcTemplate.execute("DELETE FROM environments");
        jdbcTemplate.execute("DELETE FROM projects");
    }
}
