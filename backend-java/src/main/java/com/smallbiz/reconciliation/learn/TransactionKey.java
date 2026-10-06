package com.smallbiz.reconciliation.learn;

import java.time.Instant;
import java.util.Objects;

import com.smallbiz.reconciliation.upload.BankDirection;

/**
 * 한 CSV 안에서 반복 의심을 가리기 위한 비교 키.
 * 은행 고유 거래번호가 아니며, 업로드·DB 저장 경로에는 연결하지 않는다.
 */
public final class TransactionKey {

	private final Instant bookedAt;
	private final BankDirection direction;
	private final String institution;
	private final long amount;
	private final Long balanceAfter;

	public TransactionKey(
			Instant bookedAt,
			BankDirection direction,
			String institution,
			long amount,
			Long balanceAfter
	) {
		this.bookedAt = bookedAt;
		this.direction = Objects.requireNonNull(direction, "direction");
		this.institution = institution;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
	}

	public Instant bookedAt() {
		return bookedAt;
	}

	public BankDirection direction() {
		return direction;
	}

	public String institution() {
		return institution;
	}

	public long amount() {
		return amount;
	}

	public Long balanceAfter() {
		return balanceAfter;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TransactionKey that)) {
			return false;
		}
		return amount == that.amount
				&& Objects.equals(bookedAt, that.bookedAt)
				&& direction == that.direction
				&& Objects.equals(institution, that.institution)
				&& Objects.equals(balanceAfter, that.balanceAfter);
	}

	@Override
	public int hashCode() {
		return Objects.hash(bookedAt, direction, institution, amount, balanceAfter);
	}
}
