package com.smallbiz.reconciliation.upload;

public class DuplicateUploadFileException extends RuntimeException {

	public DuplicateUploadFileException() {
		super("이미 업로드된 파일입니다.");
	}
}
