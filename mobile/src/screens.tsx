import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import type { Me, Resource, Tenant } from './types';
import { dateLabel, money, statusLabel } from './format';
import { invoiceDetail, ledger, summary, usage, useLoad, type LedgerItem, type Workspace } from './data';
import { Button, color, Empty, Heading, Icon, Loading, MainMoney, Notice, QueryError, s } from './ui';
function InvoiceRow({ item, open }: { item: LedgerItem; open(id: string): void }) {
  const [focused, setFocused] = useState(false);
  const invoice = item.invoice!, balance = item.invoiceBalance!;
  return <Pressable onPress={() => open(invoice.id)} accessibilityRole="button" accessibilityLabel={`${item.customer?.name}, ${invoice.invoiceNumber}, 잔액 ${money(balance.outstandingAmount)}, 상세 보기`} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} style={[s.item, focused && { backgroundColor: color.pale, borderRadius: 8 }]}>
    <View style={s.sectionRow}><View style={{ flex: 1, gap: 4 }}><Text style={s.itemTitle}>{item.customer?.name}</Text><Text style={s.muted}>{invoice.invoiceNumber}</Text></View><Icon name="chevron-forward" size={18} tint={color.muted} /></View>
    <View style={s.sectionRow}><Text style={s.muted}>기한 {dateLabel(invoice.dueDate)}</Text><Text style={s.money}>{money(balance.outstandingAmount)}</Text></View>
    <Text style={[s.badge, balance.overdueAmount !== '0' && styles.overdue]}>{balance.overdueAmount !== '0' ? '기한 지남 · ' : ''}{statusLabel(balance.status)}</Text>
  </Pressable>;
}
export function HomeScreen({ w, refresh, add, open, all }: { w: Workspace; refresh: number; add(r: Resource): void; open(id: string): void; all(): void }) {
  const narrow = useWindowDimensions().width <= 360;
  const q = useLoad(() => summary(w), [refresh]);
  const recent = useLoad(() => ledger(w, 'invoices', 0, 3), [refresh]);
  return <><Heading eyebrow="오늘도 차근차근" title="받을 돈을 한눈에" subtitle="청구와 입금을 기록하고, 남은 금액을 확인하세요." />
    {!q.data ? q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading /> : <>
      <View style={styles.hero}><View style={s.sectionRow}><Text style={styles.heroLabel}>현재 미수 잔액</Text><Icon name="wallet-outline" tint="#B5DBD0" size={25} /></View><View style={{ paddingVertical: 8 }}>{q.data.invoiceCount === 0 ? <Text style={styles.heroAmount}>등록된 청구 없음</Text> : <MainMoney amount={q.data.outstandingAmount} tint={color.white} />}</View><Text style={styles.heroHint}>청구 {q.data.invoiceCount}건 · 현재 연결한 입금 반영</Text><View style={styles.heroLine} /><View style={[styles.heroColumns, narrow && { flexDirection: 'column' }]}><View style={styles.heroColumn}><Text style={styles.heroHint}>기한이 지난 잔액</Text><Text style={styles.heroSmall}>{q.data.invoiceCount === 0 ? '—' : money(q.data.overdueAmount)}</Text></View><View style={[styles.heroColumn, { borderLeftWidth: narrow ? 0 : 1, borderTopWidth: narrow ? 1 : 0, borderColor: '#5B968B', paddingLeft: narrow ? 0 : 20, paddingTop: narrow ? 16 : 0 }]}><Text style={styles.heroHint}>연결 전 입금</Text><Text style={styles.heroSmall}>{q.data.paymentCount === 0 ? '등록 없음' : money(q.data.unallocatedAmount)}</Text></View></View><Text style={styles.heroFoot}>연체 기준일 {dateLabel(q.data.asOf)} · 현재 전체 장부</Text></View>
      {!w.sample ? <View style={styles.actions}><View style={s.grow}><Button title="청구 추가" icon="add-outline" onPress={() => add('invoices')} /></View><View style={s.grow}><Button title="입금 추가" icon="add-outline" secondary onPress={() => add('payments')} /></View></View> : null}
      {q.data.invoiceCount === 0 ? <Empty title="첫 청구부터 시작해 볼까요?" text="거래처를 등록한 뒤 청구를 추가하면 받을 돈이 여기 모여요." action={!w.sample ? <Button title="거래처 등록" secondary onPress={() => add('counterparties')} /> : undefined} /> : null}
    </>}
    <View><View style={s.sectionRow}><Text accessibilityRole="header" style={s.sectionTitle}>최근 청구</Text><Button title="장부 보기" quiet onPress={all} /></View>{recent.data ? recent.data.items.length ? recent.data.items.slice(0, 3).map(item => <InvoiceRow key={item.invoice!.id} item={item} open={open} />) : <Text style={[s.body, { paddingVertical: 20 }]}>아직 등록된 청구가 없어요.</Text> : recent.error ? <QueryError text={recent.error} retry={recent.retry} /> : <Loading />}</View>
    <Notice>입금 등록만으로 청구가 갚아지지는 않아요. 입금 연결 검토는 다음 단계에서 제공해요.</Notice>
  </>;
}
const labels: Record<Resource, string> = { invoices: '청구', payments: '입금', counterparties: '거래처' };
export function LedgerScreen({ w, refresh, add, open }: { w: Workspace; refresh: number; add(r: Resource): void; open(id: string): void }) {
  const [resource, setResource] = useState<Resource>('invoices');
  return <><Heading eyebrow="나의 장부" title="청구와 입금" subtitle="기록한 내역과 현재 연결 상태를 살펴보세요." /><View style={styles.segments}>{(Object.keys(labels) as Resource[]).map(r => <Segment key={r} title={labels[r]} selected={r === resource} press={() => setResource(r)} />)}</View><LedgerPage key={resource} w={w} resource={resource} refresh={refresh} add={add} open={open} /></>;
}
export function Segment({ title, selected, press }: { title: string; selected: boolean; press(): void }) {
  const [focus, setFocus] = useState(false);
  return <Pressable accessibilityRole="tab" accessibilityState={{ selected }} onPress={press} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)} style={[styles.segment, selected && styles.segmentActive, focus && s.focus]}><Text style={[styles.segmentText, selected && { color: color.primary }]}>{title}</Text></Pressable>;
}
function LedgerPage({ w, resource, refresh, add, open }: { w: Workspace; resource: Resource; refresh: number; add(r: Resource): void; open(id: string): void }) {
  const [page, setPage] = useState(0);
  const q = useLoad(() => ledger(w, resource, page), [page, refresh]);
  return <><View style={s.sectionRow}><Text style={s.sectionTitle}>{labels[resource]} 내역</Text>{!w.sample ? <Button title={`${labels[resource]} 추가`} icon="add-outline" secondary onPress={() => add(resource)} /> : null}</View>
    {q.data ? <><View>{q.data.items.length === 0 ? <Empty title={`아직 등록된 ${labels[resource]}${resource === 'payments' ? '이' : resource === 'counterparties' ? '가' : '가'} 없어요`} text={resource === 'invoices' ? '거래처를 선택하고 첫 청구를 기록해 보세요.' : '직접 등록한 자료가 이곳에 표시돼요.'} /> : q.data.items.map((item, i) => item.invoice ? <InvoiceRow key={item.invoice.id} item={item} open={open} /> : item.payment ? <View key={item.payment.id} style={s.item}><View style={s.sectionRow}><Text style={s.itemTitle}>{item.payment.payerName || '입금자 미기재'}</Text><Text style={s.money}>{money(item.payment.amount)}</Text></View><Text style={s.muted}>입금일 {dateLabel(item.payment.bookedDate)}</Text>{item.payment.note ? <Text style={s.body}>{item.payment.note}</Text> : null}<View style={s.sectionRow}><Text style={s.badge}>{statusLabel(item.paymentBalance!.status)}</Text><Text style={s.muted}>미연결 {money(item.paymentBalance!.unallocatedAmount)}</Text></View></View> : <View style={s.item} key={item.customer!.id}><Text style={s.itemTitle}>{item.customer!.name}</Text><Text style={s.muted}>거래처 코드 {item.customer!.code}</Text></View>)}</View><View style={s.pager}><Button title="이전 페이지" secondary disabled={page === 0} onPress={() => setPage(v => v - 1)} /><Text accessibilityLiveRegion="polite" style={s.muted}>{page + 1}페이지</Text><Button title="다음 페이지" secondary disabled={!q.data.hasNext} onPress={() => setPage(v => v + 1)} /></View><Text style={s.muted}>최근 등록순 · 페이지마다 최대 10건{w.sample ? ' · 읽기 전용 샘플' : ''}</Text></> : q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading />}
    {resource === 'payments' ? <Notice>미연결 입금은 아직 청구에 반영되지 않은 금액이에요. 실제 은행 입금 확인을 대신하지 않아요.</Notice> : null}
  </>;
}
export function DetailScreen({ w, id, refresh }: { w: Workspace; id: string; refresh: number }) {
  const q = useLoad(() => invoiceDetail(w, id), [id, refresh]);
  if (!q.data) return q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading />;
  const { invoice, balance, customer } = q.data;
  return <><Heading eyebrow="청구 상세" title={customer.name} subtitle={invoice.invoiceNumber} /><Text style={[s.badge, balance.overdueAmount !== '0' && styles.overdue]}>{balance.overdueAmount !== '0' ? '기한 지남 · ' : ''}{statusLabel(balance.status)}</Text><View style={s.surface}><Text style={s.muted}>현재 남은 금액</Text><View style={{ marginTop: 12 }}><MainMoney amount={balance.outstandingAmount} /></View><View style={[s.divider, { marginVertical: 24 }]} /><AmountLine title="청구한 금액" amount={balance.amount} /><AmountLine title="연결된 입금" amount={balance.allocatedAmount} /><AmountLine title="기한이 지난 잔액" amount={balance.overdueAmount} /></View><View style={s.gap}><MetaLine title="청구일" text={dateLabel(invoice.issueDate)} /><MetaLine title="입금 기한" text={dateLabel(invoice.dueDate)} /><MetaLine title="연체 기준일" text={dateLabel(balance.asOf)} /></View><Notice>현재 확정된 입금 연결을 반영한 잔액이에요. 기준일은 연체 여부를 판단하며 과거 잔액을 뜻하지 않아요. 연결 내역 검토·확정은 다음 단계에서 제공해요.</Notice></>;
}
function AmountLine({ title, amount }: { title: string; amount: string }) { return <View style={[s.sectionRow, { marginBottom: 18 }]}><Text style={s.body}>{title}</Text><Text style={s.money}>{money(amount)}</Text></View>; }
function MetaLine({ title, text }: { title: string; text: string }) { return <View style={s.sectionRow}><Text style={s.body}>{title}</Text><Text style={[s.body, { color: color.text, fontWeight: '600' }]}>{text}</Text></View>; }
export function AccountScreen({ w, me, tenant, changeTenant, logout, loggingOut }: { w: Workspace; me: Me | null; tenant: Tenant; changeTenant(): void; logout(): void; loggingOut: boolean }) {
  const q = useLoad(() => usage(w), []);
  return <><Heading eyebrow="내 계정" title="차근차근, 내 사업장" subtitle="사업장과 이번 달 이용 현황을 확인하세요." /><View style={[s.surface, s.gap]}><View style={s.row}><View style={styles.accountIcon}><Icon name="storefront-outline" size={28} /></View><View style={s.grow}><Text style={s.sectionTitle}>{tenant.name}</Text><Text style={s.muted}>{w.sample ? '합성 자료로 둘러보는 공간' : me?.login}</Text></View></View>{!w.sample && (me?.tenants.length ?? 0) > 1 ? <Button title="사업장 변경" secondary onPress={changeTenant} /> : null}</View>
    <View style={s.gap}><Text style={s.sectionTitle}>이번 달 이용 현황</Text>{q.data ? <View style={[s.surface, s.gap]}><View style={s.sectionRow}><Text style={s.badge}>{q.data.plan}</Text><Text style={s.muted}>{q.data.month} · 서울 기준</Text></View><MetaLine title="정상 완료한 연결 후보 검토" text={`${q.data.completed}회`} /><MetaLine title="이번 달 이용 한도" text={`${q.data.limit}회`} /><MetaLine title="남은 이용 횟수" text={`${q.data.remaining}회`} /><Text style={s.muted}>{w.sample ? '샘플 수치이며 실제 이용량과 관계없어요.' : '현재 사업장에 적용된 이용 현황이에요.'} 직접 등록과 조회는 이 횟수를 사용하지 않아요.</Text></View> : q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading />}</View><Text style={s.body}>이 앱의 기본 업무에는 AI를 사용하지 않아요. 후보 검토와 CSV 가져오기는 다음 단계에서 제공해요.</Text><Button title={w.sample ? '샘플 체험 끝내기' : '로그아웃'} secondary icon="log-out-outline" busy={loggingOut} onPress={logout} /></>;
}
const styles = StyleSheet.create({
  hero: { backgroundColor: '#086255', padding: 26, borderRadius: 22, gap: 12 }, heroLabel: { color: '#E3F3ED', fontSize: 15, fontWeight: '600' }, heroAmount: { color: color.white, fontSize: 36, fontWeight: '800', letterSpacing: -1.1, fontVariant: ['tabular-nums'], paddingVertical: 8 }, heroHint: { color: '#D1E8E0', fontSize: 13, lineHeight: 20 }, heroLine: { height: 1, backgroundColor: '#518F82', marginVertical: 8 }, heroColumns: { flexDirection: 'row', gap: 20 }, heroColumn: { flex: 1, gap: 7 }, heroSmall: { color: color.white, fontSize: 21, fontWeight: '700', fontVariant: ['tabular-nums'] }, heroFoot: { color: '#D1E8E0', fontSize: 11, lineHeight: 18, paddingTop: 10 }, actions: { flexDirection: 'row', gap: 12 },
  overdue: { color: color.attention, backgroundColor: '#FFF0E5' }, segments: { flexDirection: 'row', backgroundColor: '#E7EEEB', borderRadius: 12, padding: 4 }, segment: { flex: 1, minHeight: 46, alignItems: 'center', justifyContent: 'center', borderRadius: 9, borderWidth: 2, borderColor: 'transparent' }, segmentActive: { backgroundColor: color.white }, segmentText: { color: color.muted, fontSize: 15, fontWeight: '700' }, detailMoney: { fontSize: 36, fontWeight: '800', color: color.primary, fontVariant: ['tabular-nums'], marginTop: 12 }, accountIcon: { width: 54, height: 54, borderRadius: 16, backgroundColor: color.pale, alignItems: 'center', justifyContent: 'center' },
});
