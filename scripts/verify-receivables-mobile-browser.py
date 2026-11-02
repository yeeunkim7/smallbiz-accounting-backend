#!/usr/bin/env python3
"""Local Chromium + isolated PostgreSQL test-DB checks; synthetic data only, no model calls."""
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
def api(path,body=None):
 headers={'Authorization':'Bearer '+token}
 if body is not None:headers['Content-Type']='application/json'
 req=urllib.request.Request(API+'/api/v1'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
 with urllib.request.urlopen(req,timeout=10) as r:return json.loads(r.read()) if r.status!=204 else None
async def nooverflow(page):assert not await page.evaluate('document.documentElement.scrollWidth>innerWidth')
async def main():
 global owner,tenant,token
 async with async_playwright() as p:
  executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
  b=await p.chromium.launch(**({'executable_path':executable} if executable else {}),headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  context=await b.new_context(viewport={'width':390,'height':844});page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
  sample_requests=[]
  async def block_sample(route):
   sample_requests.append(route.request.url);await route.abort()
  await context.route('**/api/**',block_sample)
  await page.goto(WEB,wait_until='networkidle');await expect(page.get_by_role('button',name='샘플 체험',exact=True)).to_be_visible()
  await page.screenshot(path=str(OUT/'mobile-core-login.png'))
  await page.get_by_role('button',name='샘플 체험',exact=True).click();await expect(page.get_by_text('현재 미수 잔액',exact=True)).to_be_visible();await nooverflow(page)
  await page.screenshot(path=str(OUT/'mobile-core-sample-home.png'))
  await page.set_viewport_size({'width':1280,'height':900});await nooverflow(page);await page.screenshot(path=str(OUT/'mobile-core-desktop.png'))
  await page.set_viewport_size({'width':320,'height':740});await nooverflow(page)
  await page.get_by_role('tab',name='장부',exact=True).click();await page.get_by_role('button',name='느린날 스튜디오',exact=False).click();await expect(page.get_by_text('현재 남은 금액',exact=True)).to_be_visible()
  await page.get_by_role('tab',name='계정',exact=True).click();await expect(page.get_by_text('이번 달 이용 현황',exact=True)).to_be_visible()
  assert not sample_requests;checks.append('sampleHomeLedgerDetailAccountZeroApiAndResponsive')
  await page.get_by_role('button',name='체험 끝내기',exact=True).click();await expect(page.get_by_role('button',name='로그인',exact=True)).to_be_visible();await context.close()
  context=await b.new_context(viewport={'width':390,'height':844});page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
  async def guard(route):
   request=route.request;u=urllib.parse.urlsplit(request.url)
   assert f'{u.scheme}://{u.netloc}'==API
   all_requests.append((request.method,u.path))
   if '/ai-review/' in u.path:raise AssertionError('AI path requested')
   await route.continue_()
  await context.route('**/api/**',guard)
  await page.goto(WEB,wait_until='networkidle');await page.get_by_role('button',name='처음이신가요? · 회원가입',exact=True).click()
  login='mobile-ui-'+uuid.uuid4().hex;password='Synthetic-'+uuid.uuid4().hex
  await page.get_by_label('아이디',exact=True).fill(login);await page.get_by_label('비밀번호',exact=True).fill(password);await page.get_by_label('사업장 이름',exact=True).fill('가상공방 · 화면 검증')
  async with page.expect_response(lambda r:r.url.endswith('/api/v1/auth/signup') and r.request.method=='POST') as response:
   await page.get_by_role('button',name='가입하고 시작하기',exact=True).click()
  session=await (await response.value).json();owner=session['accountId'];token=session['accessToken'];tenant=api('/auth/me')['tenants'][0]['id'];prefix='/tenants/'+tenant
  await expect(page.get_by_text('등록된 청구 없음',exact=True)).to_be_visible();await page.screenshot(path=str(OUT/'mobile-core-empty.png'));checks.append('actualSignupAndEmptyHome')
  await page.get_by_role('button',name='거래처 등록',exact=True).click();await page.get_by_label('거래처 이름',exact=True).fill('합성 거래처');await page.get_by_label('거래처 코드',exact=True).fill('SYNTHETIC');await page.get_by_role('button',name='거래처 등록하기',exact=True).click();await expect(page.get_by_text('거래처를 등록했어요.',exact=False)).to_be_visible()
  await page.get_by_role('tab',name='홈',exact=True).click();await page.get_by_role('button',name='청구 추가',exact=True).click()
  await page.get_by_role('radio',name='합성 거래처',exact=False).click();await page.get_by_label('청구 이름·번호',exact=True).fill('11월 합성 청구');await page.get_by_label('금액 (원)',exact=True).fill('100000');await page.get_by_label('청구일',exact=True).fill('2026-11-01');await page.get_by_label('입금 기한',exact=True).fill('2026-11-30')
  await page.screenshot(path=str(OUT/'mobile-core-registration.png'))
  await page.get_by_role('button',name='청구 등록하기',exact=True).click();await expect(page.get_by_text('청구를 등록했어요.',exact=False)).to_be_visible()
  await page.get_by_role('button',name='합성 거래처, 11월 합성 청구',exact=False).click();await expect(page.get_by_text('현재 남은 금액',exact=True)).to_be_visible();await expect(page.get_by_text('100,000원',exact=True).first).to_be_visible();await page.screenshot(path=str(OUT/'mobile-core-invoice-detail.png'));checks.append('actualCounterpartyInvoiceAndAuthoritativeBalance')
  await page.get_by_role('tab',name='홈',exact=True).click();await page.get_by_role('button',name='입금 추가',exact=True).click()
  await page.get_by_label('금액 (원)',exact=True).fill('40000');await page.get_by_label('입금일',exact=True).fill('2026-11-01');await page.get_by_label('입금자 이름 (선택)',exact=True).fill('합성 입금자')
  payment_keys=[]; attempts=0
  async def uncertain(route):
   nonlocal attempts
   if route.request.method!='POST':await route.fallback();return
   attempts+=1;payment_keys.append(route.request.headers.get('idempotency-key'))
   if attempts==1:
    await route.fetch();await route.fulfill(status=503,json={'code':'SYNTHETIC_RESPONSE_LOST'});return
   await route.fallback()
  await context.route('**/payments',uncertain)
  await page.get_by_role('button',name='입금 등록하기',exact=True).click();await expect(page.get_by_text('등록되었을 수도 있어요.',exact=False)).to_be_visible()
  await page.get_by_label('입금자 이름 (선택)',exact=True).fill('합성 입금자 ');await page.get_by_role('button',name='입금 등록하기',exact=True).click();await expect(page.get_by_text('입금을 등록했어요.',exact=False)).to_be_visible()
  assert attempts==2 and payment_keys[0] and payment_keys[0]==payment_keys[1]
  assert len(api(prefix+'/payments')['items'])==1;checks.append('uncertainCommittedPaymentCanonicalRetryNoDuplicate')
  await page.get_by_role('tab',name='홈',exact=True).click();await expect(page.get_by_text('100,000원',exact=True).first).to_be_visible();await expect(page.get_by_text('40,000원',exact=True)).to_be_visible();await page.screenshot(path=str(OUT/'mobile-core-live-home.png'));checks.append('unallocatedPaymentDoesNotReduceReceivable')
  await page.get_by_role('tab',name='계정',exact=True).click();await expect(page.get_by_text('이번 달 이용 현황',exact=True)).to_be_visible();assert api(prefix+'/matching-usage')['completed']==0;await page.screenshot(path=str(OUT/'mobile-core-account.png'));checks.append('serverUsageAndRegistrationZeroCharge')
  assert await page.evaluate('Object.keys(localStorage).length')==0 and await page.evaluate('Object.keys(sessionStorage).length')==0;checks.append('webNoTokenPersistence')
  await page.reload(wait_until='networkidle');await expect(page.get_by_role('button',name='로그인',exact=True)).to_be_visible();checks.append('webReloadRequiresLogin')
  await page.get_by_label('아이디',exact=True).fill(login);await page.get_by_label('비밀번호',exact=True).fill(password)
  async with page.expect_response(lambda r:r.url.endswith('/api/v1/auth/login') and r.request.method=='POST') as response:
   await page.get_by_role('button',name='로그인',exact=True).click()
  token=(await (await response.value).json())['accessToken']
  await expect(page.get_by_role('tab',name='계정',exact=True)).to_be_visible();await page.get_by_role('tab',name='계정',exact=True).click();api('/auth/logout',{})
  await page.get_by_role('tab',name='홈',exact=True).click();await expect(page.get_by_text('로그인 정보가 만료되었어요. 다시 로그인해 주세요.',exact=True)).to_be_visible();checks.append('expiredSessionClearsWorkspaceWithNotice')
  assert not errors,errors
  print(json.dumps({'browserScenarios':len(checks),'passed':checks,'pageErrors':errors,'apiPaths':[list(v) for v in sorted(set(all_requests))]},ensure_ascii=False))
  await b.close()
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
