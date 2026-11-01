import { useEffect, useState } from 'react';
import type { Api } from './api';
import { StaleSession } from './session';
import type { Counterparty, Invoice, InvoiceBalance, Page, Payment, PaymentBalance, Resource, Summary, Usage } from './types';
import { sampleCustomers, sampleInvoiceBalances, sampleInvoices, samplePage, samplePaymentBalances, samplePayments, sampleSummary, sampleUsage } from './sample';
import { koreaToday } from './format';
export type Workspace = { api: Api; prefix: string; sample: boolean };
export const failText = (error: unknown) => error instanceof Error ? error.message : '자료를 불러오지 못했어요.';
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true; setData(null); setError('');
    load().then(value => { if (active) setData(value); }).catch(e => { if (active && !(e instanceof StaleSession)) setError(failText(e)); });
    return () => { active = false; };
  }, [...deps, attempt]);
  return { data, error, retry: () => setAttempt(v => v + 1) };
}
export type LedgerItem = { invoice?: Invoice; payment?: Payment; customer?: Counterparty; invoiceBalance?: InvoiceBalance; paymentBalance?: PaymentBalance };
export async function ledger(w: Workspace, resource: Resource, page: number, size = 10): Promise<Page<LedgerItem>> {
  if (w.sample) return samplePage<LedgerItem>(resource === 'invoices' ? sampleInvoices.map(invoice => ({ invoice, invoiceBalance: sampleInvoiceBalances[invoice.id], customer: sampleCustomers.find(c => c.id === invoice.counterpartyId) })) : resource === 'payments' ? samplePayments.map(payment => ({ payment, paymentBalance: samplePaymentBalances[payment.id] })) : sampleCustomers.map(customer => ({ customer })));
  const result = await w.api.request<Page<Invoice | Payment | Counterparty>>(`${w.prefix}/${resource}?page=${page}&size=${size}`);
  const customers = new Map<string, Promise<Counterparty>>();
  const items = await Promise.all(result.items.map(async item => {
    if (resource === 'invoices') {
      const invoice = item as Invoice;
      if (!customers.has(invoice.counterpartyId)) customers.set(invoice.counterpartyId, w.api.request(`${w.prefix}/counterparties/${invoice.counterpartyId}`));
      const [invoiceBalance, customer] = await Promise.all([w.api.request<InvoiceBalance>(`${w.prefix}/invoices/${invoice.id}/balance?asOf=${koreaToday()}`), customers.get(invoice.counterpartyId)!]);
      return { invoice, invoiceBalance, customer };
    }
    if (resource === 'payments') {
      const payment = item as Payment;
      return { payment, paymentBalance: await w.api.request<PaymentBalance>(`${w.prefix}/payments/${payment.id}/balance`) };
    }
    return { customer: item as Counterparty };
  }));
  return { ...result, items };
}
export const summary = (w: Workspace) => w.sample ? Promise.resolve(sampleSummary) : w.api.request<Summary>(`${w.prefix}/receivables/summary?asOf=${koreaToday()}`);
export const usage = (w: Workspace) => w.sample ? Promise.resolve(sampleUsage) : w.api.request<Usage>(`${w.prefix}/matching-usage`);
export const invoiceDetail = async (w: Workspace, id: string) => {
  if (w.sample) {
    const invoice = sampleInvoices.find(i => i.id === id)!;
    return { invoice, balance: sampleInvoiceBalances[id], customer: sampleCustomers.find(c => c.id === invoice.counterpartyId)! };
  }
  const [invoice, balance] = await Promise.all([w.api.request<Invoice>(`${w.prefix}/invoices/${id}`), w.api.request<InvoiceBalance>(`${w.prefix}/invoices/${id}/balance?asOf=${koreaToday()}`)]);
  const customer = await w.api.request<Counterparty>(`${w.prefix}/counterparties/${invoice.counterpartyId}`);
  return { invoice, balance, customer };
};
