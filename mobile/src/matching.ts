import type { InvoiceBalance, PaymentBalance } from './types';
import { validAmount, validDate } from './format.ts';
export type AllocationLine = { paymentId: string; invoiceId: string; amount: string };
export type AllocationPayload = { lines: AllocationLine[]; reason?: string };
export function allocationPayload(lines: AllocationLine[], reason: string): AllocationPayload {
  if (!lines.length || lines.length > 100 || lines.some(l => !l.paymentId || !l.invoiceId || !validAmount(l.amount))) throw new Error('연결 행은 1~100개, 금액은 1원~1조 원의 정수로 입력해 주세요.');
  if (new Set(lines.map(l => JSON.stringify([l.paymentId, l.invoiceId]))).size !== lines.length) throw new Error('같은 입금과 청구 쌍은 한 번만 담을 수 있어요.');
  const text = reason.trim(); if (Array.from(text).length > 1000) throw new Error('사유는 1,000자 이내로 입력해 주세요.');
  return { lines: lines.map(({ paymentId, invoiceId, amount }) => ({ paymentId, invoiceId, amount })).sort((a, b) => a.paymentId.localeCompare(b.paymentId) || a.invoiceId.localeCompare(b.invoiceId)), ...(text ? { reason: text } : {}) };
}
export function addLine(lines: AllocationLine[], line: AllocationLine): AllocationLine[] {
  if (lines.some(l => l.paymentId === line.paymentId && l.invoiceId === line.invoiceId)) throw new Error('이미 담은 입금·청구예요. 초안에서 금액을 수정해 주세요.');
  const next = [...lines, line]; allocationPayload(next, ''); return next;
}
export function rangePayload(from: string, to: string) {
  if (!validDate(from) || !validDate(to) || from > to || (Date.parse(to) - Date.parse(from)) / 86400000 > 365) throw new Error('실제 날짜를 YYYY-MM-DD로 입력해 주세요. 시작일부터 종료일까지 최대 366일이에요.');
  return { from, to };
}
export type PreviewLine = { line: AllocationLine; payment: PaymentBalance; invoice: InvoiceBalance };
export type Review = { signature: string; lines: PreviewLine[]; checkedAt: string };
export const reviewSignature = (payload: AllocationPayload) => JSON.stringify(payload);
export function canConfirm(payload: AllocationPayload, review: Review | null): boolean {
  return !!review && review.signature === reviewSignature(payload) && review.lines.length === payload.lines.length && payload.lines.every(line => review.lines.some(p => p.line.paymentId === line.paymentId && p.line.invoiceId === line.invoiceId && p.line.amount === line.amount && p.payment.paymentId === line.paymentId && p.invoice.invoiceId === line.invoiceId));
}
export type RunSummary = { id: string; from: string; to: string; createdAt: string; sourceCounts: { paymentCount: number; invoiceCount: number }; dataPresence: string; ruleVersion: string };
export type RunInvoice = { id: string; invoiceNumber: string; counterpartyName: string; amount: string; allocatedAmount: string; outstandingAmount: string; issueDate: string; dueDate: string };
export type RunPayment = { id: string; payerName: string | null; amount: string; allocatedAmount: string; unallocatedAmount: string; bookedDate: string };
export type Candidate = { invoiceId: string; suggestedAmount: string; reasons: string[]; warnings: string[] };
export type Run = RunSummary & { invoices: RunInvoice[]; payments: { payment: RunPayment; status: string; totalCandidateCount: number; candidatesTruncated: boolean; candidates: Candidate[] }[] };
export type Allocation = { id: string; status: string; version: number; lines: AllocationLine[]; reason: string | null; createdAt: string; cancellation: { reason: string; createdAt: string } | null; audit: { eventType: string; reason: string | null; createdAt: string; version: number }[] };
export const evidenceText: Record<string, string> = {
  EXACT_REMAINING_AMOUNT: '저장 당시 양쪽 잔액이 정확히 일치', NORMALIZED_NAME_EXACT: '정규화한 입금자명·거래처명 일치', BOOKED_ON_OR_AFTER_ISSUE_DATE: '청구일 이후 또는 당일 입금',
  REMAINING_AMOUNT_MISMATCH: '잔액 차이 · 부분 연결 검토 필요', PAYER_NAME_MISSING: '입금자 이름 미기재', COUNTERPARTY_NAME_MISSING: '거래처 이름 미기재', NAME_MISMATCH: '입금자명·거래처명 다름', PAYMENT_BEFORE_ISSUE_DATE: '청구일 전 입금', PAYMENT_AFTER_DUE_DATE: '입금 기한 이후 입금',
};
export const presenceText: Record<string, string> = { NO_ELIGIBLE_SOURCES: '이번 범위·잔액 조건에 해당하는 입금과 청구가 없어요.', PAYMENTS_ONLY: '조건에 해당하는 입금만 있어요. 검토할 미수 청구가 없어요.', INVOICES_ONLY: '조건에 해당하는 미수 청구만 있어요. 선택 기간의 미연결 입금이 없어요.', BOTH: '조건에 해당하는 입금과 청구가 모두 있어요.' };
