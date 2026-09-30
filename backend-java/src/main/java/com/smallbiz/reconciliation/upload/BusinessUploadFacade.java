package com.smallbiz.reconciliation.upload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

@Service
public class BusinessUploadFacade {

	static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

	private final BusinessCsvParser parser;
	private final BusinessUploadService businessUploadService;

	public BusinessUploadFacade(BusinessCsvParser parser, BusinessUploadService businessUploadService) {
		this.parser = parser;
		this.businessUploadService = businessUploadService;
	}

	public BusinessUploadResponse upload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new UploadValidationException(
					List.of(new FieldErrorResponse("file", "업로드할 파일이 없습니다.")),
					false
			);
		}
		if (file.getSize() > MAX_FILE_BYTES) {
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
		if (content.length > MAX_FILE_BYTES) {
			throw new PayloadTooLargeException();
		}
		String originalFilename = sanitizeFilename(file.getOriginalFilename());
		String sha256 = sha256Hex(content);
		var rows = parser.parse(content);
		return businessUploadService.save(originalFilename, sha256, rows);
	}

	private String sanitizeFilename(String originalFilename) {
		String name = originalFilename == null ? "business.csv" : originalFilename.trim();
		if (name.isEmpty()) {
			name = "business.csv";
		}
		if (name.length() > BusinessCsvParser.MAX_FILENAME_LENGTH) {
			throw new UploadValidationException(
					List.of(new FieldErrorResponse("file", "파일 이름은 255자 이하여야 합니다.")),
					false
			);
		}
		return name;
	}

	private String sha256Hex(byte[] content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required");
		}
	}
}
