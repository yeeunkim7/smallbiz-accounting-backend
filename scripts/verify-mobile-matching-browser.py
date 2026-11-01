#!/usr/bin/env python3
"""Actual mobile matching/confirmation/cancellation in Chromium; isolated synthetic test DB only."""
import argparse,asyncio,json,uuid,subprocess,pathlib,urllib.request,urllib.parse,os,shutil
from playwright.async_api import async_playwright,expect
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--api',default='http://127.0.0.1:8091')
parser.add_argument('--web',default='http://127.0.0.1:8081')
parser.add_argument('--screenshots',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[1]/'docs'/'screenshots')
args=parser.parse_args();API=args.api.rstrip('/');WEB=args.web.rstrip('/');OUT=args.screenshots;OUT.mkdir(parents=True,exist_ok=True)
for value in (API,WEB):
 u=urllib.parse.urlsplit(value)
 if u.scheme!='http' or u.hostname not in ('localhost','127.0.0.1') or u.username or u.password or u.path or u.query or u.fragment:raise SystemExit('Use local HTTP test servers only.')
db=urllib.parse.urlsplit(os.environ.get('TEST_DB_URL','').removeprefix('jdbc:'))
if db.scheme!='postgresql' or db.hostname not in ('localhost','127.0.0.1') or db.path!='/reconciliation_test':raise SystemExit('TEST_DB_URL must select local reconciliation_test.')
if not os.environ.get('TEST_DB_USERNAME') or not os.environ.get('TEST_DB_PASSWORD'):raise SystemExit('Configure test DB credentials securely first.')
checks=[];errors=[];owner=None;tenant=None;token=None;all_requests=[]
def api(path,body=None,key=None):
 headers={'Authorization':'Bearer '+token} if token else {}
 if key:headers['Idempotency-Key']=key
 if body is not None:headers['Content-Type']='application/json'
 req=urllib.request.Request(API+'/api/v1'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
 with urllib.request.urlopen(req,timeout=10) as r:return json.loads(r.read()) if r.status!=204 else None
async def nooverflow(page):assert not await page.evaluate('document.documentElement.scrollWidth>innerWidth')
async def main():
 global owner,tenant,token
 login='matching-ui-'+uuid.uuid4().hex;password='Synthetic-'+uuid.uuid4().hex
 session=api('/auth/signup',{'login':login,'password':password,'tenantName':'가상공방 · 입금 연결 검증'});owner=session['accountId'];token=session['accessToken'];tenant=api('/auth/me')['tenants'][0]['id'];prefix='/tenants/'+tenant
 def create(resource,body):return api(prefix+'/'+resource,body,uuid.uuid4().hex)
 cp=create('counterparties',{'code':'SYNTHETIC','name':'가상 거래처'})
 invoices=[create('invoices',{'counterpartyId':cp['id'],'invoiceNumber':'합성 청구 '+label,'amount':amount,'issueDate':'2026-11-01','dueDate':'2026-11-30'}) for label,amount in [('A','100000'),('B','50000')]]
 payments=[create('payments',{'amount':amount,'bookedDate':'2026-11-0'+str(i+1),'payerName':'가상 거래처'}) for i,amount in enumerate(['40000','60000','150000'])]
 async with async_playwright() as p:
  executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium');b=await p.chromium.launch(**({'executable_path':executable} if executable else {}),headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  context=await b.new_context(viewport={'width':390,'height':844});page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
  async def guard(route):
   r=route.request;u=urllib.parse.urlsplit(r.url);assert f'{u.scheme}://{u.netloc}'==API
   assert '/ai-review/' not in u.path;all_requests.append((r.method,u.path));await route.continue_()
  await context.route('**/api/**',guard)
  await page.goto(WEB,wait_until='networkidle');await page.get_by_label('아이디',exact=True).fill(login);await page.get_by_label('비밀번호',exact=True).fill(password);await page.get_by_role('button',name='로그인',exact=True).click()
  await page.get_by_role('tab',name='입금 연결',exact=True).click();await expect(page.get_by_text('새 후보 생성',exact=True)).to_be_visible()
  await page.get_by_label('입금 시작일',exact=True).fill('2026-11-01');await page.get_by_label('입금 종료일',exact=True).fill('2026-11-30')
  await page.get_by_role('button',name='후보 생성 · 성공 시 1회 사용',exact=True).click();await expect(page.get_by_text('저장 당시 후보',exact=True)).to_be_visible()
  assert api(prefix+'/matching-usage')['completed']==1;assert len(api(prefix+'/allocations')['items'])==0
  await nooverflow(page);await page.get_by_text('저장 당시 후보',exact=True).evaluate("e=>e.scrollIntoView({block:'start',behavior:'instant'})");await page.evaluate('document.activeElement?.blur()');await page.screenshot(path=str(OUT/'mobile-matching-candidates.png'));checks.append('explicitCandidateGenerationOneChargeAndNoAutoAllocation')
  async def add(amount,invoice):
   text=amount+' → 합성 청구 '+invoice+' 초안에 담기'
   await page.get_by_role('button',name=__import__('re').compile(__import__('re').escape(text)+'$')).click()
  async def review():
   await page.get_by_role('button',name='현재 잔액 검토',exact=True).click();await expect(page.get_by_text('확정 전 최종 검토',exact=True)).to_be_visible()
  async def confirm():
   await review();await page.get_by_role('button',name='연결 확정',exact=True).click();await expect(page.get_by_text('확정 요청 후 현재 상태',exact=True)).to_be_visible();await expect(page.get_by_text('확정됨 · 현재 조회 버전 1',exact=True)).to_be_visible()
  async def cancel(reason):
   await page.get_by_label('연결 취소 사유 (필수)',exact=True).fill(reason)
   await page.get_by_role('button',name='전체 연결 취소 검토',exact=True).click()
   await page.get_by_role('button',name='연결 취소 확정',exact=True).click()
   await expect(page.get_by_text('취소됨 · 현재 조회 버전 2',exact=True)).to_be_visible()
  # Actual write commits; only its first response is replaced to test uncertainty.
  await add('40,000원','A');await review();keys=[];attempts=0
  async def uncertain(route):
   nonlocal attempts
   if route.request.method!='POST':await route.fallback();return
   attempts+=1;keys.append(route.request.headers.get('idempotency-key'))
   if attempts==1:
    await route.fetch();await route.fulfill(status=503,json={'code':'SYNTHETIC_RESPONSE_LOST'});return
   await route.fallback()
  await context.route('**/allocations',uncertain)
  await page.get_by_role('button',name='연결 확정',exact=True).click();await expect(page.get_by_text('이미 저장됐을 수도 있어요.',exact=False)).to_be_visible()
  await page.get_by_role('tab',name='계정',exact=True).click();await page.get_by_role('tab',name='입금 연결',exact=True).click();await expect(page.get_by_label('1행 연결 금액 (원)',exact=True)).to_have_value('40000')
  await confirm();assert attempts==2 and keys[0] and keys[0]==keys[1];assert len(api(prefix+'/allocations')['items'])==1
  assert api(prefix+'/receivables/summary?asOf=2026-11-30')['outstandingAmount']=='110000';await expect(page.get_by_text('연결한 입금·청구 표시 정보를 읽고 있어요.',exact=True)).to_have_count(0);await page.get_by_text('확정 요청 후 현재 상태',exact=True).evaluate("e=>e.scrollIntoView({block:'start',behavior:'instant'})");await page.evaluate('document.activeElement?.blur()');await page.screenshot(path=str(OUT/'mobile-matching-partial-confirm.png'));checks.append('actualPartialConfirmUncertainResponseSameKeyAcrossTabsNoDuplicate')
  await cancel('합성 부분 연결 취소 검증');assert api(prefix+'/receivables/summary?asOf=2026-11-30')['outstandingAmount']=='150000';checks.append('requiredReasonCancellationRestoresBalanceAndAudit')
  # The saved candidate is intentionally old; current balance preview and server locks decide.
  await add('40,000원','A');await add('60,000원','A');await confirm()
  assert api(prefix+'/receivables/summary?asOf=2026-11-30')['outstandingAmount']=='50000';checks.append('bundledTwoPaymentsOneInvoiceAuthoritativeBalance')
  await cancel('합성 묶음 연결 취소 검증')
  await add('150,000원','A');await add('150,000원','B');await review();await page.get_by_text('확정 전 최종 검토',exact=True).evaluate("e=>e.scrollIntoView({block:'start',behavior:'instant'})");await page.evaluate('document.activeElement?.blur()');await page.screenshot(path=str(OUT/'mobile-matching-split-review.png'))
  await page.get_by_role('button',name='연결 확정',exact=True).click();await expect(page.get_by_text('확정됨 · 현재 조회 버전 1',exact=True)).to_be_visible()
  assert api(prefix+'/receivables/summary?asOf=2026-11-30')['outstandingAmount']=='0';checks.append('singlePaymentSplitAcrossTwoInvoices')
  await page.get_by_role('tab',name='홈',exact=True).click();await expect(page.get_by_text('0원',exact=True).first).to_be_visible();await page.screenshot(path=str(OUT/'mobile-matching-home-paid.png'))
  await page.get_by_role('tab',name='입금 연결',exact=True).click();await cancel('합성 분할 연결 취소 검증');await expect(page.get_by_text('연결한 입금·청구 표시 정보를 읽고 있어요.',exact=True)).to_have_count(0);await page.get_by_text('현재 연결 상세',exact=True).evaluate("e=>e.scrollIntoView({block:'start',behavior:'instant'})");await page.evaluate('document.activeElement?.blur()');await page.screenshot(path=str(OUT/'mobile-matching-cancelled.png'))
  assert api(prefix+'/receivables/summary?asOf=2026-11-30')['outstandingAmount']=='150000';assert api(prefix+'/matching-usage')['completed']==1;checks.append('confirmationCancellationAndReadsUseNoExtraQuota')
  await page.get_by_role('button',name='연결 이력',exact=True).click();await expect(page.get_by_text('최근 연결 이력',exact=True)).to_be_visible();assert len(api(prefix+'/allocations')['items'])==3;assert all(a['status']=='CANCELLED' and len(a['audit'])==2 for a in api(prefix+'/allocations')['items']);await page.screenshot(path=str(OUT/'mobile-matching-history.png'));checks.append('immutableOriginalLinesAndConfirmationCancellationHistory')
  await page.set_viewport_size({'width':320,'height':740});await nooverflow(page);await page.get_by_role('button',name='후보·직접 연결',exact=True).click();await nooverflow(page);checks.append('narrow320WithoutHorizontalDocumentOverflow')
  # Actual server quota exhaustion; existing GET results and manual tools remain available.
  remaining=api(prefix+'/matching-usage')['remaining'];assert 0<=remaining<=10
  for _ in range(remaining):create('matching-runs',{'from':'2026-11-01','to':'2026-11-30'})
  await page.get_by_role('button',name='사용량 새로고침',exact=True).click();await expect(page.get_by_text('남은 0회',exact=False)).to_be_visible()
  await page.get_by_role('button',name='후보 생성 · 성공 시 1회 사용',exact=True).click();await expect(page.get_by_text('이번 달 후보 생성 한도를 모두 사용했어요.',exact=False)).to_be_visible()
  assert api(prefix+'/matching-usage')['completed']==10
  await page.get_by_role('button',name='2026.11.01 ~ 2026.11.30 저장 결과 열기',exact=True).first.click();await expect(page.get_by_text('저장 당시 후보',exact=True)).to_be_visible();assert api(prefix+'/matching-usage')['completed']==10;checks.append('actualQuota429PreservesFreeSavedResultReads')
  assert not errors,errors;print(json.dumps({'browserScenarios':len(checks),'passed':checks,'pageErrors':errors},ensure_ascii=False));await b.close()
try:asyncio.run(main())
finally:
 if owner and tenant:
  uuid.UUID(owner);uuid.UUID(tenant)
  tables=['receivable_matching_usage_event','receivable_monthly_usage','receivable_matching_run','receivable_allocation_command','receivable_allocation_audit','receivable_allocation_cancellation','receivable_allocation_line','receivable_allocation_batch','receivable_registration_request','receivable_bank_import_request','receivable_bank_source_row','receivable_bank_transaction','receivable_bank_import','receivable_invoice','receivable_payment','receivable_counterparty']
  sql='BEGIN; SET CONSTRAINTS ALL DEFERRED;'+''.join(f"DELETE FROM {t} WHERE tenant_id='{tenant}';" for t in tables)+f"DELETE FROM app_session WHERE account_id='{owner}'; DELETE FROM app_membership WHERE tenant_id='{tenant}'; DELETE FROM app_tenant WHERE id='{tenant}'; DELETE FROM app_account WHERE id='{owner}'; COMMIT;"
  db_env={**os.environ,'PGPASSWORD':os.environ['TEST_DB_PASSWORD']}
  r=subprocess.run(['psql','-h',db.hostname,'-p',str(db.port or 5432),'-U',os.environ['TEST_DB_USERNAME'],'-d','reconciliation_test','-v','ON_ERROR_STOP=1','-q'],env=db_env,input=sql,text=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
  if r.returncode:raise RuntimeError('Synthetic browser test tenant cleanup failed')
  print('Synthetic browser test tenant cleaned.')
