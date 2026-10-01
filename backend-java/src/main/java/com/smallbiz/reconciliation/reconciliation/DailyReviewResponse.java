package com.smallbiz.reconciliation.reconciliation;

import java.time.Instant;
import java.time.LocalDate;

public record DailyReviewResponse(
		LocalDate date,
		ReviewStatus status,
		String memo,
		Instant lastReviewedAt,
		Instant updatedAt,
		long version
) {

	static DailyReviewResponse absent(LocalDate date) {
		return new DailyReviewResponse(date, ReviewStatus.UNREVIEWED, null, null, null, 0L);
	}

	static DailyReviewResponse from(DailyReview review) {
		return new DailyReviewResponse(
				review.getReviewDate(),
				review.getStatus(),
				review.getMemo(),
				review.getLastReviewedAt(),
				review.getUpdatedAt(),
				review.getVersion() == null ? 0L : review.getVersion()
		);
	}
}
