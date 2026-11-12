#!/usr/bin/env python3
"""Local real Java API + Chromium restart test; isolated test DB and disposable accounts only."""
import asyncio, json, os, pathlib, shutil, subprocess, tempfile, urllib.parse, urllib.request, uuid
from playwright.async_api import async_playwright, expect
API='http://127.0.0.1:8091'; WEB='http://127.0.0.1:8081'
db=urllib.parse.urlsplit(os.environ.get('TEST_DB_URL','').removeprefix('jdbc:'))
assert db.hostname=='127.0.0.1' and db.path=='/reconciliation_test'
assert os.environ.get('TEST_DB_USERNAME')=='chagok_test'
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
accounts=[]; checks=[]; errors=[]; payment_requests=[]
output=pathlib.Path(tempfile.mkdtemp(prefix='payment-recovery-',dir='/workspace/.local/run'))

def api(path, body=None, token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(API+'/api/v1'+path, data=None if body is None else json.dumps(body).encode(),headers=headers)
    with opener.open(req,timeout=10) as response:return json.loads(response.read())

def create_account():
    login='recovery-'+uuid.uuid4().hex; password='Synthetic-'+uuid.uuid4().hex
    session=api('/auth/signup',{'login':login,'password':password,'tenantName':'입금 복구 검수'})
    me=api('/auth/me',token=session['accessToken'])
    result={'login':login,'password':password,'id':me['accountId'],'tenant':me['tenants'][0]['id'],'token':session['accessToken']}
    accounts.append(result);return result

async def login(page,account):
    await page.goto(WEB,wait_until='networkidle')
    await page.get_by_label('아이디',exact=True).fill(account['login'])
    await page.get_by_label('비밀번호',exact=True).fill(account['password'])
    await page.get_by_role('button',name='로그인',exact=True).click()
    await expect(page.get_by_role('tab',name='홈',exact=True)).to_be_visible()

async def main():
    a=create_account();b=create_account(); prefix='/tenants/'+a['tenant']
    async with async_playwright() as pw:
        async def start(profile):
            context=await pw.chromium.launch_persistent_context(str(profile),executable_path=os.environ.get('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox','--disable-dev-shm-usage'],viewport={'width':390,'height':844})
            async def guard(route):
                request=route.request
                assert request.url.startswith(API+'/api/') and '/ai-review/' not in request.url
                if request.method=='POST' and request.url.endswith('/payments'):
                    payment_requests.append({'key':request.headers.get('idempotency-key'),'body':request.post_data_json})
                    if len(payment_requests)==1:
                        result=await route.fetch()
                        assert result.status==201
                        await route.fulfill(status=503,json={'code':'SIMULATED_RESPONSE_LOST'})
                        return
                await route.continue_()
            await context.route('**/api/**',guard)
            page=context.pages[0];page.on('pageerror',lambda e:errors.append(str(e)))
            return context,page
        profile=output/'profile'
        context,page=await start(profile)
        await login(page,a)
        await page.get_by_role('button',name='입금 추가',exact=True).click()
        await page.get_by_label('금액 (원)',exact=True).fill('40000')
        await page.get_by_label('입금일',exact=True).fill('2026-11-12')
        await page.get_by_label('입금자 이름 (선택)',exact=True).fill('복구 확인 거래처')
        await page.get_by_role('button',name='입금 등록하기',exact=True).click()
        await expect(page.get_by_text('확인이 필요한 입금 등록',exact=True)).to_be_visible()
        assert len(api(prefix+'/payments',token=a['token'])['items'])==1
        assert len(payment_requests)==1
        await page.screenshot(path=str(output/'pending-before-restart.png'),full_page=True)
        checks.append('serverCommittedLostResponseOnePaymentAndNoAutomaticRetry')
        await context.close()
        context,page=await start(profile)
        await login(page,b)
        await expect(page.get_by_role('button',name='입금 추가',exact=True)).to_be_visible()
        await expect(page.get_by_text('확인이 필요한 입금 등록',exact=True)).to_have_count(0)
        assert len(payment_requests)==1
        checks.append('browserProcessRestartAndDifferentAccountCannotSeePending')
        await page.get_by_role('tab',name='계정',exact=True).click()
        await page.get_by_role('button',name='로그아웃',exact=True).click()
        await login(page,a)
        await expect(page.get_by_text('확인이 필요한 입금 등록',exact=True)).to_be_visible()
        assert len(payment_requests)==1
        await page.set_viewport_size({'width':320,'height':740})
        assert not await page.evaluate('document.documentElement.scrollWidth>innerWidth')
        await page.screenshot(path=str(output/'pending-after-restart.png'),full_page=True)
        checks.append('sameAccountRestoresImmutableRequestWithoutPostAndResponsive320')
        await page.get_by_role('button',name='등록 결과 다시 확인',exact=True).click()
        await expect(page.get_by_text('확인이 필요한 입금 등록',exact=True)).to_have_count(0)
        assert len(payment_requests)==2 and payment_requests[0]==payment_requests[1]
        rows=api(prefix+'/payments',token=a['token'])['items']
        assert len(rows)==1 and rows[0]['amount']=='40000'
        count=await page.evaluate("""() => new Promise((resolve,reject)=>{const r=indexedDB.open('chagok-payment-recovery',1);r.onsuccess=()=>{const db=r.result;const t=db.transaction('pending');const q=t.objectStore('pending').count();q.onsuccess=()=>resolve(q.result);t.oncomplete=()=>db.close();};r.onerror=()=>reject();})""")
        assert count==0
        checks.append('explicitRetrySameKeySameBodyOneActualPaymentAndPendingRemoved')
        await page.screenshot(path=str(output/'recovered-payment.png'),full_page=True)
        await context.close()
        # Browser storage unavailable: form must never dispatch a payment POST.
        context,page=await start(output/'blocked-storage-profile')
        await context.add_init_script("Object.defineProperty(window,'indexedDB',{get(){throw new Error('storage disabled')}})")
        await login(page,a)
        await page.get_by_role('button',name='입금 추가',exact=True).click()
        await expect(page.get_by_role('button',name='입금 등록하기',exact=True)).to_be_disabled()
        assert len(payment_requests)==2
        checks.append('unavailableBrowserStorageBlocksPost')
        await context.close()
        assert not errors, 'Browser page errors observed'
        print(json.dumps({'passed':checks,'pageErrors':len(errors),'screenshots':str(output)},ensure_ascii=False))
try:
    asyncio.run(main())
finally:
    for a in accounts:
        owner=str(uuid.UUID(a['id']));tenant=str(uuid.UUID(a['tenant']))
        tables=['receivable_registration_request','receivable_payment','receivable_counterparty']
        sql='BEGIN;'+''.join(f"DELETE FROM {table} WHERE tenant_id='{tenant}';" for table in tables)+f"DELETE FROM app_session WHERE account_id='{owner}';DELETE FROM app_membership WHERE tenant_id='{tenant}';DELETE FROM app_tenant WHERE id='{tenant}';DELETE FROM app_account WHERE id='{owner}';COMMIT;"
        e=os.environ.copy();e['PGPASSWORD']=e['TEST_DB_PASSWORD']
        psql=shutil.which('psql') or '/workspace/.local/postgres/usr/lib/postgresql/17/bin/psql'
        r=subprocess.run([psql,'-X','-w','-v','ON_ERROR_STOP=1','-q','-h',db.hostname,'-p',str(db.port or 5432),'-U','chagok_test','-d','reconciliation_test'],input=sql,text=True,env=e,capture_output=True)
        if r.returncode:raise RuntimeError('Disposable recovery account cleanup failed')
    print('Disposable recovery accounts cleaned; development DB was not used.')
