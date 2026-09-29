package com.smallbiz.reconciliation.common;

import java.util.List;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.smallbiz.reconciliation.vendor.DuplicateVendorCodeException;
import com.smallbiz.reconciliation.vendor.VendorNotFoundException;

import jakarta.validation.ConstraintViolation;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String VENDOR_CODE_UNIQUE = "uk_vendor_vendor_code";
	private static final String VENDOR_SETTLEMENT_TYPE_CHECK = "ck_vendor_settlement_type";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		List<FieldErrorResponse> fieldErrors = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(this::toFieldError)
				.toList();
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다.", fieldErrors));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다."));
	}

	@ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(
			jakarta.validation.ConstraintViolationException ex
	) {
		List<FieldErrorResponse> fieldErrors = ex.getConstraintViolations()
				.stream()
				.map(this::toFieldError)
				.toList();
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다.", fieldErrors));
	}

	@ExceptionHandler({
			HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<ErrorResponse> handleUnreadableMessage(Exception ex) {
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다."));
	}

	@ExceptionHandler(VendorNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleVendorNotFound(VendorNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of(ErrorCode.VENDOR_NOT_FOUND, ex.getMessage()));
	}

	@ExceptionHandler(DuplicateVendorCodeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateVendorCode(DuplicateVendorCodeException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of(ErrorCode.DUPLICATE_VENDOR_CODE, ex.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		if (constraintNameContains(ex, VENDOR_CODE_UNIQUE)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(ErrorResponse.of(ErrorCode.DUPLICATE_VENDOR_CODE, "이미 사용 중인 거래처 코드입니다."));
		}
		if (constraintNameContains(ex, VENDOR_SETTLEMENT_TYPE_CHECK)) {
			return ResponseEntity.badRequest()
					.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다."));
		}
		log.warn("Unhandled data integrity violation", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of(ErrorCode.DATA_INTEGRITY_VIOLATION, "데이터를 저장할 수 없습니다."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "요청을 처리할 수 없습니다."));
	}

	private FieldErrorResponse toFieldError(FieldError fieldError) {
		return new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
	}

	private FieldErrorResponse toFieldError(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath().toString();
		int lastDot = path.lastIndexOf('.');
		String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
		return new FieldErrorResponse(field, violation.getMessage());
	}

	private boolean constraintNameContains(DataIntegrityViolationException ex, String constraintName) {
		String expected = constraintName.toLowerCase(Locale.ROOT);
		for (Throwable current = ex; current != null; current = current.getCause()) {
			if (current instanceof ConstraintViolationException hibernateEx) {
				String name = hibernateEx.getConstraintName();
				if (name != null && name.toLowerCase(Locale.ROOT).contains(expected)) {
					return true;
				}
			}
			String message = current.getMessage();
			if (message != null && message.toLowerCase(Locale.ROOT).contains(expected)) {
				return true;
			}
		}
		return false;
	}
}
