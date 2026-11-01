package com.smallbiz.reconciliation.aireview.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import com.smallbiz.reconciliation.reconciliation.InvalidReconciliationQueryException;

final class ReviewQueryValidation {
    private ReviewQueryValidation() { }

    static LocalDate date(String raw, String field) {
        if (raw == null || !raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw invalid(field, "날짜는 YYYY-MM-DD 형식이어야 합니다.");
        }
        try { return LocalDate.parse(raw); }
        catch (DateTimeParseException ex) { throw invalid(field, "실재하는 날짜를 입력해 주세요."); }
    }

    static Range range(String fromRaw, String toRaw) {
        LocalDate from = date(fromRaw, "from");
        LocalDate to = date(toRaw, "to");
        if (from.isAfter(to)) { throw invalid("from", "시작일은 종료일보다 이후일 수 없습니다."); }
        if (ChronoUnit.DAYS.between(from, to) + 1 > 366) {
            throw invalid("to", "조회 기간은 양끝 포함 366일을 넘을 수 없습니다.");
        }
        return new Range(from, to);
    }

    static void page(int page, int size) {
        if (page < 0) { throw invalid("page", "페이지는 0 이상이어야 합니다."); }
        if (size < 1 || size > 100) { throw invalid("size", "페이지 크기는 1~100이어야 합니다."); }
    }

    static String direction(String direction) {
        if (direction == null) { return null; }
        if (!"IN".equals(direction) && !"OUT".equals(direction)) {
            throw invalid("direction", "입출금 방향은 IN 또는 OUT이어야 합니다.");
        }
        return direction;
    }

    static InvalidReconciliationQueryException invalid(String field, String message) {
        return new InvalidReconciliationQueryException(field, message);
    }

    record Range(LocalDate from, LocalDate to) { }
}
