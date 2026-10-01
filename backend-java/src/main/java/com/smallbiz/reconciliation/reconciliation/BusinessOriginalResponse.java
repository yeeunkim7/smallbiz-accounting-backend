package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

import com.smallbiz.reconciliation.upload.BusinessEvent;
import com.smallbiz.reconciliation.upload.BusinessEventType;

public record BusinessOriginalResponse(
		Long id,
		String vendorCode,
		String vendorName,
		BusinessEventType eventType,
		long amount,
		LocalDate usageDate,
		LocalDate expectedCashDate,
		String note,
		Long uploadId,
		String sourceLineId,
		int sourceRowNumber
) {

	static BusinessOriginalResponse from(BusinessEvent event) {
		return new BusinessOriginalResponse(
				event.getId(),
				event.getVendor().getVendorCode(),
				event.getVendor().getVendorName(),
				event.getEventType(),
				event.getAmount(),
				event.getUsageDate(),
				event.getExpectedCashDate(),
				event.getNote(),
				event.getUploadFile().getId(),
				event.getSourceLineId(),
				event.getSourceRowNumber()
		);
	}
}
