import React, { useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { ApiError } from './api';
import { CommandKey } from './commands';
import { createCommand } from './forms';
import { failText, invoiceDetail, ledger, usage, useLoad, type Workspace, type LedgerItem } from './data';
import { dateLabel, koreaToday, money, validAmount } from './format';
import { addLine, allocationPayload, canConfirm, evidenceText, presenceText, rangePayload, reviewSignature, type Allocation, type AllocationLine, type Review, type Run, type RunSummary } from './matching';
import { StaleSession } from './session';
import type { InvoiceBalance, Page, Payment, PaymentBalance, Usage } from './types';
import { Button, Empty, Field, Heading, Loading, Notice, QueryError, s } from './ui';

type CancelMemory = Record<string, { command: CommandKey; reason: string }>;
type Props = { w: Workspace; refresh: number; busyChange(value: boolean): void; changed(): void };
export function MatchingScreen(props: Props) {
  if (props.w.sample) return <><Heading eyebrow="읽기 전용 샘플" title="입금 연결" subtitle="실제 사업장에서 후보를 검토하고 직접 연결할 수 있어요." /><Notice>샘플은 합성 장부를 읽기만 해요. 후보 생성·연결 확정·취소와 실제 API·사용량을 사용하지 않아요.</Notice></>;
  return <LiveMatching {...props} />;
}
function LiveMatching({ w, refresh, busyChange, changed }: Props) {
  const [section, setSection] = useState<'review' | 'history'>('review');
  const [from, setFrom] = useState(koreaToday()), [to, setTo] = useState(koreaToday());
  const [run, setRun] = useState<Run | null>(null), [runPage, setRunPage] = useState(0), [revision, setRevision] = useState(0);
  const [lines, setLines] = useState<AllocationLine[]>([]), [reason, setReason] = useState(''), [review, setReview] = useState<Review | null>(null);
  const [labels, setLabels] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false), [reading, setReading] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [limitUsage, setLimitUsage] = useState<Usage | null>(null), [result, setResult] = useState<string | null>(null);
  const [manual, setManual] = useState(false);
  const locked = useRef(false), readEpoch = useRef(0);
  const commands = useRef({ run: createCommand(), confirm: createCommand() });
  const cancellations = useRef<CancelMemory>({});
  const mutationChange = (value: boolean) => { setBusy(value); busyChange(value); };
  const uq = useLoad(() => usage(w), [refresh, revision]);
  const rq = useLoad(() => w.api.request<Page<RunSummary>>(`${w.prefix}/matching-runs?page=${runPage}&size=5`), [runPage, refresh, revision]);
  const displayedUsage = limitUsage ?? uq.data;
  const edit = (next: AllocationLine[]) => { readEpoch.current++; setLines(next); setReview(null); setError(''); };
  const remember = (paymentId: string, payment: string, invoiceId: string, invoice: string) => setLabels(v => ({ ...v, [`p:${paymentId}`]: payment, [`i:${invoiceId}`]: invoice }));
  const append = (line: AllocationLine, payment: string, invoice: string) => {
    try { edit(addLine(lines, line)); remember(line.paymentId, payment, line.invoiceId, invoice); setNotice('초안에 담았어요. 금액을 확인한 뒤 현재 잔액을 검토해 주세요.'); } catch (e) { setError(failText(e)); }
  };
  async function openRun(id: string) {
    setReading(true); setError('');
    try { setRun(await w.api.request<Run>(`${w.prefix}/matching-runs/${id}`)); }
    catch (e) { if (!(e instanceof StaleSession)) setError(failText(e)); }
    finally { setReading(false); }
  }
  async function generate() {
    if (locked.current || reading) return;
    let body; try { body = rangePayload(from, to); } catch (e) { setError(failText(e)); return; }
    locked.current = true; setBusy(true); busyChange(true); setError(''); setNotice('');
    try {
      const value = await w.api.request<Run>(`${w.prefix}/matching-runs`, { method: 'POST', body, key: commands.current.run.forPayload(body) });
      commands.current.run.succeeded(); setRun(value); setLimitUsage(null); setRevision(v => v + 1); changed(); setNotice('후보 실행이 저장됐어요. 동일 요청의 재전송은 추가 차감되지 않아요. 연결은 아직 확정되지 않았어요.');
    } catch (e) {
      if (e instanceof StaleSession) return;
      if (e instanceof ApiError && e.usage) setLimitUsage(e.usage);
      setError(`${failText(e)}${uncertain(e) ? ' 결과가 저장되었을 수도 있어요. 내용을 바꾸지 않고 다시 누르면 같은 요청으로 확인해요.' : ''}`); setRevision(v => v + 1);
    } finally { locked.current = false; setBusy(false); busyChange(false); }
  }
  async function preview() {
    if (locked.current || reading) return;
    let body; try { body = allocationPayload(lines, reason); } catch (e) { setError(failText(e)); return; }
    const epoch = ++readEpoch.current; setReading(true); setReview(null); setError('');
    try {
      const values = await Promise.all(body.lines.map(async line => {
        const [payment, invoice] = await Promise.all([w.api.request<PaymentBalance>(`${w.prefix}/payments/${line.paymentId}/balance`), w.api.request<InvoiceBalance>(`${w.prefix}/invoices/${line.invoiceId}/balance?asOf=${koreaToday()}`)]);
        return { line, payment, invoice };
      }));
      if (epoch === readEpoch.current) setReview({ signature: reviewSignature(body), lines: values, checkedAt: new Date().toISOString() });
    } catch (e) { if (!(e instanceof StaleSession)) setError(failText(e)); }
    finally { setReading(false); }
  }
  async function confirm() {
    if (locked.current) return;
    let body; try { body = allocationPayload(lines, reason); } catch (e) { setError(failText(e)); return; }
    if (!canConfirm(body, review)) { setError('현재 잔액을 다시 검토해 주세요.'); return; }
    locked.current = true; setBusy(true); busyChange(true); setError(''); setNotice('');
    try {
      const value = await w.api.request<Allocation>(`${w.prefix}/allocations`, { method: 'POST', body, key: commands.current.confirm.forPayload(body) });
      commands.current.confirm.succeeded(); setResult(value.id); setLines([]); setReason(''); setReview(null); setRevision(v => v + 1); changed();
      setNotice('연결 확정 요청의 저장된 응답을 확인했어요. 아래 현재 상태 조회를 확인해 주세요.');
    } catch (e) {
      if (e instanceof StaleSession) return;
      setReview(null); setError(`${failText(e)}${uncertain(e) ? ' 이미 저장됐을 수도 있어요. 초안을 바꾸지 않고 잔액을 다시 조회한 뒤 확정을 누르면 같은 키로 확인해요. 자동 재시도하지 않아요.' : ' 초안은 유지했어요. 현재 잔액을 다시 조회해 주세요.'}`);
    } finally { locked.current = false; setBusy(false); busyChange(false); }
  }
  return <><Heading eyebrow="직접 검토하고 확정" title="입금 연결" subtitle="후보는 검토를 돕는 자료예요. 실제 은행 확인이나 자동 연결을 뜻하지 않아요." />
    <View style={s.row}><Button title="후보·직접 연결" secondary={section !== 'review'} disabled={busy} onPress={() => setSection('review')} /><Button title="연결 이력" secondary={section !== 'history'} disabled={busy} onPress={() => setSection('history')} /></View>
    {error ? <Notice error>{error}</Notice> : null}{notice ? <Notice>{notice}</Notice> : null}
    <View style={{ display: section === 'history' ? 'flex' : 'none', gap: 28 }}><History w={w} refresh={refresh + revision} busyChange={mutationChange} changed={changed} cancellations={cancellations.current} /></View><View style={{ display: section === 'review' ? 'flex' : 'none', gap: 28 }}>
      <View style={[s.surface, s.gap]}><Text style={s.sectionTitle}>새 후보 생성</Text>{displayedUsage ? <UsageView value={displayedUsage} /> : uq.error ? <QueryError text={uq.error} retry={uq.retry} /> : <Loading />}
        <Field label="입금 시작일" value={from} onChange={v => { setFrom(v); setLimitUsage(null); }} disabled={busy} maxLength={10} hint="YYYY-MM-DD" /><Field label="입금 종료일" value={to} onChange={v => { setTo(v); setLimitUsage(null); }} disabled={busy} maxLength={10} hint="양끝 포함 최대 366일 · 청구는 종료일 이전의 오래된 미수 청구도 포함" />
        <Notice>새 후보가 정상 저장되면 1회를 사용해요. 자료·후보가 0건이어도 1회예요. 실패는 차감되지 않아요. 기존 결과 조회·직접 연결·취소는 횟수를 사용하지 않아요.</Notice>
        <Button title="후보 생성 · 성공 시 1회 사용" busy={busy} disabled={reading} onPress={generate} /><Button title="사용량 새로고침" secondary disabled={busy} onPress={() => { setLimitUsage(null); uq.retry(); }} />
      </View>
      <View style={s.gap}><Text style={s.sectionTitle}>저장된 후보 실행 · 무료 조회</Text>{rq.data ? <>{rq.data.items.length ? rq.data.items.map(item => <View key={item.id} style={s.item}><Text style={s.itemTitle}>{dateLabel(item.from)} ~ {dateLabel(item.to)}</Text><Text style={s.muted}>{time(item.createdAt)} · 입금 {item.sourceCounts.paymentCount}건 / 청구 {item.sourceCounts.invoiceCount}건</Text><Button title={`${dateLabel(item.from)} ~ ${dateLabel(item.to)} 저장 결과 열기`} secondary disabled={busy || reading} onPress={() => openRun(item.id)} /></View>) : <Text style={s.body}>저장된 후보 실행이 없어요. 직접 연결 검토는 아래에서 시작할 수 있어요.</Text>}<Pager page={runPage} hasNext={rq.data.hasNext} disabled={busy || reading} previous={() => setRunPage(v => v - 1)} next={() => setRunPage(v => v + 1)} /></> : rq.error ? <QueryError text={rq.error} retry={rq.retry} /> : <Loading />}</View>
      {reading ? <Loading /> : null}
      {run ? <View style={s.gap}><Text style={s.sectionTitle}>저장 당시 후보</Text><Notice>{time(run.createdAt)} 저장 결과예요. 이후 연결·취소로 잔액이 달라질 수 있어요. 현재 잔액과 별개이며 후보 제안은 자동 배분 계획이 아니에요.</Notice><Text style={s.body}>{presenceText[run.dataPresence] ?? '자료 상태를 확인해 주세요.'}</Text><Text style={s.muted}>입금 {run.sourceCounts.paymentCount}건 · 미수 청구 {run.sourceCounts.invoiceCount}건</Text>
        {run.payments.map(item => <View key={item.payment.id} style={[s.surface, s.gap]}><Text style={s.sectionTitle}>{paymentLabel(item.payment)}</Text><Text style={s.body}>원래 입금 {money(item.payment.amount)} · 저장 당시 미연결 {money(item.payment.unallocatedAmount)}</Text><SourceEvidence w={w} paymentId={item.payment.id} disabled={busy} />
          {!item.candidates.length ? <Notice>규칙 후보가 없어요. 직접 검토가 필요해요. 미입금이나 누락 파일을 증명하지 않아요.</Notice> : item.candidates.map(candidate => {
            const invoice = run.invoices.find(i => i.id === candidate.invoiceId); if (!invoice) return <Notice key={candidate.invoiceId}>청구 증거를 찾을 수 없어 후보를 담을 수 없어요.</Notice>;
            return <View key={candidate.invoiceId} style={s.item}><Text style={s.itemTitle}>{invoice.counterpartyName || '거래처명 미기재'} · {invoice.invoiceNumber}</Text><Text style={s.body}>청구일 {dateLabel(invoice.issueDate)} · 기한 {dateLabel(invoice.dueDate)}</Text><Text style={s.body}>청구액 {money(invoice.amount)} · 저장 당시 미수 {money(invoice.outstandingAmount)}</Text><Text style={s.body}>서버 제안 금액 {money(candidate.suggestedAmount)}</Text>{candidate.reasons.map((v, i) => <Text key={`r${i}`} style={s.muted}>근거: {evidenceText[v] ?? '추가 근거 확인 필요'}</Text>)}{candidate.warnings.map((v, i) => <Text key={`w${i}`} style={s.muted}>주의: {evidenceText[v] ?? '추가 주의사항 확인 필요'}</Text>)}<Button title={`${paymentLabel(item.payment)} → ${invoice.invoiceNumber} 초안에 담기`} secondary disabled={busy || reading} onPress={() => append({ paymentId: item.payment.id, invoiceId: invoice.id, amount: candidate.suggestedAmount }, paymentLabel(item.payment), `${invoice.counterpartyName} · ${invoice.invoiceNumber}`)} /></View>;
          })}{item.candidatesTruncated ? <Notice>후보 {item.totalCandidateCount}건 중 일부만 표시해요. 다른 청구도 직접 검토할 수 있어요.</Notice> : null}</View>)}
        <Notice>이름 일치는 법적 동일성이나 입금자 소유권을 증명하지 않아요. 범위에 해당하는 자료가 없다는 사실도 파일 미업로드를 뜻하지 않아요.</Notice></View> : null}
      <Button title={manual ? '직접 선택 닫기' : '입금·청구 직접 선택'} secondary disabled={busy || reading} onPress={() => setManual(v => !v)} />
      {manual ? <ManualPicker w={w} refresh={refresh} disabled={busy || reading} append={append} /> : null}
      <View style={[s.surface, s.gap]}><Text style={s.sectionTitle}>연결 초안 · {lines.length}행</Text><Text style={s.body}>부분 입금·묶음 입금은 행을 나눠 담으세요. 같은 입금과 청구 쌍은 한 번만 담을 수 있어요. 합계와 연결 후 잔액은 이 화면에서 계산하지 않아요.</Text>
        {lines.length ? lines.map((line, index) => <View key={`${line.paymentId}:${line.invoiceId}`} style={s.item}><Text style={s.itemTitle}>{labels[`p:${line.paymentId}`] || '선택한 입금'} → {labels[`i:${line.invoiceId}`] || '선택한 청구'}</Text><Field label={`${index + 1}행 연결 금액 (원)`} value={line.amount} numeric maxLength={13} disabled={busy || reading} onChange={amount => edit(lines.map((l, i) => i === index ? { ...l, amount } : l))} hint="1원~1조 원 · 쉼표·소수점 없이" /><Button title={`${index + 1}행 제거`} quiet disabled={busy || reading} onPress={() => edit(lines.filter((_, i) => i !== index))} /></View>) : <Text style={s.body}>후보 또는 직접 선택으로 연결할 행을 담아 주세요.</Text>}
        <Field label="연결 사유 (선택)" value={reason} maxLength={1000} multiline disabled={busy || reading} onChange={v => { readEpoch.current++; setReason(v); setReview(null); }} />
        <Button title="현재 잔액 검토" secondary disabled={!lines.length || busy || reading} onPress={preview} />
        {review ? <><Text style={s.sectionTitle}>확정 전 최종 검토</Text><Text style={s.muted}>{time(review.checkedAt)} 서버 조회 · 현재 잔액이며 과거 증빙이 아니에요.</Text>{review.lines.map((value, i) => <View key={i} style={s.item}><Text style={s.itemTitle}>{i + 1}행 · {labels[`p:${value.line.paymentId}`]} → {labels[`i:${value.line.invoiceId}`]}</Text><Text style={s.body}>연결할 금액 {money(value.line.amount)}</Text><Text style={s.body}>현재 입금 미연결 {money(value.payment.unallocatedAmount)}</Text><Text style={s.body}>현재 청구 미수 {money(value.invoice.outstandingAmount)}</Text></View>)}<Notice>위 행과 사유를 직접 확인해 주세요. 확정 시 서버가 잔액을 다시 검사해요. 잔액이 바뀌면 전체 요청을 거절할 수 있어요. 실제 돈 이동은 발생하지 않아요.</Notice><Button title="연결 확정" busy={busy} onPress={confirm} /><Button title="검토로 돌아가기" secondary disabled={busy} onPress={() => setReview(null)} /></> : null}
      </View>
      {result ? <View style={s.gap}><Text style={s.sectionTitle}>확정 요청 후 현재 상태</Text><AllocationDetail key={`${result}:${revision}`} w={w} id={result} refresh={refresh + revision} busyChange={mutationChange} changed={changed} cancellations={cancellations.current} /></View> : null}
    </View>
  </>;
}
function uncertain(error: unknown) { return error instanceof ApiError && (error.status === 0 || error.status >= 500); }
function time(value: string) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) + ' 서울'; }
function paymentLabel(p: { payerName: string | null; bookedDate: string; amount?: string }) { return `${p.payerName || '입금자 미기재'} · ${dateLabel(p.bookedDate)}${p.amount ? ` · ${money(p.amount)}` : ''}`; }
function UsageView({ value }: { value: Usage }) { return <View style={s.gap}><Text style={s.body}>{value.month} · {value.plan} · 완료 {value.completed}회 / 한도 {value.limit}회 · 남은 {value.remaining}회</Text><Text style={s.muted}>서버 기준 초기화 {time(value.resetAt)} · {value.timezone}</Text></View>; }
function Pager({ page, hasNext, disabled, previous, next }: { page: number; hasNext: boolean; disabled: boolean; previous(): void; next(): void }) { return <View style={s.pager}><Button title="이전 페이지" secondary disabled={page === 0 || disabled} onPress={previous} /><Text style={s.muted}>{page + 1}페이지</Text><Button title="다음 페이지" secondary disabled={!hasNext || disabled} onPress={next} /></View>; }
function SourceEvidence({ w, paymentId, disabled }: { w: Workspace; paymentId: string; disabled: boolean }) {
  const [opened, setOpened] = useState(false);
  return opened ? <SourceContent w={w} paymentId={paymentId} /> : <Button title="입금 원본 출처 확인" quiet disabled={disabled} onPress={() => setOpened(true)} />;
}
function SourceContent({ w, paymentId }: { w: Workspace; paymentId: string }) {
  const q = useLoad(async () => {
    const payment = await w.api.request<Payment>(`${w.prefix}/payments/${paymentId}`);
    if (payment.sourceKind === 'MANUAL') return { kind: 'MANUAL' as const };
    if (payment.sourceKind !== 'BANK_CSV') throw new Error('입금 출처 유형을 확인하지 못했어요.');
    const row = await w.api.request<{ importId: string; recordNumber: number; bankAccountKey: string; sourceLineId: string; bookedDate: string; payerName: string | null; amount: string; note: string | null; originalCells: unknown }>(`${w.prefix}/payments/${paymentId}/source`);
    const file = await w.api.request<{ filename: string }>(`${w.prefix}/bank-imports/${row.importId}`);
    return { ...row, filename: file.filename, kind: 'BANK_CSV' as const };
  }, [paymentId]);
  return q.data?.kind === 'MANUAL' ? <Notice>서버에 직접 등록된 입금이에요. CSV 원본 출처를 제공하지 않아요.</Notice> : q.data ? <View style={s.gap}><Text style={s.badge}>서버에서 조회한 CSV 원본 출처</Text><Text style={s.body}>파일 {q.data.filename} · 계좌 별칭 {q.data.bankAccountKey} · 원본 거래번호 {q.data.sourceLineId} · 원본 레코드 {q.data.recordNumber} · {dateLabel(q.data.bookedDate)} · {q.data.payerName || '입금자 미기재'} · {money(q.data.amount)}</Text>{q.data.note ? <Text style={s.body}>{q.data.note}</Text> : null}<Text style={s.muted}>원본 셀: {Array.isArray(q.data.originalCells) ? q.data.originalCells.map(String).join(' / ') : '셀 정보 확인 필요'}</Text></View> : q.error ? <><Notice>이 입금의 CSV 원본 출처를 확인하지 못했어요. 직접 등록한 입금일 수 있어요. 파일 누락 여부를 판단할 수 없어요.</Notice><Button title="출처 다시 조회" secondary onPress={q.retry} /></> : <Loading />;
}
function ManualPicker({ w, refresh, disabled, append }: { w: Workspace; refresh: number; disabled: boolean; append(line: AllocationLine, payment: string, invoice: string): void }) {
  const [paymentPage, setPaymentPage] = useState(0), [invoicePage, setInvoicePage] = useState(0), [payment, setPayment] = useState<LedgerItem | null>(null), [invoice, setInvoice] = useState<LedgerItem | null>(null), [amount, setAmount] = useState('');
  const pq = useLoad(() => ledger(w, 'payments', paymentPage, 5), [paymentPage, refresh]);
  const iq = useLoad(() => ledger(w, 'invoices', invoicePage, 5), [invoicePage, refresh]);
  return <View style={[s.surface, s.gap]}><Text style={s.sectionTitle}>직접 판단으로 선택</Text><Notice>규칙 후보에 없는 청구도 직접 확인했다면 선택할 수 있어요. 금액을 자동 제안하지 않아요. 현재 잔액이 있는 항목만 선택할 수 있어요.</Notice><Text style={s.sectionTitle}>입금 선택</Text>{pq.data ? <>{pq.data.items.length ? pq.data.items.map(item => <View key={item.payment!.id} style={s.item}><Text style={s.body}>{paymentLabel(item.payment!)} · 현재 미연결 {money(item.paymentBalance!.unallocatedAmount)}</Text><Button title={`${paymentLabel(item.payment!)} 선택${payment?.payment?.id === item.payment!.id ? '됨' : ''}`} secondary disabled={disabled || item.paymentBalance!.unallocatedAmount === '0'} onPress={() => setPayment(item)} /></View>) : <Text style={s.body}>이 페이지에 등록된 입금이 없어요.</Text>}<Pager page={paymentPage} hasNext={pq.data.hasNext} disabled={disabled} previous={() => setPaymentPage(v => v - 1)} next={() => setPaymentPage(v => v + 1)} /></> : pq.error ? <QueryError text={pq.error} retry={pq.retry} /> : <Loading />}
    <Text style={s.sectionTitle}>청구 선택</Text>{iq.data ? <>{iq.data.items.length ? iq.data.items.map(item => <View key={item.invoice!.id} style={s.item}><Text style={s.body}>{item.customer!.name} · {item.invoice!.invoiceNumber} · 현재 미수 {money(item.invoiceBalance!.outstandingAmount)}</Text><Button title={`${item.invoice!.invoiceNumber} 선택${invoice?.invoice?.id === item.invoice!.id ? '됨' : ''}`} secondary disabled={disabled || item.invoiceBalance!.outstandingAmount === '0'} onPress={() => setInvoice(item)} /></View>) : <Text style={s.body}>이 페이지에 등록된 청구가 없어요.</Text>}<Pager page={invoicePage} hasNext={iq.data.hasNext} disabled={disabled} previous={() => setInvoicePage(v => v - 1)} next={() => setInvoicePage(v => v + 1)} /></> : iq.error ? <QueryError text={iq.error} retry={iq.retry} /> : <Loading />}
    {payment ? <Text style={s.badge}>선택 입금: {paymentLabel(payment.payment!)}</Text> : null}{invoice ? <Text style={s.badge}>선택 청구: {invoice.customer!.name} · {invoice.invoice!.invoiceNumber}</Text> : null}<Field label="직접 연결할 금액 (원)" numeric value={amount} disabled={disabled} maxLength={13} onChange={setAmount} hint="사용자가 직접 확인한 1원~1조 원 정수" /><Button title="직접 판단한 행 초안에 담기" secondary disabled={disabled || !payment || !invoice || !validAmount(amount)} onPress={() => { if (payment && invoice) append({ paymentId: payment.payment!.id, invoiceId: invoice.invoice!.id, amount }, paymentLabel(payment.payment!), `${invoice.customer!.name} · ${invoice.invoice!.invoiceNumber}`); }} />
  </View>;
}
function History({ w, refresh, busyChange, changed, cancellations }: Props & { cancellations: CancelMemory }) {
  const [page, setPage] = useState(0), [id, setId] = useState<string | null>(null), [busy, setBusy] = useState(false);
  const q = useLoad(() => w.api.request<Page<Allocation>>(`${w.prefix}/allocations?page=${page}&size=5`), [page, refresh]);
  return <View style={s.gap}><Text style={s.sectionTitle}>최근 연결 이력</Text><Text style={s.muted}>조회와 취소는 후보 생성 횟수를 사용하지 않아요. 최신 등록순 · 페이지당 최대 5건</Text><Button title="이력 새로고침" secondary disabled={busy} onPress={q.retry} />{q.data ? <>{q.data.items.length ? q.data.items.map((item, index) => <View key={item.id} style={s.item}><Text style={s.itemTitle}>{item.status === 'CANCELLED' ? '취소됨' : '확정됨'} · {time(item.createdAt)}</Text><Text style={s.body}>{item.lines.length}행 · {item.reason || '확정 사유 없음'}</Text><Button title={`${index + 1}번 연결 이력 상세`} secondary disabled={busy} onPress={() => setId(item.id)} /></View>) : <Empty title="연결 이력이 없어요" text="직접 확정한 연결과 취소 이력이 이곳에 남아요." />}<Pager page={page} hasNext={q.data.hasNext} disabled={busy} previous={() => setPage(v => v - 1)} next={() => setPage(v => v + 1)} /></> : q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading />}{id ? <AllocationDetail key={id} w={w} id={id} refresh={refresh} busyChange={value => { setBusy(value); busyChange(value); }} changed={changed} cancellations={cancellations} /> : null}</View>;
}
function HistoryLine({ w, line }: { w: Workspace; line: AllocationLine }) {
  const q = useLoad(async () => {
    const [invoice, payment] = await Promise.all([invoiceDetail(w, line.invoiceId), w.api.request<Payment>(`${w.prefix}/payments/${line.paymentId}`)]);
    return `${paymentLabel(payment)} → ${invoice.customer.name} · ${invoice.invoice.invoiceNumber}`;
  }, [line.paymentId, line.invoiceId]);
  return <View style={s.gap}><Text style={s.body}>{money(line.amount)}</Text>{q.data ? <Text style={s.muted}>{q.data}</Text> : q.error ? <QueryError text="입금·청구 표시 정보를 읽지 못했어요. 금액은 저장된 연결 이력이에요." retry={q.retry} /> : <Text style={s.muted}>연결한 입금·청구 표시 정보를 읽고 있어요.</Text>}</View>;
}
function AllocationDetail({ w, id, refresh, busyChange, changed, cancellations }: { w: Workspace; id: string; refresh: number; busyChange(value: boolean): void; changed(): void; cancellations: CancelMemory }) {
  if (!cancellations[id]) cancellations[id] = { command: createCommand(), reason: '' };
  const memory = cancellations[id];
  const [reason, setReason] = useState(memory.reason), [stage, setStage] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState(''), [revision, setRevision] = useState(0);
  const command = memory.command, locked = useRef(false);
  const [visibleLines, setVisibleLines] = useState(10);
  const q = useLoad(() => w.api.request<Allocation>(`${w.prefix}/allocations/${id}`), [id, refresh, revision]);
  async function cancel() {
    if (!stage || locked.current || !reason.trim() || Array.from(reason.trim()).length > 1000) return;
    const body = { reason: reason.trim() }; locked.current = true; setBusy(true); busyChange(true); setError('');
    try {
      await w.api.request<Allocation>(`${w.prefix}/allocations/${id}/cancel`, { method: 'POST', body, key: command.forPayload({ id, ...body }) });
      command.succeeded(); memory.reason = ''; setStage(false); setReason(''); setNotice('취소 요청의 저장된 응답을 확인했어요. 현재 상태를 다시 조회해요.'); setRevision(v => v + 1); changed();
    } catch (e) { if (!(e instanceof StaleSession)) setError(`${failText(e)}${uncertain(e) ? ' 이미 취소됐을 수도 있어요. 사유를 바꾸지 않고 다시 누르면 같은 키로 확인해요.' : ' 현재 상태를 다시 불러와 주세요.'}`); }
    finally { locked.current = false; setBusy(false); busyChange(false); }
  }
  return <View style={[s.surface, s.gap]}><Text style={s.sectionTitle}>현재 연결 상세</Text>{notice ? <Notice>{notice}</Notice> : null}{error ? <Notice error>{error}</Notice> : null}<Button title="현재 연결 상태 다시 조회" secondary disabled={busy} onPress={q.retry} />{q.data ? <><Text style={s.badge}>{q.data.status === 'CANCELLED' ? '취소됨' : '확정됨'} · 현재 조회 버전 {q.data.version}</Text><Text style={s.body}>확정 시각 {time(q.data.createdAt)} · 사유 {q.data.reason || '없음'}</Text><Text style={s.muted}>전체 {q.data.lines.length}행 · {Math.min(visibleLines, q.data.lines.length)}행 표시</Text>{q.data.lines.slice(0, visibleLines).map((line, i) => <HistoryLine key={i} w={w} line={line} />)}{visibleLines < q.data.lines.length ? <Button title="연결 행 더 보기" secondary disabled={busy} onPress={() => setVisibleLines(v => v + 10)} /> : null}<Text style={s.sectionTitle}>감사 이력</Text>{q.data.audit.map((a, i) => <Text key={i} style={s.body}>{a.eventType === 'CANCELLED' || a.eventType === 'CANCEL' ? '취소' : '확정'} · {time(a.createdAt)} · {a.reason || '사유 없음'} · 버전 {a.version}</Text>)}{q.data.cancellation ? <Notice>취소 시각 {time(q.data.cancellation.createdAt)} · 사유: {q.data.cancellation.reason}</Notice> : <><Field label="연결 취소 사유 (필수)" value={reason} multiline maxLength={1000} disabled={busy} onChange={v => { memory.reason = v; setReason(v); setStage(false); }} />{stage ? <><Notice>이 연결의 모든 행을 취소해요. 원래 청구·입금은 유지하고 연결 잔액을 복원해요. 환불이나 실제 돈 이동이 아니에요. 사유: {reason.trim()}</Notice><Button title="연결 취소 확정" onPress={cancel} busy={busy} /><Button title="취소 검토로 돌아가기" secondary disabled={busy} onPress={() => setStage(false)} /></> : <Button title="전체 연결 취소 검토" secondary disabled={busy || !reason.trim()} onPress={() => setStage(true)} />}</>}</> : q.error ? <QueryError text={q.error} retry={q.retry} /> : <Loading />}</View>;
}
