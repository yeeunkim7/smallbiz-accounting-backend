package com.smallbiz.reconciliation.upload;

public class UploadFileNotFoundException extends RuntimeException {

	public UploadFileNotFoundException(Long uploadId) {
		super("업로드 이력을 찾을 수 없습니다.");
	}
}
