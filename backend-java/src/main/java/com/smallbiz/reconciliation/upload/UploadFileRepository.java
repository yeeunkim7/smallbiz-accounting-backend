package com.smallbiz.reconciliation.upload;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {

	boolean existsByContentSha256(String contentSha256);

	Page<UploadFile> findByFileType(UploadFileType fileType, Pageable pageable);
}
