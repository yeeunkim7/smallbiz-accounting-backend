package com.smallbiz.reconciliation.receivables;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared strict input contract for registration and manual allocation. */
public final class ReceivablesValidation {
    private ReceivablesValidation() {}
    public static void fields(Map<String, Object> body, Set<String> allowed) {
        if (body == null || !allowed.containsAll(body.keySet())) throw ReceivablesException.invalid();
    }
    public static String text(Map<String, Object> body, String field, int max, boolean required) {
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
    public static long money(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[1-9][0-9]{0,12}")) throw ReceivablesException.invalid();
        long amount = Long.parseLong(value);
        if (amount > 1_000_000_000_000L) throw ReceivablesException.invalid();
        return amount;
    }
    public static LocalDate date(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw ReceivablesException.invalid();
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw ReceivablesException.invalid();
            return date;
        } catch (DateTimeParseException ex) { throw ReceivablesException.invalid(); }
    }
    public static UUID uuid(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw ReceivablesException.invalid();
        return UUID.fromString(value);
    }
    public static void key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}")) throw ReceivablesException.invalid();
    }
    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ReceivablesException.invalid();
    }
}
