package com.smallbiz.reconciliation.upload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

final class UploadFileSupport {

	private UploadFileSupport() {
	}

	static byte[] readWithinLimit(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new UploadValidationException(
					List.of(new FieldErrorResponse("file", "업로드할 파일이 없습니다.")),
					false
			);
		}
		if (file.getSize() > BusinessUploadFacade.MAX_FILE_BYTES) {
			throw new PayloadTooLargeException();
		}
		byte[] content;
		try {
			content = file.getBytes();
		}
		catch (Exception ex) {
			throw new UploadValidationException(
					List.of(new FieldErrorResponse("file", "파일을 읽을 수 없습니다.")),
					false
			);
		}
		if (content.length > BusinessUploadFacade.MAX_FILE_BYTES) {
			throw new PayloadTooLargeException();
		}
		return content;
	}

	static String sanitizeFilename(String originalFilename, String fallback) {
		String name = originalFilename == null ? fallback : originalFilename.trim();
		if (name.isEmpty()) {
			name = fallback;
		}
		if (name.length() > BusinessCsvParser.MAX_FILENAME_LENGTH) {
			throw new UploadValidationException(
					List.of(new FieldErrorResponse("file", "파일 이름은 255자 이하여야 합니다.")),
					false
			);
		}
		return name;
	}

	static String sha256Hex(byte[] content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required");
		}
	}
}
