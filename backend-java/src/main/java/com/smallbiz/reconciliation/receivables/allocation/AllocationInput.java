package com.smallbiz.reconciliation.receivables.allocation;

import static com.smallbiz.reconciliation.receivables.ReceivablesValidation.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AllocationInput {
    record Line(UUID paymentId, UUID invoiceId, long amount) {}
    record Confirmation(List<Line> lines, String reason) {}
    record Cancellation(UUID batchId, String reason) {}
    static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);
    private AllocationInput() {}

    static Confirmation confirmation(Map<String, Object> body) {
        fields(body, Set.of("lines", "reason"));
        if (!(body.get("lines") instanceof List<?> rows) || rows.isEmpty() || rows.size() > 100)
            throw AllocationException.invalid();
        List<Line> lines = new ArrayList<>();
        Set<List<UUID>> pairs = new HashSet<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row) || !row.keySet().equals(Set.of("paymentId", "invoiceId", "amount")))
                throw AllocationException.invalid();
            var payment = uuid(row.get("paymentId"));
            var invoice = uuid(row.get("invoiceId"));
            if (!pairs.add(List.of(payment, invoice))) throw AllocationException.invalid();
            lines.add(new Line(payment, invoice, money(row.get("amount"))));
        }
        // Line order is immaterial to a command's meaning and normalized idempotency hash.
        lines.sort(Comparator.comparing(Line::paymentId, UUID_ORDER).thenComparing(Line::invoiceId, UUID_ORDER));
        return new Confirmation(List.copyOf(lines), text(body, "reason", 1000, false));
    }
    static Cancellation cancellation(UUID batchId, Map<String, Object> body) {
        fields(body, Set.of("reason"));
        return new Cancellation(batchId, text(body, "reason", 1000, true));
    }
}
