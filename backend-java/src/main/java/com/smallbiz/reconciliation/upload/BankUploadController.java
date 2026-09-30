package com.smallbiz.reconciliation.upload;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class BankUploadController {

	private final BankUploadFacade bankUploadFacade;

	public BankUploadController(BankUploadFacade bankUploadFacade) {
		this.bankUploadFacade = bankUploadFacade;
	}

	@PostMapping(path = "/bank", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public BusinessUploadResponse uploadBankCsv(@RequestParam("file") MultipartFile file) {
		return bankUploadFacade.upload(file);
	}
}
