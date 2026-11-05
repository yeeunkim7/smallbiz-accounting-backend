import React, { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { Api } from './src/api';
import { SessionState, StaleSession } from './src/session';
import { vault } from './src/vault';
import { AuthScreen, blankDraft, createCommand, Registration } from './src/forms';
import { MatchingScreen } from './src/matching-screen';
import { AccountScreen, DetailScreen, HomeScreen, LedgerScreen } from './src/screens';
import { Button, color, Heading, Icon, Loading, Notice, QueryError, s } from './src/ui';
import { failText, type Workspace } from './src/data';
import { sampleTenant } from './src/sample';
import type { Draft, Me, Resource, Tenant } from './src/types';

export default function App() { return <SafeAreaProvider><StatusBar style="dark" /><Main /></SafeAreaProvider>; }
function Main() {
  const insets = useSafeAreaInsets();
  const [booting, setBooting] = useState(true), [authenticated, setAuthenticated] = useState(false), [sample, setSample] = useState(false);
  const [me, setMe] = useState<Me | null>(null), [tenant, setTenant] = useState<Tenant | null>(null);
  const [message, setMessage] = useState(''), [meError, setMeError] = useState(''), [loggingOut, setLoggingOut] = useState(false);
  const sessionRef = useRef<SessionState | null>(null);
  if (!sessionRef.current) sessionRef.current = new SessionState(vault, () => { setAuthenticated(false); setMe(null); setTenant(null); setMessage('로그인 정보가 만료되었어요. 다시 로그인해 주세요.'); });
  const session = sessionRef.current;
  const apiRef = useRef<Api | null>(null);
  if (!apiRef.current) apiRef.current = new Api(process.env.EXPO_PUBLIC_API_URL ?? '', session);
  const api = apiRef.current;
  const loadMe = useCallback(async () => {
    setMeError('');
    try { const value = await api.request<Me>('/auth/me'); setMe(value); if (value.tenants.length === 1) setTenant(value.tenants[0]); }
    catch (e) { if (!(e instanceof StaleSession)) setMeError(failText(e)); }
  }, [api]);
  useEffect(() => {
    let active = true;
    session.restore().then(() => { if (!active) return; if (session.token) { setAuthenticated(true); void loadMe(); } }).catch(() => { if (active) setMessage('기기에 저장한 로그인을 읽지 못했어요. 다시 로그인해 주세요.'); }).finally(() => { if (active) setBooting(false); });
    return () => { active = false; };
  }, [session, loadMe]);
  async function accepted(token: string) {
    try { await session.set(token); }
    catch {
      void api.revoke().catch(() => {}); await session.clear().catch(() => {});
      throw new Error('안전한 로그인 저장을 완료하지 못했어요. 기기 보안 설정을 확인하고 다시 로그인해 주세요.');
    }
    setMessage(''); setMe(null); setTenant(null); setAuthenticated(true); void loadMe();
  }
  async function logout() {
    if (loggingOut) return;
    if (sample) { setSample(false); setMessage('샘플 체험을 마쳤어요. 내 사업장으로 시작해 보세요.'); return; }
    setLoggingOut(true);
    // Capture and dispatch revocation before clearing; lock the UI immediately.
    const revocation = api.revoke();
    const cleared = session.clear();
    setAuthenticated(false); setMe(null); setTenant(null); setMessage('로그아웃했어요.');
    const outcomes = await Promise.allSettled([revocation, cleared]);
    if (outcomes[0].status === 'rejected') setMessage('기기에서는 로그아웃했지만 서버 세션 종료를 확인하지 못했어요. 서버 연결을 확인해 주세요.');
    if (outcomes[1].status === 'rejected') setMessage('기기 저장소의 로그인 삭제를 확인하지 못했어요. 서버 세션 종료 후 기기 보안 설정도 확인해 주세요.');
    setLoggingOut(false);
  }
  const currentTenant = sample ? sampleTenant : tenant;
  const headerName = sample ? '샘플 · 읽기 전용' : currentTenant?.name || '작은 사업의 미수금 장부';
  return <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={s.root}>
    <View style={[s.header, { paddingTop: insets.top }]}><View style={s.headerInner}><View style={s.brand}><View style={s.logo}><Icon name="leaf-outline" tint={color.white} size={23} /></View><View style={{ flexShrink: 1 }}><Text style={s.brandText}>차곡</Text><Text numberOfLines={2} style={s.context}>{headerName}</Text></View></View>{sample ? <Button title="체험 끝내기" quiet onPress={logout} /> : null}</View></View>
    {booting ? <Loading /> : (authenticated || sample) && currentTenant ? <WorkspaceView key={`${sample ? 'sample' : me?.accountId}:${currentTenant.id}`} w={{ api, prefix: `/tenants/${currentTenant.id}`, sample }} me={me} tenant={currentTenant} logout={logout} loggingOut={loggingOut} changeTenant={() => setTenant(null)} /> : <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={[s.scroll, { paddingBottom: 40 + insets.bottom }]}>{authenticated ? !me ? meError ? <QueryError text={meError} retry={loadMe} /> : <Loading /> : <><Heading title="사업장을 선택해 주세요" subtitle="현재 계정에 연결된 사업장이에요." />{me.tenants.length ? me.tenants.map(t => <Button key={t.id} title={t.name} secondary onPress={() => setTenant(t)} />) : <Notice>접근할 수 있는 사업장이 없어요. 계정의 소속을 확인해 주세요.</Notice>}</> : <AuthScreen api={api} accepted={accepted} initialMessage={message} sample={() => { setSample(true); setMessage(''); }} />}{authenticated ? <Button title="로그아웃" secondary onPress={logout} busy={loggingOut} /> : null}</ScrollView>}
  </KeyboardAvoidingView>;
}
type Tab = 'home' | 'ledger' | 'matching' | 'account';
function WorkspaceView({ w, me, tenant, logout, loggingOut, changeTenant }: { w: Workspace; me: Me | null; tenant: Tenant; logout(): void; loggingOut: boolean; changeTenant(): void }) {
  const insets = useSafeAreaInsets();
  const [tab, setTab] = useState<Tab>('home'), [form, setForm] = useState<Resource | null>(null), [detail, setDetail] = useState<string | null>(null);
  const [matchingVisited, setMatchingVisited] = useState(false);
  const [refresh, setRefresh] = useState(0), [notice, setNotice] = useState(''), [mutationBusy, setMutationBusy] = useState(false);
  const [drafts, setDrafts] = useState<Record<Resource, Draft>>(() => ({ invoices: blankDraft(), payments: blankDraft(), counterparties: blankDraft() }));
  const commands = useRef({ invoices: createCommand(), payments: createCommand(), counterparties: createCommand() });
  const scroll = useRef<React.ComponentRef<typeof ScrollView> | null>(null);
  const back = useCallback(() => { if (mutationBusy) return true; if (detail) { setDetail(null); return true; } if (form) { setForm(null); return true; } if (tab !== 'home') { setTab('home'); return true; } return false; }, [detail, form, tab, mutationBusy]);
  useEffect(() => { const sub = BackHandler.addEventListener('hardwareBackPress', back); return () => sub.remove(); }, [back]);
  useEffect(() => { scroll.current?.scrollTo({ y: 0, animated: false }); }, [tab, form, detail]);
  const add = (r: Resource) => { setDetail(null); setForm(r); setNotice(''); };
  const open = (id: string) => { setForm(null); setDetail(id); setNotice(''); };
  const registered = (r: Resource, result: { id: string }) => {
    setDrafts(previous => ({ ...previous, [r]: blankDraft(), ...(r === 'counterparties' ? { invoices: { ...previous.invoices, counterpartyId: result.id } } : {}) }));
    setNotice(`${r === 'invoices' ? '청구를' : r === 'payments' ? '입금을' : '거래처를'} 등록했어요.${r === 'counterparties' ? ' 청구 추가에서 바로 선택할 수 있어요.' : ''}`);
    setForm(null); setDetail(null); setTab('ledger'); setRefresh(v => v + 1);
  };
  return <><ScrollView ref={scroll} keyboardShouldPersistTaps="handled" contentContainerStyle={s.scroll}>
    {form || detail ? <View style={{ alignSelf: 'flex-start' }}><Button title="돌아가기" icon="arrow-back" quiet disabled={mutationBusy} onPress={back} /></View> : null}
    {notice ? <Notice>{notice}</Notice> : null}
    {w.sample ? <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}><Icon name="eye-outline" size={17} /><Text style={[s.muted, { color: color.primary }]}>샘플 체험 · 읽기 전용 · 기준일 2026.11.01</Text></View> : null}
    {form ? <Registration key={form} w={w} resource={form} draft={drafts[form]} update={value => setDrafts(d => ({ ...d, [form]: value }))} command={commands.current[form]} registered={registered} changeResource={add} busyChange={setMutationBusy} /> : detail ? <DetailScreen w={w} id={detail} refresh={refresh} /> : tab === 'home' ? <HomeScreen w={w} refresh={refresh} add={add} open={open} all={() => setTab('ledger')} /> : tab === 'ledger' ? <LedgerScreen w={w} refresh={refresh} add={add} open={open} /> : tab === 'matching' ? null : <AccountScreen refresh={refresh} w={w} me={me} tenant={tenant} changeTenant={changeTenant} logout={logout} loggingOut={loggingOut} />}
    {matchingVisited ? <View style={{ display: tab === 'matching' && !form && !detail ? 'flex' : 'none', gap: 28 }}><MatchingScreen w={w} refresh={refresh} busyChange={setMutationBusy} changed={() => setRefresh(v => v + 1)} /></View> : null}
    </ScrollView><View style={[s.nav, { paddingBottom: Math.max(insets.bottom, 8) }]}><View style={s.navInner}>{(['home', 'ledger', 'matching', 'account'] as Tab[]).map(t => <NavButton key={t} title={{ home: '홈', ledger: '장부', matching: '입금 연결', account: '계정' }[t]} icon={{ home: 'home-outline', ledger: 'albums-outline', matching: 'git-compare-outline', account: 'person-outline' }[t] as React.ComponentProps<typeof Icon>['name']} selected={tab === t && !form && !detail} disabled={mutationBusy} onPress={() => { if (t === 'matching') setMatchingVisited(true); setTab(t); setForm(null); setDetail(null); }} />)}</View></View></>;
}
function NavButton({ title, icon, selected, onPress, disabled }: { title: string; icon: React.ComponentProps<typeof Icon>['name']; selected: boolean; disabled: boolean; onPress(): void }) {
  const [focus, setFocus] = useState(false);
  return <Pressable accessibilityRole="tab" accessibilityState={{ selected, disabled }} disabled={disabled} accessibilityLabel={title} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)} onPress={onPress} style={[s.navButton, disabled && { opacity: .5 }, selected && { backgroundColor: color.pale }, focus && s.focus]}><Icon name={icon} tint={selected ? color.primary : color.muted} /><Text style={[s.navText, selected && { color: color.primary }]}>{title}</Text></Pressable>;
}
