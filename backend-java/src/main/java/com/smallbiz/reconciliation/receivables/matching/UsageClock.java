package com.smallbiz.reconciliation.receivables.matching;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Authoritative database wall time; separate from the authentication/session clock. */
@Component
public class UsageClock {
    private final JdbcTemplate jdbc;
    public UsageClock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Instant instant() {
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
    }
}
