package com.smallbiz.reconciliation.receivables;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}")) throw ReceivablesException.invalid();
    }
    static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ReceivablesException.invalid();
    }
    private static void fields(Map<String, Object> body, Set<String> allowed) {
        if (body == null || !allowed.containsAll(body.keySet())) throw ReceivablesException.invalid();
    }
    private static String text(Map<String, Object> body, String field, int max, boolean required) {
        Object raw = body.get(field);
        if (raw == null && !required) return null;
        if (!(raw instanceof String value)) throw ReceivablesException.invalid();
        value = value.strip();
        if (value.isEmpty()) {
            if (required) throw ReceivablesException.invalid();
            return null;
        }
        if (value.codePointCount(0, value.length()) > max || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw ReceivablesException.invalid();
        return value;
    }
    private static long money(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[1-9][0-9]{0,12}")) throw ReceivablesException.invalid();
        long amount = Long.parseLong(value);
        if (amount > 1_000_000_000_000L) throw ReceivablesException.invalid();
        return amount;
    }
    private static LocalDate date(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw ReceivablesException.invalid();
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw ReceivablesException.invalid();
            return date;
        } catch (DateTimeParseException ex) { throw ReceivablesException.invalid(); }
    }
    private static UUID uuid(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw ReceivablesException.invalid();
        return UUID.fromString(value);
    }
}
