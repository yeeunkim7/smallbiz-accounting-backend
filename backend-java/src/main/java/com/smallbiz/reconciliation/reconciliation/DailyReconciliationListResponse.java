package com.smallbiz.reconciliation.reconciliation;

import java.util.List;

public record DailyReconciliationListResponse(List<DailyReconciliationDayResponse> days) {
}
