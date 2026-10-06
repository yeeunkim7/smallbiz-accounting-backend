package com.smallbiz.reconciliation.learn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smallbiz.reconciliation.upload.BankDirection;

class TransactionKeyHashSetTest {

	private static final Instant T1 = Instant.parse("2026-12-28T05:37:51Z");
	private static final Instant T2 = Instant.parse("2026-12-28T05:37:52Z");

	@Test
	void emptySetDoesNotContainKey() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		assertThat(set.size()).isZero();
		assertThat(set.contains(key(T1, BankDirection.IN, "가상점", 1000L, 5000L))).isFalse();
	}

	@Test
	void equalValuesOnDifferentInstancesAreDuplicates() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		TransactionKey first = key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		TransactionKey same = key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		assertThat(set.add(first)).isTrue();
		assertThat(set.add(same)).isFalse();
		assertThat(set.size()).isEqualTo(1);
		assertThat(set.contains(same)).isTrue();
	}

	@Test
	void keysDifferingInOneFieldAreDistinct() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		TransactionKey base = key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		assertThat(set.add(base)).isTrue();
		assertThat(set.add(key(T2, BankDirection.IN, "가상점", 1000L, 5000L))).isTrue();
		assertThat(set.add(key(T1, BankDirection.OUT, "가상점", 1000L, 5000L))).isTrue();
		assertThat(set.add(key(T1, BankDirection.IN, "다른점", 1000L, 5000L))).isTrue();
		assertThat(set.add(key(T1, BankDirection.IN, "가상점", 1001L, 5000L))).isTrue();
		assertThat(set.add(key(T1, BankDirection.IN, "가상점", 1000L, 5001L))).isTrue();
		assertThat(set.size()).isEqualTo(6);
	}

	@Test
	void nullBalanceIsDistinctFromZero() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		assertThat(set.add(key(T1, BankDirection.IN, "가상점", 1000L, null))).isTrue();
		assertThat(set.add(key(T1, BankDirection.IN, "가상점", 1000L, 0L))).isTrue();
		assertThat(set.size()).isEqualTo(2);
		assertThat(set.contains(key(T1, BankDirection.IN, "가상점", 1000L, null))).isTrue();
		assertThat(set.contains(key(T1, BankDirection.IN, "가상점", 1000L, 0L))).isTrue();
	}

	@Test
	void collidingDifferentKeysAreStoredAndFound() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		TransactionKey[] pair = collidingPair(set.tableLength());
		assertThat(set.bucketIndex(pair[0])).isEqualTo(set.bucketIndex(pair[1]));
		assertThat(pair[0]).isNotEqualTo(pair[1]);
		assertThat(set.add(pair[0])).isTrue();
		assertThat(set.add(pair[1])).isTrue();
		assertThat(set.size()).isEqualTo(2);
		assertThat(set.chainLength(pair[0])).isGreaterThanOrEqualTo(2);
		assertThat(set.contains(pair[0])).isTrue();
		assertThat(set.contains(pair[1])).isTrue();
	}

	@Test
	void resizeKeepsExistingKeysAndSize() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		int initialLength = set.tableLength();
		List<TransactionKey> keys = new ArrayList<>();
		for (int i = 0; i < 13; i++) {
			TransactionKey key = key(T1, BankDirection.IN, "가상점" + i, 1000L + i, 5000L);
			keys.add(key);
			assertThat(set.add(key)).isTrue();
		}
		assertThat(set.tableLength()).isGreaterThan(initialLength);
		assertThat(set.size()).isEqualTo(13);
		for (TransactionKey key : keys) {
			assertThat(set.contains(key)).isTrue();
		}
	}

	@Test
	void nullKeyIsRejected() {
		TransactionKeyHashSet set = new TransactionKeyHashSet();
		assertThatThrownBy(() -> set.contains(null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> set.add(null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(set.size()).isZero();
	}

	static TransactionKey key(
			Instant bookedAt,
			BankDirection direction,
			String institution,
			long amount,
			Long balanceAfter
	) {
		return new TransactionKey(bookedAt, direction, institution, amount, balanceAfter);
	}

	static TransactionKey[] collidingPair(int tableLength) {
		TransactionKey first = key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		int firstBucket = Math.floorMod(first.hashCode(), tableLength);
		for (int amount = 1001; amount < 1_000_000; amount++) {
			TransactionKey other = key(T1, BankDirection.IN, "가상점", amount, 5000L);
			if (Math.floorMod(other.hashCode(), tableLength) == firstBucket && !first.equals(other)) {
				return new TransactionKey[] {first, other};
			}
		}
		throw new IllegalStateException("같은 버킷의 서로 다른 키를 찾지 못했습니다.");
	}
}
