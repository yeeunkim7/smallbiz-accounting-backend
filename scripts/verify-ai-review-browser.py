#!/usr/bin/env python3
"""Real Chromium UI checks. Model success is an intercepted fixture, never a paid call."""
import argparse, asyncio, json, urllib.request, re
from pathlib import Path
from playwright.async_api import async_playwright, expect

async def main():
    ap=argparse.ArgumentParser();ap.add_argument('--base',default='http://127.0.0.1:8082');ap.add_argument('--prefix',default='');args=ap.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]*',args.prefix):ap.error('--prefix must contain only letters, digits, hyphens or underscores')
    def artifact(name):return out/(args.prefix+name)
    base=args.base.rstrip('/'); out=Path(__file__).resolve().parents[1]/'docs/screenshots';out.mkdir(exist_ok=True)
    def query(path):
        with urllib.request.urlopen(base+path,timeout=15) as r:return json.load(r)
    date='2026-10-31'
    summary=query('/api/v1/ai-review/summary?date='+date)
    business=query('/api/v1/ai-review/originals/business?from='+date+'&to='+date)['content']
    banks=query('/api/v1/ai-review/originals/bank?from='+date+'&to='+date)['content']
    uploads=query('/api/v1/ai-review/uploads?from='+date+'&to='+date)['content']
    evidence=[{'id':'summary:IN','kind':'summary','data':summary['incoming']},{'id':'summary:OUT','kind':'summary','data':summary['outgoing']}]
    for kind,rows,key in [('business',business,'id'),('bank',banks,'id'),('upload',uploads,'uploadId')]:
        evidence += [{'id':f'{kind}:{r[key]}','kind':kind,'data':r} for r in rows]
    fixture={'date':date,'model':'ui-fixture-not-a-model','generatedAt':'2026-10-31T10:00:00Z','summary':summary,
      'facts':[{'text':'UI 검증용 고정 응답입니다. 실제 모델 답변이 아닙니다. 입금 합계는 일치하고 출금은 비교할 자료가 없습니다.','evidenceIds':['summary:IN','summary:OUT']}],
      'possibleCauses':[{'text':'추정: 합계가 같더라도 개별 거래의 구성이 같은지는 원본을 확인해야 합니다.','evidenceIds':['summary:IN']}],
      'checks':[{'text':'업무와 은행 원본의 식별값·행 번호를 확인하세요. <script>문자열도 텍스트로 표시합니다.</script>','evidenceIds':[evidence[-1]['id']]}],
      'evidence':evidence,'toolTrace':[],'limitations':['UI fixture — 실제 모델 호출 미수행']}
    checks=[];errors=[]
    async with async_playwright() as p:
        browser=await p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
        context=await browser.new_context(viewport={'width':1440,'height':1050},device_scale_factor=1)
        page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
        # Even if a key is configured later, this script cannot send a real review request.
        await context.route('**/api/v1/ai-review/reviews',lambda route:route.fulfill(status=503,json={'code':'UI_TEST_NO_REAL_CALL','message':'UI test blocks paid calls'}))
        await page.goto(base+'/ai-review.html?date='+date)
        await expect(page.locator('#summary-cards')).to_contain_text('합계 일치')
        await expect(page.locator('#summary-cards')).to_contain_text('비교할 자료 없음')
        await expect(page.locator('#originals-content')).to_contain_text('12,345')
        await expect(page.locator('#related-uploads')).to_contain_text('smoke')
        assert not await page.evaluate('document.documentElement.scrollWidth > innerWidth')
        await expect(page.locator('#load-day')).to_be_enabled()
        await page.locator('#review-date').focus()
        # Native date controls may expose separate month/day/year keyboard segments.
        for _ in range(6):
            await page.keyboard.press('Tab')
            if await page.locator('#load-day').evaluate('(e)=>e===document.activeElement'):break
        assert await page.locator('#load-day').evaluate('(e)=>e===document.activeElement')
        await page.evaluate("document.activeElement.blur();window.scrollTo({top:0,behavior:'instant'})")
        await page.screenshot(path=str(artifact('ai-review-desktop.png')),full_page=True)
        checks.append('actual app: summary, no-data distinction, source rows, uploads, keyboard focus, desktop overflow')
        await page.locator('#tab-business').focus();await page.keyboard.press('ArrowRight');await expect(page.locator('#tab-bank')).to_have_attribute('aria-selected','true')
        await page.locator('#direction').select_option('OUT');await expect(page.locator('#originals-content')).to_contain_text('없')
        checks.append('actual app: keyboard tabs and IN/OUT source filter')
        await page.locator('#review-date').fill('2026-10-30');await page.locator('#load-day').click()
        await expect(page.locator('#summary-cards')).to_contain_text('비교할 자료 없음')
        await expect(page.locator('#first-use')).to_be_visible()
        await page.evaluate("document.activeElement.blur();window.scrollTo({top:0,behavior:'instant'})")
        await page.screenshot(path=str(artifact('ai-review-no-data.png')),full_page=True)
        checks.append('actual app: empty date and first-upload guidance')
        await page.set_viewport_size({'width':390,'height':844})
        await page.goto(base+'/ai-review.html?date='+date)
        await expect(page.locator('#summary-cards')).to_contain_text('합계 일치')
        assert not await page.evaluate('document.documentElement.scrollWidth > innerWidth')
        await page.locator('.original-details summary').first.click()
        await expect(page.locator('.original-details dl').first).to_contain_text('유형')
        await expect(page.locator('.original-details dl').first).to_contain_text('충전 예정')
        await page.evaluate("document.activeElement.blur();window.scrollTo({top:0,behavior:'instant'})")
        await page.screenshot(path=str(artifact('ai-review-mobile.png')),full_page=True)
        checks.append('actual app: 390px responsive layout without page overflow; source type accessible in mobile details')
        await context.close()
        # Dedicated context for simulated AI states. Network interception is explicit and comprehensive.
        context=await browser.new_context(viewport={'width':1440,'height':1050})
        await context.route('**/api/v1/ai-review/capabilities',lambda route:route.fulfill(json={'configured':True,'model':'ui-fixture'}))
        mode={'value':'success'}
        async def ai_route(route):
            await asyncio.sleep(.25)
            if mode['value']=='error':await route.fulfill(status=502,json={'code':'AI_UPSTREAM_ERROR','message':'fixture error'})
            else:await route.fulfill(json=fixture)
        await context.route('**/api/v1/ai-review/reviews',ai_route)
        page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
        await page.goto(base+'/ai-review.html?date='+date);await expect(page.locator('#run-ai')).to_be_enabled()
        await page.locator('#run-ai').click();await expect(page.locator('#ai-results')).to_contain_text('UI 검증용 고정 응답')
        await expect(page.locator('#ai-results')).to_contain_text('<script>문자열')
        assert await page.locator('#ai-results script').count()==0
        await page.locator('.evidence-link').first.click()
        assert await page.locator('#evidence-summary-IN').get_attribute('open') is not None
        # Record a presentation screenshot separately from the hostile-text assertion.
        fixture['checks'][0]['text']='업무와 은행 원본의 식별값·행 번호와 관련 업로드를 확인하세요.'
        await page.locator('#run-ai').click()
        await expect(page.locator('#ai-results')).to_contain_text('관련 업로드를 확인하세요')
        await page.evaluate('document.activeElement.blur(); window.scrollTo(0, 0)')
        await page.wait_for_timeout(150)
        await page.evaluate("document.activeElement.blur();window.scrollTo({top:0,behavior:'instant'})")
        await page.screenshot(path=str(artifact('ai-review-fixture-result.png')),full_page=True)
        checks.append('fixture only: date → AI result → citation focus; untrusted output remains text')
        mode['value']='error';await page.locator('#run-ai').click();await expect(page.locator('#ai-status')).to_contain_text('준비하지 못했습니다')
        await page.evaluate('document.activeElement.blur();window.scrollTo(0,0)')
        await page.evaluate("document.activeElement.blur();window.scrollTo({top:0,behavior:'instant'})")
        await page.screenshot(path=str(artifact('ai-review-fixture-error.png')),full_page=True)
        mode['value']='success';await page.locator('#run-ai').click();await expect(page.locator('#ai-results')).to_contain_text('UI 검증용 고정 응답')
        checks.append('fixture only: upstream error and retry preserve summary')
        await page.locator('#run-ai').click();await page.locator('#review-date').fill('2026-10-30');await asyncio.sleep(.6)
        await expect(page.locator('#ai-results')).to_be_hidden();await expect(page.locator('#run-ai')).to_be_disabled()
        checks.append('fixture only: date change cancels/hides stale AI response')
        async def summary_error(route):await route.fulfill(status=503,json={'code':'UI_FIXTURE_ERROR'})
        await page.route('**/api/v1/ai-review/summary*',summary_error)
        await page.locator('#load-day').click();await expect(page.locator('#page-status')).to_contain_text('불러오지 못했습니다')
        await page.unroute('**/api/v1/ai-review/summary*',summary_error)
        await page.locator('#load-day').click();await expect(page.locator('#summary-cards')).to_contain_text('비교할 자료 없음')
        await expect(page.locator('a[download]')).to_have_count(2)
        await expect(page.locator('#first-use')).to_contain_text('UTF-8 CSV')
        await expect(page.locator('#first-use')).to_contain_text('우리은행')
        checks.append('fixture only: summary error and successful retry; example CSV links available')
        await context.close()

        # Concurrency regressions are isolated browser fixtures, never database mutations.
        context=await browser.new_context(viewport={'width':1440,'height':1050})
        await context.route('**/api/v1/ai-review/capabilities',lambda route:route.fulfill(json={'configured':True,'model':'ui-fixture'}))
        mode={'value':'success'};posts=[];reads={'business':0,'bank':0,'uploads':0}
        current=json.loads(json.dumps(summary));current['reviewStatus']='REVIEWED'
        changed=json.loads(json.dumps(summary));changed['reviewStatus']='NEEDS_RECHECK'
        changed['incoming'].update(expectedAmount='14345',difference='-2000',businessCount=2,comparisonStatus='TOTAL_DIFF')
        changed_rows=json.loads(json.dumps(business))
        added=dict(changed_rows[0]);added.update(id=900001,amount=2000,sourceLineId='UI-FIXTURE-ADDED',sourceRowNumber=3,note='UI 검증용 추가 원본')
        changed_rows.append(added)
        t1=json.loads(json.dumps(fixture));t1['summary']=changed
        t1['facts']=[{'text':'UI 검증용 고정 응답: 추가 원본을 조회한 새 요약 기준입니다.','evidenceIds':['summary:IN']}]
        t1['evidence'][0]['data']=changed['incoming']
        t1['evidence'].append({'id':'business:900001','kind':'business','data':added})
        async def summary_route(route):await route.fulfill(json=current)
        async def business_route(route):
            reads['business']+=1
            rows=changed_rows if mode['value']=='changed' else business
            await route.fulfill(json={'content':rows,'page':0,'size':10,'totalElements':len(rows)})
        async def bank_route(route):
            reads['bank']+=1
            await route.fulfill(json={'content':banks,'page':0,'size':10,'totalElements':len(banks)})
        async def uploads_route(route):
            reads['uploads']+=1
            await route.fulfill(json={'content':uploads,'page':0,'size':20,'totalElements':len(uploads)})
        async def review_route(route):
            posts.append(route.request.post_data_json)
            await asyncio.sleep(.35)
            if mode['value']=='conflict':
                await route.fulfill(status=409,json={'code':'AI_DATA_CHANGED','message':'fixture conflict'})
            else:
                mode['value']='changed'
                await route.fulfill(json=t1)
        await context.route('**/api/v1/ai-review/summary*',summary_route)
        await context.route('**/api/v1/ai-review/originals/business*',business_route)
        await context.route('**/api/v1/ai-review/originals/bank*',bank_route)
        await context.route('**/api/v1/ai-review/uploads*',uploads_route)
        await context.route('**/api/v1/ai-review/reviews',review_route)
        page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
        await page.goto(base+'/ai-review.html?date='+date)
        await expect(page.locator('#review-status')).to_have_class('status equal')
        await expect(page.locator('#run-ai')).to_be_enabled()
        before_reads=dict(reads)
        await page.locator('#run-ai').click()
        await expect(page.locator('#ai-status')).to_contain_text('이미 요청한 검토는 계속 처리될 수 있습니다')
        await expect(page.locator('#ai-results')).to_contain_text('새 요약 기준')
        await expect(page.locator('#summary-cards')).to_contain_text('14,345')
        await expect(page.locator('#summary-cards')).to_contain_text('−2,000')
        await expect(page.locator('#review-status')).to_have_text('재확인 필요')
        await expect(page.locator('#originals-content')).to_contain_text('UI 검증용 추가 원본')
        assert reads['business']>before_reads['business'] and reads['uploads']>before_reads['uploads'],reads
        checks.append('fixture only: returned Java summary replaces older page amounts; originals and uploads refresh before displaying review')
        checks.append('fixture only: progress wording does not promise cancellation of server processing or API charges')
        await page.evaluate('document.activeElement.blur();window.scrollTo(0,0)')
        await page.evaluate("document.activeElement.blur();window.scrollTo({top:0,behavior:'instant'})")
        await page.screenshot(path=str(artifact('ai-review-fixture-updated-summary.png')),full_page=True)
        mode['value']='conflict';current=changed
        await page.locator('#run-ai').click()
        await expect(page.locator('#reload-day-from-ai')).to_be_visible()
        await expect(page.locator('#run-ai')).to_be_disabled()
        await expect(page.locator('#ai-results')).to_be_hidden()
        before_posts=len(posts)
        await page.locator('#reload-day-from-ai').click()
        await expect(page.locator('#run-ai')).to_be_enabled()
        await expect(page.locator('#review-status')).to_have_text('재확인 필요')
        assert len(posts)==before_posts,'Reloading the day must not submit another model request'
        checks.append('fixture only: AI_DATA_CHANGED 409 requires a fresh day query and cannot automatically retry a paid review')
        await page.locator('#review-date').fill('2026-10-30')
        await expect(page.locator('#review-status')).to_have_text('미검토')
        await expect(page.locator('#review-status')).to_have_class('status neutral')
        checks.append('fixture only: changing date resets both review text and the previous status color')
        await context.close();await browser.close()
    assert not errors,errors
    result={'checks':checks,'passed':len(checks),'browser':'system Chromium via Playwright','actualModelCalled':False,'consoleErrors':errors}
    (artifact('browser-verification.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':asyncio.run(main())
