package com.smallbiz.reconciliation.receivables.matching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Operational computation bounds, independent of prices, plans and monthly usage. */
@Component
public record MatchingPolicy(int maxPayments, int maxInvoices, int topPerPayment) {
    public MatchingPolicy(
            @Value("${app.receivables.matching.max-payments:200}") int maxPayments,
            @Value("${app.receivables.matching.max-invoices:200}") int maxInvoices,
            @Value("${app.receivables.matching.top-per-payment:5}") int topPerPayment) {
        if (maxPayments < 1 || maxPayments > 1000 || maxInvoices < 1 || maxInvoices > 1000
                || topPerPayment < 1 || topPerPayment > 20)
            throw new IllegalArgumentException("Invalid matching computation limits");
        this.maxPayments = maxPayments; this.maxInvoices = maxInvoices; this.topPerPayment = topPerPayment;
    }
}
