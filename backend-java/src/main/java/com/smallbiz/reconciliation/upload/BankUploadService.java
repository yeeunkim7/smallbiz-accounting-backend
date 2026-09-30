package com.smallbiz.reconciliation.upload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

@Service
public class BankUploadService {

	private static final int ID_BATCH_SIZE = 500;

	private final UploadFileRepository uploadFileRepository;
	private final BankTransactionRepository bankTransactionRepository;

	public BankUploadService(
			UploadFileRepository uploadFileRepository,
			BankTransactionRepository bankTransactionRepository
	) {
		this.uploadFileRepository = uploadFileRepository;
		this.bankTransactionRepository = bankTransactionRepository;
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
					row.direction(),
					row.amount(),
					row.counterpartyName(),
					row.description()
			));
		}
		bankTransactionRepository.saveAll(transactions);
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
		ErrorCollector errors = new ErrorCollector();
		for (ParsedBankRow row : rows) {
			if (existing.contains(row.sourceLineId())) {
				errors.add(row.sourceRowNumber(), "source_line_id", "이미 저장된 원천 거래 ID입니다.");
			}
		}
		throw errors.toDuplicateSourceLineIdException();
	}

	private static final class ErrorCollector {
		private final List<FieldErrorResponse> items = new ArrayList<>();
		private int total;

		void add(Integer rowNumber, String field, String message) {
			total++;
			if (items.size() < BusinessCsvParser.MAX_ERRORS) {
				items.add(new FieldErrorResponse(rowNumber, field, message));
			}
		}

		DuplicateSourceLineIdException toDuplicateSourceLineIdException() {
			return new DuplicateSourceLineIdException(List.copyOf(items), total > BusinessCsvParser.MAX_ERRORS);
		}
	}
}
