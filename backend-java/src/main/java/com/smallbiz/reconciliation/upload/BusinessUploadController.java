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
public class BusinessUploadController {

	private final BusinessUploadFacade businessUploadFacade;

	public BusinessUploadController(BusinessUploadFacade businessUploadFacade) {
		this.businessUploadFacade = businessUploadFacade;
	}

	@PostMapping(path = "/business", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public BusinessUploadResponse uploadBusinessCsv(@RequestParam("file") MultipartFile file) {
		return businessUploadFacade.upload(file);
	}
}
