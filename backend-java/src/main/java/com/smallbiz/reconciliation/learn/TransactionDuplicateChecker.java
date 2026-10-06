package com.smallbiz.reconciliation.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 한 CSV의 행 목록에서 같은 비교 키의 최초 행과 반복 행을 모두 찾는다. */
public final class TransactionDuplicateChecker {

	public Result check(List<InputRow> rows) {
		Objects.requireNonNull(rows, "rows");
		List<InputRow> input = List.copyOf(rows);
		TransactionKeyHashSet seenKeys = new TransactionKeyHashSet();
		TransactionKeyHashSet repeatedKeys = new TransactionKeyHashSet();
		int repeatedOccurrences = 0;

		for (InputRow row : input) {
			if (!seenKeys.add(row.key())) {
				repeatedOccurrences++;
				repeatedKeys.add(row.key());
			}
		}

		List<InputRow> suspectedRows = new ArrayList<>();
		for (InputRow row : input) {
			if (repeatedKeys.contains(row.key())) {
				suspectedRows.add(row);
			}
		}

		return new Result(repeatedOccurrences, repeatedKeys.size(), suspectedRows);
	}

	/** rowNumber는 파서에서 얻은 실제 CSV 행 번호를 전달한다. */
	public record InputRow(int rowNumber, TransactionKey key) {
		public InputRow {
			if (rowNumber < 1) {
				throw new IllegalArgumentException("행 번호는 1 이상이어야 합니다.");
			}
			Objects.requireNonNull(key, "key");
		}
	}

	public record Result(
			int repeatedOccurrenceCount,
			int repeatedKeyCount,
			List<InputRow> suspectedRows) {
		public Result {
			suspectedRows = List.copyOf(suspectedRows);
		}
	}
}
