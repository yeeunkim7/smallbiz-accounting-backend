package com.smallbiz.reconciliation.reconciliation;

public class ReviewVersionConflictException extends RuntimeException {

	public ReviewVersionConflictException() {
		super("검토 정보가 변경되었습니다. 다시 조회한 버전으로 저장하세요.");
	}
}
