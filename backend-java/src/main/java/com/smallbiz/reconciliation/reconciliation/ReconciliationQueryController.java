package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/reconciliations")
public class ReconciliationQueryController {

	private final ReconciliationQueryService reconciliationQueryService;
	private final ReconciliationOriginalQueryService originalQueryService;
	private final DailyReviewService dailyReviewService;

	public ReconciliationQueryController(
			ReconciliationQueryService reconciliationQueryService,
			ReconciliationOriginalQueryService originalQueryService,
			DailyReviewService dailyReviewService
	) {
		this.reconciliationQueryService = reconciliationQueryService;
		this.originalQueryService = originalQueryService;
		this.dailyReviewService = dailyReviewService;
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

	@GetMapping("/daily/{date}/business")
	public BusinessOriginalListResponse businessOriginals(
			@PathVariable LocalDate date,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return originalQueryService.businessDay(date, page, size);
	}

	@GetMapping("/daily/{date}/bank")
	public BankOriginalListResponse bankOriginals(
			@PathVariable LocalDate date,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return originalQueryService.bankDay(date, page, size);
	}

	@GetMapping("/daily/{date}/review")
	public DailyReviewResponse getReview(@PathVariable LocalDate date) {
		return dailyReviewService.get(date);
	}

	@PutMapping("/daily/{date}/review")
	public DailyReviewResponse putReview(
			@PathVariable LocalDate date,
			@Valid @RequestBody DailyReviewUpdateRequest request
	) {
		return dailyReviewService.put(date, request);
	}
}
