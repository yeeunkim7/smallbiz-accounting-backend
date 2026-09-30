package com.smallbiz.reconciliation.upload;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
		byte[] content = UploadFileSupport.readWithinLimit(file);
		String originalFilename = UploadFileSupport.sanitizeFilename(file.getOriginalFilename(), "business.csv");
		String sha256 = UploadFileSupport.sha256Hex(content);
		var rows = parser.parse(content);
		return businessUploadService.save(originalFilename, sha256, rows);
	}
}
