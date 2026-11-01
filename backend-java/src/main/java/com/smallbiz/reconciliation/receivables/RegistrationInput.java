package com.smallbiz.reconciliation.receivables;

import static com.smallbiz.reconciliation.receivables.ReceivablesValidation.*;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit JSON types: in particular, Jackson may not coerce numeric money into strings. */
final class RegistrationInput {
    private RegistrationInput() {}
    record Counterparty(String code, String name) {}
    record Invoice(UUID counterpartyId, String invoiceNumber, long amount, LocalDate issueDate, LocalDate dueDate) {}
    record Payment(long amount, LocalDate bookedDate, String payerName, String note) {}

    static Counterparty counterparty(Map<String, Object> body) {
        fields(body, Set.of("code", "name"));
        String code = text(body, "code", 32, true);
        if (!code.matches("[A-Za-z0-9_-]{1,32}")) throw ReceivablesException.invalid();
        return new Counterparty(code.toUpperCase(Locale.ROOT), text(body, "name", 100, true));
    }
    static Invoice invoice(Map<String, Object> body) {
        fields(body, Set.of("counterpartyId", "invoiceNumber", "amount", "issueDate", "dueDate"));
        UUID counterparty = uuid(body.get("counterpartyId"));
        LocalDate issue = date(body.get("issueDate")), due = date(body.get("dueDate"));
        if (due.isBefore(issue)) throw ReceivablesException.invalid();
        return new Invoice(counterparty, text(body, "invoiceNumber", 64, true), money(body.get("amount")), issue, due);
    }
    static Payment payment(Map<String, Object> body) {
        fields(body, Set.of("amount", "bookedDate", "payerName", "note"));
        return new Payment(money(body.get("amount")), date(body.get("bookedDate")),
                text(body, "payerName", 100, false), text(body, "note", 1000, false));
    }
    static void key(String key) {
        ReceivablesValidation.key(key);
    }
    static void page(int page, int size) {
        ReceivablesValidation.page(page, size);
    }
}
