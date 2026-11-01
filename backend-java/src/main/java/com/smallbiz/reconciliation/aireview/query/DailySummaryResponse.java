package com.smallbiz.reconciliation.aireview.query;

import java.time.LocalDate;
import com.smallbiz.reconciliation.reconciliation.ReviewStatus;

/** Money is serialized as decimal text so browser/model transports cannot round long values. */
public record DailySummaryResponse(
        LocalDate date,
        ReviewStatus reviewStatus,
        String matchingStatus,
        DirectionSummary incoming,
        DirectionSummary outgoing
) {
    public record DirectionSummary(
            String expectedAmount,
            String actualAmount,
            String difference,
            long businessCount,
            long bankCount,
            String sourcePresence,
            String comparisonStatus
    ) { }
}
