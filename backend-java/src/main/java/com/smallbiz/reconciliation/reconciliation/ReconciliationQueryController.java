package com.smallbiz.reconciliation.reconciliation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliations")
public class ReconciliationQueryController {

	private final ReconciliationQueryService reconciliationQueryService;

	public ReconciliationQueryController(ReconciliationQueryService reconciliationQueryService) {
		this.reconciliationQueryService = reconciliationQueryService;
	}

	@GetMapping("/daily")
	public DailyReconciliationListResponse daily(
			@RequestParam("from") String from,
			@RequestParam("to") String to
	) {
		return reconciliationQueryService.daily(from, to);
	}

	@GetMapping("/monthly")
	public MonthlyReconciliationResponse monthly(@RequestParam("yearMonth") String yearMonth) {
		return reconciliationQueryService.monthly(yearMonth);
	}
}
