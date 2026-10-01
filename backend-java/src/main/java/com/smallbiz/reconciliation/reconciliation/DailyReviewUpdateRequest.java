package com.smallbiz.reconciliation.reconciliation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DailyReviewUpdateRequest(
		@NotNull ReviewStatus status,
		@Size(max = 2000) String memo,
		@NotNull Long version
) {
}
