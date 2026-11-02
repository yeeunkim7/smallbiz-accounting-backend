import type { Invoice, InvoiceBalance, Page, Payment, PaymentBalance, Summary, Counterparty, Usage } from './types';
// Synthetic fixtures are static server-shaped values. No calculation, network, copying or quota.
export const sampleTenant = { id: 'sample', name: '느린날 디자인 · 샘플', plan: 'FREE' };
export const sampleCustomers: Counterparty[] = [{ id: 'sample-c1', code: 'SLOWDAY', name: '느린날 스튜디오' }, { id: 'sample-c2', code: 'GREEN', name: '초록서점' }];
export const sampleInvoices: Invoice[] = [
  { id: 'sample-i1', counterpartyId: 'sample-c1', invoiceNumber: '11월 디자인 작업', amount: '880000', issueDate: '2026-10-20', dueDate: '2026-10-31' },
  { id: 'sample-i2', counterpartyId: 'sample-c2', invoiceNumber: '브랜드 소품 제작', amount: '420000', issueDate: '2026-10-28', dueDate: '2026-11-15' },
];
export const samplePayments: Payment[] = [{ id: 'sample-p1', amount: '300000', bookedDate: '2026-10-30', payerName: '느린날 스튜디오', note: '디자인 작업 일부 입금' }, { id: 'sample-p2', amount: '120000', bookedDate: '2026-11-01', payerName: '초록서점', note: '연결 전 입금' }];
export const sampleSummary: Summary = { asOf: '2026-11-01', allocationState: 'CURRENT', invoiceCount: 2, paymentCount: 2, activeAllocationLineCount: 1, invoiceAmount: '1300000', allocatedAmount: '300000', outstandingAmount: '1000000', overdueAmount: '580000', paymentAmount: '420000', unallocatedAmount: '120000' };
export const sampleInvoiceBalances: Record<string, InvoiceBalance> = {
  'sample-i1': { invoiceId: 'sample-i1', amount: '880000', allocatedAmount: '300000', outstandingAmount: '580000', overdueAmount: '580000', dueDate: '2026-10-31', asOf: '2026-11-01', status: 'PARTIAL' },
  'sample-i2': { invoiceId: 'sample-i2', amount: '420000', allocatedAmount: '0', outstandingAmount: '420000', overdueAmount: '0', dueDate: '2026-11-15', asOf: '2026-11-01', status: 'UNPAID' },
};
export const samplePaymentBalances: Record<string, PaymentBalance> = {
  'sample-p1': { paymentId: 'sample-p1', amount: '300000', allocatedAmount: '300000', unallocatedAmount: '0', status: 'ALLOCATED' },
  'sample-p2': { paymentId: 'sample-p2', amount: '120000', allocatedAmount: '0', unallocatedAmount: '120000', status: 'UNALLOCATED' },
};
export const sampleUsage: Usage = { month: '2026-11', timezone: 'Asia/Seoul', plan: 'FREE', limit: 10, completed: 2, remaining: 8, resetAt: '2026-11-30T15:00:00Z' };
export const samplePage = <T,>(items: T[]): Page<T> => ({ items, page: 0, size: 10, hasNext: false });
