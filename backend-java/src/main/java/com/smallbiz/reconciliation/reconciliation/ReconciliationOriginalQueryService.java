package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.reconciliation.upload.BankTransaction;
import com.smallbiz.reconciliation.upload.BankTransactionRepository;
import com.smallbiz.reconciliation.upload.BusinessEvent;
import com.smallbiz.reconciliation.upload.BusinessEventRepository;

@Service
public class ReconciliationOriginalQueryService {

	private static final Sort ORIGINAL_SORT = Sort.by("sourceRowNumber").ascending().and(Sort.by("id").ascending());

	private final BusinessEventRepository businessEventRepository;
	private final BankTransactionRepository bankTransactionRepository;

	public ReconciliationOriginalQueryService(
			BusinessEventRepository businessEventRepository,
			BankTransactionRepository bankTransactionRepository
	) {
		this.businessEventRepository = businessEventRepository;
		this.bankTransactionRepository = bankTransactionRepository;
	}

	@Transactional(readOnly = true)
	public BusinessOriginalListResponse businessDay(LocalDate date, int page, int size) {
		Page<BusinessEvent> result = businessEventRepository.findByExpectedCashDateAndEventTypeIn(
				date,
				ReconciliationCashEvents.TYPES,
				PageRequest.of(page, size, ORIGINAL_SORT)
		);
		return new BusinessOriginalListResponse(
				result.getContent().stream().map(BusinessOriginalResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public BankOriginalListResponse bankDay(LocalDate date, int page, int size) {
		Page<BankTransaction> result = bankTransactionRepository.findByBookedDate(
				date,
				PageRequest.of(page, size, ORIGINAL_SORT)
		);
		return new BankOriginalListResponse(
				result.getContent().stream().map(BankOriginalResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements()
		);
	}
}
