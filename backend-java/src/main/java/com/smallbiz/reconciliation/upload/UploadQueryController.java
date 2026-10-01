package com.smallbiz.reconciliation.upload;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadQueryController {

	private final UploadQueryService uploadQueryService;

	public UploadQueryController(UploadQueryService uploadQueryService) {
		this.uploadQueryService = uploadQueryService;
	}

	@GetMapping
	public UploadFileListResponse list(
			@RequestParam(required = false) UploadFileType fileType,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return uploadQueryService.findPage(fileType, page, size);
	}

	@GetMapping("/{uploadId}")
	public UploadFileResponse get(@PathVariable Long uploadId) {
		return uploadQueryService.findById(uploadId);
	}
}
