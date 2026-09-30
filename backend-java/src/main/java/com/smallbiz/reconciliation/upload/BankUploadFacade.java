package com.smallbiz.reconciliation.upload;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BankUploadFacade {

	private final BankCsvParser parser;
	private final BankUploadService bankUploadService;

	public BankUploadFacade(BankCsvParser parser, BankUploadService bankUploadService) {
		this.parser = parser;
		this.bankUploadService = bankUploadService;
	}

	public BusinessUploadResponse upload(MultipartFile file) {
		byte[] content = UploadFileSupport.readWithinLimit(file);
		String originalFilename = UploadFileSupport.sanitizeFilename(file.getOriginalFilename(), "bank.csv");
		String sha256 = UploadFileSupport.sha256Hex(content);
		var rows = parser.parse(content);
		return bankUploadService.save(originalFilename, sha256, rows);
	}
}
