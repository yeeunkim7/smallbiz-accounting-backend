package com.smallbiz.reconciliation.upload;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessEventRepository extends JpaRepository<BusinessEvent, Long> {

	List<BusinessEvent> findBySourceLineIdIn(Collection<String> sourceLineIds);

	long countByUploadFileId(Long uploadFileId);
}
