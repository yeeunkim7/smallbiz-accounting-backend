package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DailyReviewRepository extends JpaRepository<DailyReview, LocalDate> {

	List<DailyReview> findByReviewDateIn(Collection<LocalDate> dates);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM DailyReview r WHERE r.reviewDate = :date")
	Optional<DailyReview> lockByDate(@Param("date") LocalDate date);
}
