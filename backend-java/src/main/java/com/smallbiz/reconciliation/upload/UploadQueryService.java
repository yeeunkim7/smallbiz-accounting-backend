package com.smallbiz.reconciliation.upload;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadQueryService {

	private static final Sort UPLOAD_SORT = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

	private final UploadFileRepository uploadFileRepository;

	public UploadQueryService(UploadFileRepository uploadFileRepository) {
		this.uploadFileRepository = uploadFileRepository;
	}

	@Transactional(readOnly = true)
	public UploadFileListResponse findPage(UploadFileType fileType, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size, UPLOAD_SORT);
		Page<UploadFile> result = fileType == null
				? uploadFileRepository.findAll(pageRequest)
				: uploadFileRepository.findByFileType(fileType, pageRequest);
		return new UploadFileListResponse(
				result.getContent().stream().map(UploadFileResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public UploadFileResponse findById(Long uploadId) {
		return UploadFileResponse.from(
				uploadFileRepository.findById(uploadId)
						.orElseThrow(() -> new UploadFileNotFoundException(uploadId))
		);
	}
}
