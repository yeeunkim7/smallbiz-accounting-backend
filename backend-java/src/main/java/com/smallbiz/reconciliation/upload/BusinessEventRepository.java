package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessEventRepository extends JpaRepository<BusinessEvent, Long> {

	List<BusinessEvent> findBySourceLineIdIn(Collection<String> sourceLineIds);

	long countByUploadFileId(Long uploadFileId);

	@EntityGraph(attributePaths = {"vendor", "uploadFile"})
	Page<BusinessEvent> findByExpectedCashDateAndEventTypeIn(
			LocalDate expectedCashDate,
			Collection<BusinessEventType> eventTypes,
			Pageable pageable
	);
}
