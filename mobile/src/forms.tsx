import React, { useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import * as Crypto from 'expo-crypto';
import type { Api } from './api';
import { ApiError } from './api';
import { CommandKey } from './commands';
import { failText, useLoad, type Workspace } from './data';
import { StaleSession } from './session';
import { koreaToday, validAmount, validDate } from './format';
import type { AuthSession, Counterparty, Draft, Page, Resource } from './types';
import { Button, color, Empty, Field, Heading, Icon, Loading, Notice, QueryError, s } from './ui';
export const blankDraft = (): Draft => ({ code: '', name: '', counterpartyId: '', invoiceNumber: '', amount: '', issueDate: koreaToday(), dueDate: koreaToday(), bookedDate: koreaToday(), payerName: '', note: '' });
const amountHint = '원 단위 숫자만 입력 · 최대 1조 원';
export function AuthScreen({ api, accepted, sample, initialMessage }: { api: Api; accepted(token: string): Promise<void>; sample(): void; initialMessage: string }) {
  const [signup, setSignup] = useState(false), [login, setLogin] = useState(''), [password, setPassword] = useState(''), [tenantName, setTenantName] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const locked = useRef(false);
  async function submit() {
    if (locked.current) return;
    if (!/^[a-z0-9][a-z0-9@._+\-]{2,119}$/.test(login.trim().toLowerCase())) { setError('아이디는 영문 소문자·숫자로 시작하는 3~120자예요. @ . _ + - 기호를 사용할 수 있어요.'); return; }
    const bytes = Array.from(password).reduce((n, c) => n + (c.codePointAt(0)! <= 0x7f ? 1 : c.codePointAt(0)! <= 0x7ff ? 2 : c.codePointAt(0)! <= 0xffff ? 3 : 4), 0);
    if (password.length < 12 || bytes > 72) { setError('비밀번호는 12자 이상, 한글 등 다중 바이트 문자를 포함해 72바이트 이내로 입력해 주세요.'); return; }
    if (signup && (!tenantName.trim() || tenantName.trim().length > 100)) { setError('사업장 이름을 1~100자로 입력해 주세요.'); return; }
    locked.current = true; setBusy(true); setError('');
    try {
      const response = await api.request<AuthSession>(signup ? '/auth/signup' : '/auth/login', { method: 'POST', anonymous: true, body: { login: login.trim(), password, ...(signup ? { tenantName: tenantName.trim() } : {}) } });
      await accepted(response.accessToken); setPassword('');
    } catch (e) {
      if (e instanceof StaleSession) return;
      if (e instanceof ApiError && e.status === 401) setError('아이디 또는 비밀번호를 확인해 주세요.');
      else if (signup && e instanceof ApiError && (e.status === 0 || e.status >= 500)) setError('가입 결과를 확인하지 못했어요. 먼저 로그인해 보세요. 로그인되지 않을 때 다시 가입해 주세요.');
      else setError(failText(e));
    } finally { locked.current = false; setBusy(false); }
  }
  return <View style={{ gap: 28, maxWidth: 520, width: '100%', alignSelf: 'center' }}><View style={{ gap: 18, paddingVertical: 8 }}><Text style={s.eyebrow}>작은 사업의 든든한 장부</Text><Text style={[s.title, { fontSize: 38, lineHeight: 49 }]}>받을 돈은 또렷하게,{`\n`}하루는 가볍게.</Text><Text style={s.subtitle}>흩어진 청구와 입금 기록을 한곳에.{`\n`}남은 금액을 차근차근 확인하세요.</Text><View style={[s.row, { paddingVertical: 8 }]}>{['간편한 직접 기록', '고객별 미수 확인'].map(t => <View key={t} style={{ flexDirection: 'row', gap: 5, alignItems: 'center' }}><Icon name="checkmark-circle" size={17} /><Text style={[s.muted, { color: color.primary }]}>{t}</Text></View>)}</View></View>
    <View style={{ gap: 10 }}><Button title="샘플 체험" secondary icon="sparkles-outline" disabled={busy} onPress={sample} /><Text style={[s.muted, { textAlign: 'center', fontSize: 12 }]}>가입 없이 둘러보기 · 읽기 전용 합성 자료</Text></View>
    <View style={[s.surface, s.gap]}><Text style={s.sectionTitle}>{signup ? '내 사업장 시작하기' : '다시 만나 반가워요'}</Text>{initialMessage ? <Notice>{initialMessage}</Notice> : null}{error ? <Notice error>{error}</Notice> : null}<Field label="아이디" value={login} onChange={setLogin} autoComplete="username" disabled={busy} hint="이메일 형식도 사용할 수 있어요. 이메일 인증은 제공하지 않아요." maxLength={120} /><Field label="비밀번호" value={password} onChange={setPassword} secret autoComplete={signup ? 'new-password' : 'current-password'} disabled={busy} hint={signup ? '12자 이상 · 72바이트 이내' : undefined} />{signup ? <Field label="사업장 이름" value={tenantName} onChange={setTenantName} maxLength={100} disabled={busy} /> : null}<Button title={signup ? '가입하고 시작하기' : '로그인'} onPress={submit} busy={busy} /><Button title={signup ? '이미 계정이 있어요 · 로그인' : '처음이신가요? · 회원가입'} quiet disabled={busy} onPress={() => { setSignup(v => !v); setError(''); }} /></View>
</View>;
}
export function Registration({ w, resource, draft, update, command, registered, changeResource, busyChange }: { w: Workspace; resource: Resource; draft: Draft; update(value: Draft): void; command: CommandKey; registered(r: Resource, result: { id: string }): void; changeResource(r: Resource): void; busyChange(value: boolean): void }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const locked = useRef(false);
  const set = (key: keyof Draft, value: string) => { update({ ...draft, [key]: value }); };
  const field = (key: keyof Draft, label: string, extra: Partial<React.ComponentProps<typeof Field>> = {}) => <Field label={label} value={draft[key]} onChange={v => set(key, v)} disabled={busy} {...extra} />;
  const name = resource === 'invoices' ? '청구' : resource === 'payments' ? '입금' : '거래처';
  async function submit() {
    if (locked.current) return;
    let payload: unknown;
    if (resource === 'counterparties') {
      if (!/^[a-z0-9_-]{1,32}$/i.test(draft.code.trim()) || !draft.name.trim() || Array.from(draft.name.trim()).length > 100) { setError('거래처 이름과 코드를 확인해 주세요. 코드는 영문·숫자·_·- 1~32자예요.'); return; }
      payload = { code: draft.code.trim().toUpperCase(), name: draft.name.trim() };
    } else {
      if (!validAmount(draft.amount)) { setError('금액은 1원~1조 원 사이의 정수로 입력해 주세요. 쉼표, 공백과 소수점은 제외해 주세요.'); return; }
      if (resource === 'invoices') {
        if (!draft.counterpartyId || !draft.invoiceNumber.trim() || Array.from(draft.invoiceNumber.trim()).length > 64) { setError('거래처를 선택하고 청구 이름·번호를 입력해 주세요.'); return; }
        if (!validDate(draft.issueDate) || !validDate(draft.dueDate) || draft.dueDate < draft.issueDate) { setError('실제 날짜를 YYYY-MM-DD로 입력해 주세요. 입금 기한은 청구일과 같거나 이후여야 해요.'); return; }
        payload = { counterpartyId: draft.counterpartyId, invoiceNumber: draft.invoiceNumber.trim(), amount: draft.amount, issueDate: draft.issueDate, dueDate: draft.dueDate };
      } else {
        if (!validDate(draft.bookedDate)) { setError('입금일은 실제 날짜를 YYYY-MM-DD로 입력해 주세요.'); return; }
        payload = { amount: draft.amount, bookedDate: draft.bookedDate, payerName: draft.payerName.trim() || null, note: draft.note.trim() || null };
      }
    }
    locked.current = true; setBusy(true); busyChange(true); setError('');
    try {
      const result = await w.api.request<{ id: string }>(`${w.prefix}/${resource}`, { method: 'POST', body: payload, key: command.forPayload(payload) });
      command.succeeded(); registered(resource, result);
    } catch (e) {
      if (e instanceof StaleSession) return;
      const uncertain = e instanceof ApiError && (e.status === 0 || e.status >= 500);
      setError(`${failText(e)}${uncertain ? ' 등록되었을 수도 있어요. 내용을 바꾸지 않고 다시 누르면 같은 요청으로 확인해요.' : ''}`);
    } finally { locked.current = false; setBusy(false); busyChange(false); }
  }
  return <View style={{ gap: 24, maxWidth: 600, width: '100%', alignSelf: 'center' }}><Heading eyebrow="직접 기록" title={`${name} 추가`} subtitle={resource === 'invoices' ? '고객에게 청구한 금액과 입금 기한을 기록하세요.' : resource === 'payments' ? '받은 입금을 기록해 두세요. 청구 연결은 별도예요.' : '청구를 기록할 거래처를 먼저 등록하세요.'} />{error ? <Notice error>{error}</Notice> : null}
    {resource === 'counterparties' ? <>{field('name', '거래처 이름', { maxLength: 100 })}{field('code', '거래처 코드', { maxLength: 32, hint: '거래처를 구분할 짧은 이름 · 예: GREEN_01 (영문·숫자·_·-)' })}</> : <>
      {resource === 'invoices' ? <><CustomerPicker w={w} selected={draft.counterpartyId} choose={id => set('counterpartyId', id)} disabled={busy} /><Button title="새 거래처 등록" quiet icon="add-outline" disabled={busy} onPress={() => changeResource('counterparties')} />{field('invoiceNumber', '청구 이름·번호', { maxLength: 64, hint: '중복 없이 구분할 수 있는 이름 · 예: 11월 디자인 작업' })}</> : null}
      {field('amount', '금액 (원)', { numeric: true, maxLength: 13, hint: amountHint })}
      {resource === 'invoices' ? <>{field('issueDate', '청구일', { hint: 'YYYY-MM-DD · 예: 2026-11-01', maxLength: 10 })}{field('dueDate', '입금 기한', { hint: 'YYYY-MM-DD · 청구일 이후 날짜', maxLength: 10 })}</> : <>{field('bookedDate', '입금일', { hint: 'YYYY-MM-DD · 직접 확인한 날짜', maxLength: 10 })}{field('payerName', '입금자 이름 (선택)', { maxLength: 100 })}{field('note', '메모 (선택)', { maxLength: 1000, multiline: true })}<Notice>입금 등록은 청구 상환이나 은행 확인을 뜻하지 않아요. 연결 전 입금으로 보관해요.</Notice></>}
    </>}
    <Button title={`${name} 등록하기`} onPress={submit} busy={busy} /><Text style={s.muted}>입력 내용은 화면을 이동해도 유지돼요. 로그아웃하거나 사업장을 바꾸면 지워져요.</Text></View>;
}
function CustomerPicker({ w, selected, choose, disabled }: { w: Workspace; selected: string; choose(id: string): void; disabled: boolean }) {
  const [page, setPage] = useState(0);
  const q = useLoad(() => w.api.request<Page<Counterparty>>(`${w.prefix}/counterparties?page=${page}&size=10`), [page]);
  const selectedQ = useLoad(() => selected ? w.api.request<Counterparty>(`${w.prefix}/counterparties/${selected}`) : Promise.resolve(null), [selected]);
  return <View style={s.gap}><Text style={s.label}>거래처 선택</Text>{selected ? <Text accessibilityLiveRegion="polite" style={s.badge}>{selectedQ.data ? `선택: ${selectedQ.data.name}` : '거래처 선택됨'}</Text> : null}{q.data ? q.data.items.length ? <><View style={{ borderWidth: 1, borderColor: color.border, borderRadius: 12, overflow: 'hidden' }}>{q.data.items.map(c => <CustomerChoice key={c.id} customer={c} selected={selected === c.id} disabled={disabled} choose={() => choose(c.id)} />)}</View><View style={s.pager}><Button title="이전 거래처" disabled={page === 0 || disabled} secondary onPress={() => setPage(v => v - 1)} /><Text style={s.muted}>{page + 1}페이지</Text><Button title="다음 거래처" disabled={!q.data.hasNext || disabled} secondary onPress={() => setPage(v => v + 1)} /></View></> : <Empty title="등록된 거래처가 없어요" text="아래 새 거래처 등록을 먼저 눌러 주세요." /> : q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading />}</View>;
}
function CustomerChoice({ customer, selected, disabled, choose }: { customer: Counterparty; selected: boolean; disabled: boolean; choose(): void }) {
  const [focus, setFocus] = useState(false);
  return <Pressable accessibilityRole="radio" accessibilityState={{ checked: selected, disabled }} disabled={disabled} onPress={choose} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)} style={[{ padding: 14, minHeight: 54, borderWidth: 2, borderColor: focus ? color.attention : 'transparent', backgroundColor: selected ? color.pale : color.white, flexDirection: 'row', alignItems: 'center', gap: 10 }]}><Icon name={selected ? 'radio-button-on' : 'radio-button-off'} /><Text style={[s.body, { color: color.text, flex: 1 }]}>{customer.name}</Text></Pressable>;
}
export const createCommand = () => new CommandKey(() => Crypto.randomUUID());
