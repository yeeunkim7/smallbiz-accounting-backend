package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReviewService {

	private final DailyReviewRepository dailyReviewRepository;
	private final DailyReviewLockDao dailyReviewLockDao;
	private final ReconciliationAggregationRepository aggregationRepository;

	public DailyReviewService(
			DailyReviewRepository dailyReviewRepository,
			DailyReviewLockDao dailyReviewLockDao,
			ReconciliationAggregationRepository aggregationRepository
	) {
		this.dailyReviewRepository = dailyReviewRepository;
		this.dailyReviewLockDao = dailyReviewLockDao;
		this.aggregationRepository = aggregationRepository;
	}

	@Transactional(readOnly = true)
	public DailyReviewResponse get(LocalDate date) {
		return dailyReviewRepository.findById(date)
				.map(DailyReviewResponse::from)
				.orElseGet(() -> DailyReviewResponse.absent(date));
	}

	@Transactional
	public DailyReviewResponse put(LocalDate date, DailyReviewUpdateRequest request) {
		if (request.status() == ReviewStatus.NEEDS_RECHECK) {
			throw new InvalidReconciliationQueryException("status", "NEEDS_RECHECK는 업로드 처리에서만 설정됩니다.");
		}
		if (!hasCashReconciliationData(date)) {
			throw new InvalidReconciliationQueryException("date", "현금 대사 데이터가 없는 날짜는 검토를 저장할 수 없습니다.");
		}
		if (request.version() < 0) {
			throw new InvalidReconciliationQueryException("version", "버전은 0 이상이어야 합니다.");
		}
		DailyReview review = lockOrCreate(date);
		if (!Objects.equals(request.version(), review.getVersion())) {
			throw new ReviewVersionConflictException();
		}
		review.applyUserUpdate(request.status(), normalizeMemo(request.memo()));
		return DailyReviewResponse.from(review);
	}

	@Transactional
	public void markOriginalsChanged(Collection<LocalDate> dates) {
		TreeSet<LocalDate> ordered = dates.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(TreeSet::new));
		for (LocalDate date : ordered) {
			DailyReview review = lockOrCreate(date);
			review.onOriginalsChanged();
		}
	}

	@Transactional(readOnly = true)
	public Map<LocalDate, ReviewStatus> statusesFor(Collection<LocalDate> dates) {
		if (dates.isEmpty()) {
			return Map.of();
		}
		return dailyReviewRepository.findByReviewDateIn(dates).stream()
				.collect(Collectors.toMap(DailyReview::getReviewDate, DailyReview::getStatus));
	}

	private boolean hasCashReconciliationData(LocalDate date) {
		return !aggregationRepository.findDailyTotals(date, date).isEmpty();
	}

	private DailyReview lockOrCreate(LocalDate date) {
		dailyReviewLockDao.insertPlaceholderIfAbsent(date);
		return dailyReviewRepository.lockByDate(date)
				.orElseThrow(() -> new IllegalStateException("daily_review row missing after insert"));
	}

	private String normalizeMemo(String memo) {
		if (memo == null) {
			return null;
		}
		String trimmed = memo.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
