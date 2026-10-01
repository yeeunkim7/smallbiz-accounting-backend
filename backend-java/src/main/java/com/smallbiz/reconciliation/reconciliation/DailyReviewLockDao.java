package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyReviewLockDao {

	private final JdbcTemplate jdbcTemplate;

	public DailyReviewLockDao(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void insertPlaceholderIfAbsent(LocalDate date) {
		jdbcTemplate.update(
				"""
						INSERT INTO daily_review (review_date, status, memo, last_reviewed_at, updated_at, version)
						VALUES (?, 'UNREVIEWED', NULL, NULL, CURRENT_TIMESTAMP, 0)
						ON CONFLICT (review_date) DO NOTHING
						""",
				date
		);
	}
}
