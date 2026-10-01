package com.smallbiz.reconciliation.upload;

import java.time.Instant;

public record UploadFileResponse(
		Long uploadId,
		UploadFileType fileType,
		String originalFilename,
		int rowCount,
		Instant uploadedAt
) {

	static UploadFileResponse from(UploadFile uploadFile) {
		return new UploadFileResponse(
				uploadFile.getId(),
				uploadFile.getFileType(),
				uploadFile.getOriginalFilename(),
				uploadFile.getRowCount(),
				uploadFile.getUploadedAt()
		);
	}
}
