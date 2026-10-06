package com.smallbiz.reconciliation.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smallbiz.reconciliation.learn.TransactionDuplicateChecker.InputRow;
import com.smallbiz.reconciliation.upload.BankDirection;

class TransactionDuplicateCheckerTest {

	private static final Instant T1 = Instant.parse("2026-12-28T05:37:51Z");
	private static final Instant T2 = Instant.parse("2026-12-28T05:38:00Z");

	private final TransactionDuplicateChecker checker = new TransactionDuplicateChecker();

	@Test
	void uniqueRowsYieldEmptyResult() {
		TransactionKey a = TransactionKeyHashSetTest.key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		TransactionKey b = TransactionKeyHashSetTest.key(T2, BankDirection.OUT, "가상점", 2000L, 3000L);
		var result = checker.check(List.of(new InputRow(2, a), new InputRow(3, b)));
		assertThat(result.repeatedOccurrenceCount()).isZero();
		assertThat(result.repeatedKeyCount()).isZero();
		assertThat(result.suspectedRows()).isEmpty();
	}

	@Test
	void threeRowsOfSameKeyCountTwoRepeatsAndIncludeFirstRow() {
		TransactionKey key = TransactionKeyHashSetTest.key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		InputRow first = new InputRow(2, key);
		InputRow second = new InputRow(5, TransactionKeyHashSetTest.key(T1, BankDirection.IN, "가상점", 1000L, 5000L));
		InputRow third = new InputRow(8, TransactionKeyHashSetTest.key(T1, BankDirection.IN, "가상점", 1000L, 5000L));
		var result = checker.check(List.of(first, second, third));
		assertThat(result.repeatedOccurrenceCount()).isEqualTo(2);
		assertThat(result.repeatedKeyCount()).isEqualTo(1);
		assertThat(result.suspectedRows()).extracting(InputRow::rowNumber).containsExactly(2, 5, 8);
	}

	@Test
	void multipleRepeatGroupsKeepInputOrder() {
		TransactionKey in = TransactionKeyHashSetTest.key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		TransactionKey unique = TransactionKeyHashSetTest.key(T1, BankDirection.OUT, "가상점", 50L, 4950L);
		TransactionKey out = TransactionKeyHashSetTest.key(T2, BankDirection.OUT, "다른점", 200L, 100L);
		var result = checker.check(List.of(
				new InputRow(2, in),
				new InputRow(3, unique),
				new InputRow(4, out),
				new InputRow(5, in),
				new InputRow(6, out)
		));
		assertThat(result.repeatedOccurrenceCount()).isEqualTo(2);
		assertThat(result.repeatedKeyCount()).isEqualTo(2);
		assertThat(result.suspectedRows()).extracting(InputRow::rowNumber).containsExactly(2, 4, 5, 6);
	}

	@Test
	void laterCheckDoesNotKeepKeysFromEarlierCall() {
		TransactionKey repeated = TransactionKeyHashSetTest.key(T1, BankDirection.IN, "가상점", 1000L, 5000L);
		checker.check(List.of(new InputRow(2, repeated), new InputRow(3, repeated)));
		TransactionKey other = TransactionKeyHashSetTest.key(T2, BankDirection.OUT, "다른점", 9L, 1L);
		var second = checker.check(List.of(new InputRow(2, other)));
		assertThat(second.repeatedOccurrenceCount()).isZero();
		assertThat(second.repeatedKeyCount()).isZero();
		assertThat(second.suspectedRows()).isEmpty();
	}
}
