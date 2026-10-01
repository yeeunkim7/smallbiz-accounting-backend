package com.smallbiz.reconciliation.reconciliation;

import java.util.List;

import com.smallbiz.reconciliation.upload.BusinessEventType;

final class ReconciliationCashEvents {

	static final List<BusinessEventType> TYPES = List.of(
			BusinessEventType.CHARGE_EXPECTED,
			BusinessEventType.SETTLEMENT_EXPECTED,
			BusinessEventType.REFUND_EXPECTED
	);

	private ReconciliationCashEvents() {
	}
}
