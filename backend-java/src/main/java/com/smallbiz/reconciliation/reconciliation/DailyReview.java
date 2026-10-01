package com.smallbiz.reconciliation.reconciliation;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "daily_review")
public class DailyReview {

	@Id
	@Column(name = "review_date", nullable = false)
	private LocalDate reviewDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private ReviewStatus status;

	@Column(name = "memo", length = 2000)
	private String memo;

	@Column(name = "last_reviewed_at")
	private Instant lastReviewedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	protected DailyReview() {
	}

	DailyReview(LocalDate reviewDate) {
		this.reviewDate = reviewDate;
		this.status = ReviewStatus.UNREVIEWED;
		this.updatedAt = Instant.now();
	}

	void applyUserUpdate(ReviewStatus status, String memo) {
		this.status = status;
		this.memo = memo;
		if (status == ReviewStatus.REVIEWED) {
			this.lastReviewedAt = Instant.now();
		}
		touchUpdatedAt();
	}

	void onOriginalsChanged() {
		if (this.status == ReviewStatus.REVIEWED) {
			this.status = ReviewStatus.NEEDS_RECHECK;
		}
		touchUpdatedAt();
	}

	private void touchUpdatedAt() {
		Instant next = Instant.now();
		if (this.updatedAt != null && !next.isAfter(this.updatedAt)) {
			next = this.updatedAt.plusNanos(1);
		}
		this.updatedAt = next;
	}

	public LocalDate getReviewDate() {
		return reviewDate;
	}

	public ReviewStatus getStatus() {
		return status;
	}

	public String getMemo() {
		return memo;
	}

	public Instant getLastReviewedAt() {
		return lastReviewedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getVersion() {
		return version;
	}
}
