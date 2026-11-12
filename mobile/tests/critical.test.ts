import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Api, ApiError, apiOrigin } from '../src/api.ts';
import { CommandKey } from '../src/commands.ts';
import { money, koreaToday, validDate, validAmount } from '../src/format.ts';
import { SessionState, StaleSession } from '../src/session.ts';
const store = () => ({ value: null as string | null, async read() { return this.value; }, async write(value: string) { this.value = value; }, async clear() { this.value = null; } });
const response = (status: number, json: unknown = {}) => new Response(JSON.stringify(json), { status, headers: { 'Content-Type': 'application/json' } });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; }

test('financial key survives uncertain failure, canonical retry, and changes only on new payload or success', async () => {
  let n = 0; const command = new CommandKey(() => `key-${++n}`);
  const session = new SessionState(store(), () => {}); await session.set('session');
  const requests: RequestInit[] = []; let attempts = 0;
  const api = new Api('http://localhost:8091', session, (async (_url, init) => { requests.push(init!); if (++attempts === 1) throw new Error('untrusted transport detail'); return response(201, { id: 'created' }); }) as typeof fetch);
  const body = { amount: '1000', payerName: '고객' };
  await assert.rejects(api.request('/tenants/a/payments', { method: 'POST', body, key: command.forPayload(body) }), (e: unknown) => e instanceof ApiError && !e.message.includes('untrusted'));
  assert.equal(attempts, 1, 'no automatic retry');
  await api.request('/tenants/a/payments', { method: 'POST', body: { ...body }, key: command.forPayload({ ...body }) });
  assert.equal((requests[0].headers as Record<string, string>)['Idempotency-Key'], (requests[1].headers as Record<string, string>)['Idempotency-Key']);
  assert.equal(JSON.parse(requests[0].body as string).amount, '1000');
  assert.notEqual(command.forPayload({ ...body, amount: '2000' }), 'key-1');
  command.succeeded(); assert.equal(command.forPayload(body), 'key-3');
});
test('old successful response is discarded after logout/account change', async () => {
  const session = new SessionState(store(), () => {}); await session.set('old');
  const wait = deferred<Response>();
  const api = new Api('https://example.test', session, (() => wait.promise) as typeof fetch);
  const pending = api.request('/auth/me'); await session.clear(); await session.set('new');
  wait.resolve(response(200, { login: 'old account' })); await assert.rejects(pending, StaleSession); assert.equal(session.token, 'new');
});
test('old 401 cannot expire a new session; current 401 locks and clears', async () => {
  let expired = 0; const storage = store(); const session = new SessionState(storage, () => { expired++; }); await session.set('old');
  const wait = deferred<Response>(); const oldApi = new Api('https://example.test', session, (() => wait.promise) as typeof fetch);
  const pending = oldApi.request('/auth/me'); await session.set('new'); wait.resolve(response(401));
  await assert.rejects(pending, StaleSession); assert.equal(expired, 0); assert.equal(session.token, 'new');
  const api = new Api('https://example.test', session, (async () => response(401, { message: 'raw stack' })) as typeof fetch);
  await assert.rejects(api.request('/auth/me'), StaleSession); assert.equal(expired, 1); assert.equal(session.token, null);
});
test('anonymous login failure is one attempt and never expires an existing session', async () => {
  let attempts = 0, expired = 0; const session = new SessionState(store(), () => { expired++; });
  const api = new Api('https://example.test', session, (async () => { attempts++; return response(401, { message: 'sensitive server output' }); }) as typeof fetch);
  await assert.rejects(api.request('/auth/login', { method: 'POST', anonymous: true, body: { login: 'a', password: 'not-real' } }), (e: unknown) => e instanceof ApiError && !e.message.includes('sensitive'));
  assert.equal(attempts, 1); assert.equal(expired, 0);
});
test('delayed secure-store writes are ordered before logout deletion', async () => {
  const wait = deferred<void>(); const storage = store(); storage.write = async value => { await wait.promise; storage.value = value; };
  const session = new SessionState(storage, () => {}); const set = session.set('old'); const clear = session.clear();
  wait.resolve(); await Promise.all([set, clear]); assert.equal(storage.value, null); assert.equal(session.token, null);
});
test('origin refuses embedded credentials, tokens and paths', () => {
  assert.equal(apiOrigin('https://example.test/'), 'https://example.test');
  for (const value of ['https://user:secret@example.test', 'https://example.test?token=x', 'https://example.test#x', 'https://example.test/api', 'javascript:alert(1)', '']) assert.throws(() => apiOrigin(value));
});
test('money keeps arbitrary precision; real date validation and Seoul day boundary preserve date-only', () => {
  assert.equal(money('100000000000000000001'), '100,000,000,000,000,000,001원');
  assert.equal(money('-12345'), '−12,345원');
  assert.equal(validDate('2024-02-29'), true); assert.equal(validDate('2026-02-29'), false); assert.equal(validDate('0000-01-01'), false); assert.equal(validDate('0001-01-01'), true);
  assert.equal(koreaToday(new Date('2026-10-31T15:01:00Z')), '2026-11-01');
  for (const amount of ['0', '01', '1e3', '1.5', '-1', '1000000000001']) assert.equal(validAmount(amount), false);
  assert.equal(validAmount('1000000000000'), true);
});
test('default fetch preserves browser receiver instead of binding the API instance', async () => {
  const original = globalThis.fetch;
  let called = 0;
  globalThis.fetch = (function (this: unknown) {
    assert.equal(this instanceof Api, false, 'a browser fetch rejects an Api receiver'); called++;
    return Promise.resolve(response(200, { ok: true }));
  }) as typeof fetch;
  try {
    const api = new Api('https://example.test', new SessionState(store(), () => {}));
    assert.deepEqual(await api.request('/auth/me'), { ok: true }); assert.equal(called, 1);
  } finally { globalThis.fetch = original; }
});

test('cart permits split and bundled lines, rejects duplicate pairs and invalid exact decimal strings', async () => {
  const { addLine, allocationPayload } = await import('../src/matching.ts');
  let lines = addLine([], { paymentId: 'p1', invoiceId: 'i1', amount: '1' });
  lines = addLine(lines, { paymentId: 'p1', invoiceId: 'i2', amount: '1000000000000' });
  lines = addLine(lines, { paymentId: 'p2', invoiceId: 'i1', amount: '40' });
  assert.equal(lines.length, 3);
  assert.throws(() => addLine(lines, { paymentId: 'p1', invoiceId: 'i1', amount: '2' }));
  assert.throws(() => allocationPayload([], ''));
  for (const amount of ['0', '01', '1e3', '1.5', '-1', '1000000000001']) assert.throws(() => allocationPayload([{ paymentId: 'p', invoiceId: 'i', amount }], ''));
});
test('allocation intent is canonical across row and object order; uncertain retry and tenant scopes keep separate keys', async () => {
  const { allocationPayload } = await import('../src/matching.ts');
  let n = 0; const first = new CommandKey(() => `a-${++n}`), second = new CommandKey(() => `b-${++n}`);
  const lines = [{ paymentId: 'p2', invoiceId: 'i', amount: '2' }, { paymentId: 'p1', invoiceId: 'i', amount: '1' }];
  const body = allocationPayload(lines, ' 확인 '), reordered = allocationPayload([...lines].reverse(), '확인');
  assert.deepEqual(body, reordered); assert.deepEqual(Object.keys(body).sort(), ['lines', 'reason']);
  assert.equal(first.forPayload(body), first.forPayload({ reason: '확인', lines: reordered.lines }));
  assert.notEqual(first.forPayload(body), second.forPayload(body));
  assert.notEqual(first.forPayload(body), first.forPayload(allocationPayload(lines, '다른 사유')));
  const old = first.forPayload(body); first.succeeded(); assert.notEqual(first.forPayload(body), old);
});
test('confirmation requires current balance review for every unchanged line and reason', async () => {
  const { allocationPayload, canConfirm, reviewSignature } = await import('../src/matching.ts');
  const line = { paymentId: 'p', invoiceId: 'i', amount: '40' }, body = allocationPayload([line], '확인');
  const review = { signature: reviewSignature(body), checkedAt: '2026-11-01T00:00:00Z', lines: [{ line, payment: { paymentId: 'p', amount: '100', allocatedAmount: '0', unallocatedAmount: '100', status: 'UNALLOCATED' as const }, invoice: { invoiceId: 'i', amount: '100', allocatedAmount: '0', outstandingAmount: '100', overdueAmount: '0', dueDate: '2026-11-01', asOf: '2026-11-01', status: 'UNPAID' as const } }] };
  assert.equal(canConfirm(body, null), false); assert.equal(canConfirm(body, review), true);
  assert.equal(canConfirm(allocationPayload([{ ...line, amount: '41' }], '확인'), review), false);
  assert.equal(canConfirm(allocationPayload([line], '변경'), review), false);
  assert.equal(canConfirm(body, { ...review, lines: [] }), false);
  assert.equal(canConfirm(body, { ...review, lines: [{ ...review.lines[0], payment: { ...review.lines[0].payment, paymentId: 'other' } }] }), false);
});
test('date range includes at most 366 actual calendar days and never changes date-only input', async () => {
  const { rangePayload } = await import('../src/matching.ts');
  assert.deepEqual(rangePayload('2024-01-01', '2024-12-31'), { from: '2024-01-01', to: '2024-12-31' });
  for (const [from, to] of [['2024-01-01', '2025-01-01'], ['2026-02-29', '2026-03-01'], ['2026-11-02', '2026-11-01']]) assert.throws(() => rangePayload(from, to));
});
test('quota errors retain server usage and expose safe Korean guidance without retrying or raw server text', async () => {
  const session = new SessionState(store(), () => {}); await session.set('session'); let calls = 0;
  const usage = { month: '2026-11', timezone: 'Asia/Seoul', plan: 'FREE', limit: 10, completed: 10, remaining: 0, resetAt: '2026-11-30T15:00:00Z' };
  const api = new Api('https://example.test', session, (async () => { calls++; return response(429, { code: 'MATCHING_MONTHLY_LIMIT', message: 'raw secret', usage }); }) as typeof fetch);
  await assert.rejects(api.request('/tenants/a/matching-runs', { method: 'POST', body: { from: '2026-11-01', to: '2026-11-01' }, key: 'stable' }), (e: unknown) => e instanceof ApiError && e.usage?.remaining === 0 && e.message.includes('차감되지') && !e.message.includes('raw'));
  assert.equal(calls, 1);
});
test('a stored operation from an old session cannot dispatch under a new account', async () => {
  const session = new SessionState(store(), () => {}); await session.set('old');
  let calls = 0;
  const api = new Api('https://example.test', session, (async () => { calls++; return response(201, { id: 'bad' }); }) as typeof fetch);
  const epoch = api.sessionEpoch;
  await session.clear(); await session.set('new');
  await assert.rejects(api.request('/tenants/old/payments', { method: 'POST', expectedEpoch: epoch, key: 'old-key', body: {} }), StaleSession);
  assert.equal(calls, 0);
});
