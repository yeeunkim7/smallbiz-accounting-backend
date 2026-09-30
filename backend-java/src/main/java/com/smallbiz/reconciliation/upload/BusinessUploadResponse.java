package com.smallbiz.reconciliation.upload;

import java.time.Instant;

public record BusinessUploadResponse(
		Long uploadId,
		String originalFilename,
		int rowCount,
		Instant uploadedAt
) {
}
