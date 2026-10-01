package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.reconciliation.common.FieldErrorResponse;
import com.smallbiz.reconciliation.reconciliation.DailyReviewService;
import com.smallbiz.reconciliation.vendor.SettlementType;
import com.smallbiz.reconciliation.vendor.Vendor;
import com.smallbiz.reconciliation.vendor.VendorRepository;

@Service
public class BusinessUploadService {

	private static final int ID_BATCH_SIZE = 500;

	private final UploadFileRepository uploadFileRepository;
	private final BusinessEventRepository businessEventRepository;
	private final VendorRepository vendorRepository;
	private final DailyReviewService dailyReviewService;

	public BusinessUploadService(
			UploadFileRepository uploadFileRepository,
			BusinessEventRepository businessEventRepository,
			VendorRepository vendorRepository,
			DailyReviewService dailyReviewService
	) {
		this.uploadFileRepository = uploadFileRepository;
		this.businessEventRepository = businessEventRepository;
		this.vendorRepository = vendorRepository;
		this.dailyReviewService = dailyReviewService;
	}

	@Transactional
	public BusinessUploadResponse save(String originalFilename, String contentSha256, List<ParsedBusinessRow> rows) {
		if (uploadFileRepository.existsByContentSha256(contentSha256)) {
			throw new DuplicateUploadFileException();
		}
		Map<String, Vendor> vendors = loadVendors(rows);
		validateVendors(rows, vendors);
		assertSourceLineIdsAvailable(rows);

		UploadFile uploadFile = uploadFileRepository.save(new UploadFile(
				UploadFileType.BUSINESS,
				originalFilename,
				contentSha256,
				rows.size()
		));
		List<BusinessEvent> events = new ArrayList<>(rows.size());
		for (ParsedBusinessRow row : rows) {
			events.add(new BusinessEvent(
					vendors.get(row.vendorCode()),
					uploadFile,
					row.sourceLineId(),
					row.sourceRowNumber(),
					row.eventType(),
					row.usageDate(),
					row.expectedCashDate(),
					row.amount(),
					row.note()
			));
		}
		businessEventRepository.saveAll(events);
		TreeSet<LocalDate> affectedDates = new TreeSet<>();
		for (ParsedBusinessRow row : rows) {
			if (row.eventType() != BusinessEventType.USAGE && row.expectedCashDate() != null) {
				affectedDates.add(row.expectedCashDate());
			}
		}
		dailyReviewService.markOriginalsChanged(affectedDates);
		return new BusinessUploadResponse(
				uploadFile.getId(),
				uploadFile.getOriginalFilename(),
				uploadFile.getRowCount(),
				uploadFile.getUploadedAt()
		);
	}

	private Map<String, Vendor> loadVendors(List<ParsedBusinessRow> rows) {
		Set<String> codes = new HashSet<>();
		for (ParsedBusinessRow row : rows) {
			codes.add(row.vendorCode());
		}
		Map<String, Vendor> vendors = new HashMap<>();
		List<String> batch = new ArrayList<>();
		for (String code : codes) {
			batch.add(code);
			if (batch.size() == ID_BATCH_SIZE) {
				vendorRepository.findByVendorCodeIn(batch).forEach(v -> vendors.put(v.getVendorCode(), v));
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			vendorRepository.findByVendorCodeIn(batch).forEach(v -> vendors.put(v.getVendorCode(), v));
		}
		return vendors;
	}

	private void validateVendors(List<ParsedBusinessRow> rows, Map<String, Vendor> vendors) {
		ErrorCollector errors = new ErrorCollector();
		for (ParsedBusinessRow row : rows) {
			Vendor vendor = vendors.get(row.vendorCode());
			if (vendor == null) {
				errors.add(row.sourceRowNumber(), "vendor_code", "등록되지 않은 거래처 코드입니다.");
				continue;
			}
			if (row.eventType() == BusinessEventType.CHARGE_EXPECTED
					&& vendor.getSettlementType() != SettlementType.PREPAID) {
				errors.add(row.sourceRowNumber(), "event_type", "CHARGE_EXPECTED는 선불 거래처만 허용합니다.");
			}
			if (row.eventType() == BusinessEventType.SETTLEMENT_EXPECTED
					&& vendor.getSettlementType() != SettlementType.POSTPAID) {
				errors.add(row.sourceRowNumber(), "event_type", "SETTLEMENT_EXPECTED는 후불 거래처만 허용합니다.");
			}
		}
		if (errors.hasIssues()) {
			throw errors.toValidationException();
		}
	}

	private void assertSourceLineIdsAvailable(List<ParsedBusinessRow> rows) {
		List<String> ids = rows.stream().map(ParsedBusinessRow::sourceLineId).toList();
		Set<String> existing = new HashSet<>();
		for (int i = 0; i < ids.size(); i += ID_BATCH_SIZE) {
			List<String> batch = ids.subList(i, Math.min(i + ID_BATCH_SIZE, ids.size()));
			businessEventRepository.findBySourceLineIdIn(batch)
					.forEach(event -> existing.add(event.getSourceLineId()));
		}
		if (existing.isEmpty()) {
			return;
		}
		ErrorCollector errors = new ErrorCollector();
		for (ParsedBusinessRow row : rows) {
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

		boolean hasIssues() {
			return total > 0;
		}

		UploadValidationException toValidationException() {
			return new UploadValidationException(List.copyOf(items), total > BusinessCsvParser.MAX_ERRORS);
		}

		DuplicateSourceLineIdException toDuplicateSourceLineIdException() {
			return new DuplicateSourceLineIdException(List.copyOf(items), total > BusinessCsvParser.MAX_ERRORS);
		}
	}
}
