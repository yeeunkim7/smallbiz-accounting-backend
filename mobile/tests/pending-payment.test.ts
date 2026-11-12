import assert from 'node:assert/strict';
import { test } from 'node:test';
import { PendingPaymentRecovery, paymentScopeKey, parsePendingPayment } from '../src/pending-payment.ts';
import type { PaymentInput, PaymentScope, PendingPayment, PendingPaymentStore } from '../src/pending-payment.ts';
const scope: PaymentScope = { origin: 'https://example.test', accountId: 'a', tenantId: 't' };
const input: PaymentInput = { amount: '1000', bookedDate: '2026-11-12', payerName: '고객', note: null };
class Store implements PendingPaymentStore {
  rows = new Map<string, PendingPayment>();
  failRead = false; failCreate = false; failRemove = false;
  async read(s: PaymentScope) { if (this.failRead) throw new Error('secret'); return this.rows.get(paymentScopeKey(s)) ?? null; }
  async create(s: PaymentScope, p: PendingPayment) { if (this.failCreate) throw new Error('secret'); const k = paymentScopeKey(s); if (!this.rows.has(k)) this.rows.set(k, structuredClone(p)); return this.rows.get(k)!; }
  async remove(s: PaymentScope, key: string) { if (this.failRemove) throw new Error('secret'); if (this.rows.get(paymentScopeKey(s))?.key === key) this.rows.delete(paymentScopeKey(s)); }
}
function fixture() {
  const store = new Store(); const records = new Map<string, { id: string }>(); const calls: { key: string; payload: PaymentInput }[] = []; let key = 0; let lose = false;
  const post = async (payload: PaymentInput, id: string) => { calls.push({ key: id, payload: structuredClone(payload) }); assert.ok(store.rows.size); if (!records.has(id)) records.set(id, { id: `record${records.size + 1}` }); if (lose) { lose = false; throw new Error('lost response'); } return records.get(id)!; };
  return { store, records, calls, lose: () => { lose = true; }, manager: (s = scope) => new PendingPaymentRecovery(store, s, () => `key-${++key}`, post) };
}
test('lost response reload has no automatic POST and explicit retry preserves key/body and actual record', async () => {
  const f = fixture(); f.lose(); await assert.rejects(f.manager().submit(input)); assert.equal(f.calls.length, 1);
  const fresh = f.manager(); assert.deepEqual((await fresh.load())?.payload, input); assert.equal(f.calls.length, 1);
  assert.deepEqual(await fresh.retry(), { id: 'record1' }); assert.equal(f.records.size, 1); assert.deepEqual(f.calls[0], f.calls[1]); assert.equal(await fresh.load(), null);
});
test('read and write errors prevent POST without leaking storage detail', async () => {
  for (const flag of ['failRead', 'failCreate'] as const) { const f = fixture(); f.store[flag] = true; await assert.rejects(f.manager().submit(input), e => e instanceof Error && !e.message.includes('secret')); assert.equal(f.calls.length, 0); }
});
test('different body cannot replace pending even after manager recreation', async () => {
  const f = fixture(); f.lose(); await assert.rejects(f.manager().submit(input)); await assert.rejects(f.manager().submit({ ...input, amount: '2000' })); assert.equal(f.calls.length, 1); assert.deepEqual((await f.manager().load())?.payload, input);
});
test('cleanup failure preserves same key for replay and never creates duplicate record', async () => {
  const f = fixture(); f.store.failRemove = true; await assert.rejects(f.manager().submit(input)); assert.ok(await f.manager().load()); f.store.failRemove = false; assert.deepEqual(await f.manager().retry(), { id: 'record1' }); assert.equal(f.records.size, 1); assert.equal(f.calls[0].key, f.calls[1].key); assert.equal(await f.manager().load(), null);
});
test('account tenant and origin independently isolate recovery', async () => {
  const f = fixture(); f.lose(); await assert.rejects(f.manager().submit(input));
  for (const s of [{ ...scope, accountId: 'b' }, { ...scope, tenantId: 'b' }, { ...scope, origin: 'https://other.test' }]) assert.equal(await f.manager(s).load(), null);
  assert.ok(await f.manager().load());
});
test('concurrent managers atomically use stored winner', async () => {
  const f = fixture(); await Promise.all([f.manager().submit(input), f.manager().submit(input)]); assert.equal(f.records.size, 1); assert.equal(f.calls[0].key, f.calls[1].key);
});
test('concurrent different bodies send only winner', async () => {
  const f = fixture(); const results = await Promise.allSettled([f.manager().submit(input), f.manager().submit({ ...input, amount: '2000' })]); assert.equal(results.filter(r => r.status === 'fulfilled').length, 1); assert.equal(f.calls.length, 1);
});
test('corrupt persisted data blocks new submit and retry', async () => {
  for (const bad of [{ version: 2, key: 'k', payload: input }, { version: 1, key: 'k', payload: { ...input, amount: '0' } }, { version: 1, key: 'k', payload: { ...input, extra: 'x' } }]) {
    const f = fixture(); f.store.rows.set(paymentScopeKey(scope), bad as PendingPayment); await assert.rejects(f.manager().submit(input)); await assert.rejects(f.manager().retry()); assert.equal(f.calls.length, 0);
  }
});
test('strict dates money lengths and shape; snapshots frozen against caller mutation', async () => {
  for (const bad of [{ ...input, bookedDate: '2026-02-29' }, { ...input, amount: '1000000000001' }, { ...input, payerName: 'x'.repeat(101) }, { ...input, note: 'x'.repeat(1001) }, { ...input, extra: '' }]) assert.throws(() => parsePendingPayment({ version: 1, key: 'k', payload: bad }));
  const f = fixture(); const mutable = { ...input }; const promise = f.manager().submit(mutable); mutable.amount = '2000'; await promise; assert.equal(f.calls[0].payload.amount, '1000');
  const parsed = parsePendingPayment({ version: 1, key: 'k', payload: input }); assert.ok(Object.isFrozen(parsed)); assert.ok(Object.isFrozen(parsed.payload));
});
test('successful submit awaits removal and empty retry cannot POST', async () => {
  const f = fixture(); assert.deepEqual(await f.manager().submit(input), { id: 'record1' }); assert.equal(f.store.rows.size, 0); await assert.rejects(f.manager().retry()); assert.equal(f.calls.length, 1);
});
test('backend-invalid controls are rejected before durable storage or POST', async () => {
  for (const control of ['\u0000', '\u000d', '\u007f', '\u0085']) {
    const f = fixture();
    await assert.rejects(f.manager().submit({ ...input, note: `note${control}` }));
    assert.equal(f.store.rows.size, 0); assert.equal(f.calls.length, 0);
  }
});
test('valid Unicode text uses code-point limits, not UTF-16 code units', async () => {
  const f = fixture(); await f.manager().submit({ ...input, payerName: '😀'.repeat(100), note: '줄\n탭\t' });
  assert.equal(f.calls.length, 1);
});
