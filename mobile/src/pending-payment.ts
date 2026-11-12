import { validAmount, validDate } from './format.ts';

export type PaymentInput = { amount: string; bookedDate: string; payerName: string | null; note: string | null };
export type PaymentScope = { origin: string; accountId: string; tenantId: string };
export type PendingPayment = { version: 1; key: string; payload: PaymentInput };
export interface PendingPaymentStore {
  read(scope: PaymentScope): Promise<PendingPayment | null>;
  /** Atomically insert only when absent, returning the stored winner. */
  create(scope: PaymentScope, request: PendingPayment): Promise<PendingPayment>;
  /** Remove only when the stored key matches. */
  remove(scope: PaymentScope, key: string): Promise<void>;
}
const invalid = () => new Error('보관된 입금 요청을 확인할 수 없어요. 새 입금을 등록하지 말고 저장 상태를 확인해 주세요.');
function exact(value: unknown, keys: string[]): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}
function validText(value: string, limit: number): boolean {
  return Array.from(value).length <= limit && !/[\u0000-\u0008\u000b-\u001f\u007f-\u009f]/u.test(value);
}
function copyInput(value: unknown, stored = true): PaymentInput {
  if (!exact(value, ['amount', 'bookedDate', 'payerName', 'note'])
    || typeof value.amount !== 'string' || !validAmount(value.amount)
    || typeof value.bookedDate !== 'string' || !validDate(value.bookedDate)
    || !(value.payerName === null || typeof value.payerName === 'string' && validText(value.payerName, 100))
    || !(value.note === null || typeof value.note === 'string' && validText(value.note, 1000))) throw stored ? invalid() : new Error('입금 날짜·금액·입금자명·메모를 확인해 주세요.');
  return Object.freeze({ amount: value.amount, bookedDate: value.bookedDate, payerName: value.payerName, note: value.note });
}
export function parsePendingPayment(value: unknown): PendingPayment {
  if (!exact(value, ['version', 'key', 'payload']) || value.version !== 1
    || typeof value.key !== 'string' || !/^[A-Za-z0-9._:-]{1,100}$/.test(value.key)) throw invalid();
  return Object.freeze({ version: 1, key: value.key, payload: copyInput(value.payload) });
}
export function paymentScopeKey(scope: PaymentScope): string {
  if (!scope || !['origin', 'accountId', 'tenantId'].every(key => typeof scope[key as keyof PaymentScope] === 'string' && scope[key as keyof PaymentScope].length > 0)) throw invalid();
  const url = new URL(scope.origin);
  if (!['https:', 'http:'].includes(url.protocol) || url.origin !== scope.origin) throw invalid();
  return JSON.stringify([scope.origin, scope.accountId, scope.tenantId]);
}
const same = (a: PaymentInput, b: PaymentInput) => JSON.stringify(a) === JSON.stringify(b);
export class PendingPaymentRecovery {
  private readonly scope: PaymentScope;
  private active = false;
  constructor(private readonly store: PendingPaymentStore, scope: PaymentScope,
    private readonly newKey: () => string,
    private readonly post: (payload: PaymentInput, key: string) => Promise<{ id: string }>) {
    paymentScopeKey(scope);
    this.scope = Object.freeze({ ...scope });
  }
  async load(): Promise<PendingPayment | null> {
    let value: PendingPayment | null;
    try { value = await this.store.read(this.scope); }
    catch { throw new Error('보관된 입금 요청을 읽지 못했어요. 저장 상태를 확인한 뒤 다시 시도해 주세요.'); }
    return value === null ? null : parsePendingPayment(value);
  }
  async submit(payload: PaymentInput): Promise<{ id: string }> {
    const copy = copyInput(payload, false);
    return this.run(async () => {
      const existing = await this.load();
      if (existing && !same(existing.payload, copy)) throw new Error('아직 확인되지 않은 입금 요청이 있어요. 보관된 요청을 먼저 다시 전송해 주세요.');
      let pending = existing;
      if (!pending) {
        const candidate = parsePendingPayment({ version: 1, key: this.newKey(), payload: copy });
        try { pending = parsePendingPayment(await this.store.create(this.scope, candidate)); }
        catch { throw new Error('입금 요청을 안전하게 보관하지 못했어요. 저장 상태를 확인한 뒤 다시 시도해 주세요.'); }
      }
      if (!same(pending.payload, copy)) throw new Error('아직 확인되지 않은 다른 입금 요청이 있어요. 보관된 요청을 먼저 다시 전송해 주세요.');
      return this.send(pending);
    });
  }
  async retry(): Promise<{ id: string }> {
    return this.run(async () => {
      const pending = await this.load();
      if (!pending) throw new Error('다시 전송할 입금 요청이 없어요.');
      return this.send(pending);
    });
  }
  private async run(action: () => Promise<{ id: string }>): Promise<{ id: string }> {
    if (this.active) throw new Error('입금 요청을 처리하고 있어요. 잠시 기다려 주세요.');
    this.active = true;
    try { return await action(); } finally { this.active = false; }
  }
  private async send(pending: PendingPayment): Promise<{ id: string }> {
    const result = await this.post(pending.payload, pending.key);
    if (!result || typeof result.id !== 'string' || result.id.length === 0) throw new Error('입금 결과를 확인하지 못했어요. 보관된 요청을 다시 전송해 주세요.');
    try { await this.store.remove(this.scope, pending.key); }
    catch { throw new Error('입금 응답을 받았지만 보관 요청을 정리하지 못했어요. 같은 요청을 다시 전송해 결과를 확인해 주세요.'); }
    return result;
  }
}
