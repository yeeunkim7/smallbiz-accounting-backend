package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.reconciliation.reconciliation.DailyReviewService;

@Service
public class BankUploadService {

	private static final int ID_BATCH_SIZE = 500;

	private final UploadFileRepository uploadFileRepository;
	private final BankTransactionRepository bankTransactionRepository;
	private final DailyReviewService dailyReviewService;

	public BankUploadService(
			UploadFileRepository uploadFileRepository,
			BankTransactionRepository bankTransactionRepository,
			DailyReviewService dailyReviewService
	) {
		this.uploadFileRepository = uploadFileRepository;
		this.bankTransactionRepository = bankTransactionRepository;
		this.dailyReviewService = dailyReviewService;
	}

	@Transactional
	public BusinessUploadResponse save(String originalFilename, String contentSha256, List<ParsedBankRow> rows) {
		if (uploadFileRepository.existsByContentSha256(contentSha256)) {
			throw new DuplicateUploadFileException();
		}
		assertSourceLineIdsAvailable(rows);

		UploadFile uploadFile = uploadFileRepository.save(new UploadFile(
				UploadFileType.BANK,
				originalFilename,
				contentSha256,
				rows.size()
		));
		List<BankTransaction> transactions = new ArrayList<>(rows.size());
		for (ParsedBankRow row : rows) {
			transactions.add(new BankTransaction(
					uploadFile,
					row.sourceLineId(),
					row.sourceRowNumber(),
					row.bookedDate(),
					row.bookedAt(),
					row.direction(),
					row.amount(),
					row.balanceAfter(),
					row.counterpartyName(),
					row.description(),
					row.txnType(),
					row.branchName()
			));
		}
		bankTransactionRepository.saveAll(transactions);
		TreeSet<LocalDate> affectedDates = new TreeSet<>();
		for (ParsedBankRow row : rows) {
			affectedDates.add(row.bookedDate());
		}
		dailyReviewService.markOriginalsChanged(affectedDates);
		return new BusinessUploadResponse(
				uploadFile.getId(),
				uploadFile.getOriginalFilename(),
				uploadFile.getRowCount(),
				uploadFile.getUploadedAt()
		);
	}

	private void assertSourceLineIdsAvailable(List<ParsedBankRow> rows) {
		List<String> ids = rows.stream().map(ParsedBankRow::sourceLineId).toList();
		Set<String> existing = new HashSet<>();
		for (int i = 0; i < ids.size(); i += ID_BATCH_SIZE) {
			List<String> batch = ids.subList(i, Math.min(i + ID_BATCH_SIZE, ids.size()));
			bankTransactionRepository.findBySourceLineIdIn(batch)
					.forEach(tx -> existing.add(tx.getSourceLineId()));
		}
		if (existing.isEmpty()) {
			return;
		}
		CsvFieldErrorBag errors = new CsvFieldErrorBag();
		for (ParsedBankRow row : rows) {
			if (existing.contains(row.sourceLineId())) {
				String message = BankTransactionFingerprint.isReserved(row.sourceLineId())
						? "같은 거래일시·방향·금액·잔액으로 이미 저장된 거래입니다."
						: "이미 저장된 원천 거래 ID입니다.";
				errors.add(row.sourceRowNumber(), "source_line_id", message);
			}
		}
		throw errors.toDuplicateSourceLineIdException();
	}
}
