package com.smallbiz.reconciliation.upload;

import java.util.List;

public record UploadFileListResponse(
		List<UploadFileResponse> content,
		int page,
		int size,
		long totalElements
) {
}
