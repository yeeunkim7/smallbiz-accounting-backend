#!/usr/bin/env python3
"""Record actual local Expo browser + Java API, then compose a captioned portfolio MP4.

Requires Playwright, Pillow, Chromium, ffmpeg, ffprobe and the isolated local test DB.
Only this synthetic tenant is removed. Credentials and raw login footage stay outside Git.
"""
import argparse
import asyncio
import json
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.parse
import urllib.request
import uuid
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reedit', type=Path, help='Recompose an existing MP4 offline; preserves its phone area and timeline.')
parser.add_argument('--manifest', type=Path, help='Manifest for --reedit (defaults to the source directory).')
parser.add_argument('--api', default='http://127.0.0.1:8091')
parser.add_argument('--web', default='http://127.0.0.1:8081')
parser.add_argument('--output', type=Path, default=ROOT / 'docs/demo')
parser.add_argument('--raw-dir', type=Path, default=Path('/workspace/.local/run/portfolio-video'))
args = parser.parse_args()
API, WEB = args.api.rstrip('/'), args.web.rstrip('/')
for value in (API, WEB):
    u = urllib.parse.urlsplit(value)
    if u.scheme != 'http' or u.hostname not in ('localhost', '127.0.0.1') or u.username or u.password or u.path or u.query or u.fragment:
        raise SystemExit('Use local HTTP test servers only.')
db = urllib.parse.urlsplit(os.environ.get('TEST_DB_URL', '').removeprefix('jdbc:'))
if not args.reedit and (db.scheme != 'postgresql' or db.hostname not in ('localhost', '127.0.0.1') or db.path != '/reconciliation_test'):
    raise SystemExit('TEST_DB_URL must select local reconciliation_test.')
if not args.reedit and (not os.environ.get('TEST_DB_USERNAME') or not os.environ.get('TEST_DB_PASSWORD')):
    raise SystemExit('Configure test DB credentials securely first.')
OUT, RAW = args.output.resolve(), args.raw_dir.resolve()
if RAW == ROOT or ROOT in RAW.parents:
    raise SystemExit('Raw footage must be outside the repository (contains login frames).')
OUT.mkdir(parents=True, exist_ok=True)
RAW.mkdir(parents=True, exist_ok=True, mode=0o700)
os.chmod(RAW, 0o700)
owner = tenant = token = None
checks, scenes, errors = [], [], []

def api(path, body=None, key=None):
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    if key:
        headers['Idempotency-Key'] = key
    if body is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(API + '/api/v1' + path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.loads(response.read()) if response.status != 204 else None

def run(command):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=180)
    if result.returncode:
        raise RuntimeError('Media command failed: ' + result.stderr[-2000:])
    return result.stdout

def cleanup():
    if not owner or not tenant:
        return
    uuid.UUID(owner)
    uuid.UUID(tenant)
    tables = ['receivable_matching_usage_event', 'receivable_monthly_usage', 'receivable_matching_run', 'receivable_allocation_command', 'receivable_allocation_audit', 'receivable_allocation_cancellation', 'receivable_allocation_line', 'receivable_allocation_batch', 'receivable_registration_request', 'receivable_bank_import_request', 'receivable_bank_source_row', 'receivable_bank_transaction', 'receivable_bank_import', 'receivable_invoice', 'receivable_payment', 'receivable_counterparty']
    sql = 'BEGIN; SET CONSTRAINTS ALL DEFERRED;' + ''.join(f"DELETE FROM {t} WHERE tenant_id='{tenant}';" for t in tables) + f"DELETE FROM app_session WHERE account_id='{owner}'; DELETE FROM app_membership WHERE tenant_id='{tenant}'; DELETE FROM app_tenant WHERE id='{tenant}'; DELETE FROM app_account WHERE id='{owner}'; COMMIT;"
    result = subprocess.run(['psql', '-h', db.hostname, '-p', str(db.port or 5432), '-U', os.environ['TEST_DB_USERNAME'], '-d', 'reconciliation_test', '-v', 'ON_ERROR_STOP=1', '-q'], input=sql, text=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, env={**os.environ, 'PGPASSWORD': os.environ['TEST_DB_PASSWORD']}, timeout=30)
    if result.returncode:
        raise RuntimeError('Synthetic demo tenant cleanup failed.')
    checks.append('syntheticTenantRemoved')

async def record():
    from playwright.async_api import async_playwright, expect
    global owner, tenant, token
    login, password = 'portfolio-' + uuid.uuid4().hex, 'Synthetic-' + uuid.uuid4().hex
    session = api('/auth/signup', {'login': login, 'password': password, 'tenantName': '가상공방 · 포트폴리오'})
    owner, token = session['accountId'], session['accessToken']
    tenant = api('/auth/me')['tenants'][0]['id']
    prefix = '/tenants/' + tenant
    def create(resource, body):
        return api(prefix + '/' + resource, body, uuid.uuid4().hex)
    create('counterparties', {'code': 'SYNTHETIC', 'name': '합성 거래처'})
    def balance(expected):
        value = api(prefix + '/receivables/summary?asOf=2026-11-30')
        assert value['outstandingAmount'] == expected, 'Unexpected server balance'
        checks.append('serverOutstanding=' + expected)
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium'), headless=True, args=['--no-sandbox', '--disable-dev-shm-usage'])
        context = await browser.new_context(viewport={'width': 390, 'height': 844}, record_video_dir=str(RAW), record_video_size={'width': 390, 'height': 844}, locale='ko-KR', reduced_motion='reduce')
        started = time.monotonic()
        page = await context.new_page()
        page.set_default_timeout(15000)
        page.on('pageerror', lambda error: errors.append(str(error)))
        async def guard(route):
            u = urllib.parse.urlsplit(route.request.url)
            assert f'{u.scheme}://{u.netloc}' == API
            assert '/ai-review/' not in u.path
            await route.continue_()
        await context.route('**/api/**', guard)
        await page.goto(WEB, wait_until='networkidle')
        try:
            await page.get_by_label('아이디', exact=True).fill(login)
            await page.get_by_label('비밀번호', exact=True).fill(password)
            await page.get_by_role('button', name='로그인', exact=True).click()
        except Exception:
            raise RuntimeError('Off-camera demo authentication failed (details suppressed).') from None
        await expect(page.get_by_text('등록된 청구 없음', exact=True)).to_be_visible()
        await page.wait_for_timeout(1600)
        async def click(name, role='button'):
            loc = page.get_by_role(role, name=name, exact=True)
            await loc.scroll_into_view_if_needed()
            await page.wait_for_timeout(450)
            await loc.click()
            await page.wait_for_timeout(650)
        async def field(name, value):
            loc = page.get_by_label(name, exact=True)
            await loc.scroll_into_view_if_needed()
            await loc.fill(value)
            await page.wait_for_timeout(350)
        async def focus(text):
            await page.get_by_text(text, exact=True).first.evaluate("e=>e.scrollIntoView({block:'start',behavior:'instant'})")
            await page.evaluate('document.activeElement?.blur()')
            await page.wait_for_timeout(500)
        async def scene(title, subtitle, lines, duration, action):
            start = time.monotonic() - started
            await action()
            assert not await page.evaluate('document.documentElement.scrollWidth>innerWidth'), 'Horizontal overflow'
            await page.evaluate('document.activeElement?.blur()')
            elapsed = time.monotonic() - started - start
            await page.wait_for_timeout(max(0, duration - elapsed) * 1000)
            end = time.monotonic() - started
            scenes.append({'title': title, 'subtitle': subtitle, 'lines': lines, 'start': start, 'duration': end - start})
            print('Recorded chapter ' + str(len(scenes)), flush=True)
        async def invoice_form():
            await click('청구 추가')
            await page.get_by_role('radio', name=re.compile('합성 거래처')).click()
            await field('청구 이름·번호', '11월 합성 청구')
            await field('금액 (원)', '100000')
            await field('청구일', '2026-11-01')
            await field('입금 기한', '2026-11-30')
        await scene('청구 등록', '01', ['거래처와 청구 금액 100,000원을 입력합니다.'], 11, invoice_form)
        async def save_invoice():
            await click('청구 등록하기')
            await expect(page.get_by_text('청구를 등록했어요.', exact=False)).to_be_visible()
            await page.get_by_role('button', name=re.compile('합성 거래처, 11월 합성 청구')).click()
            await expect(page.get_by_text('현재 남은 금액', exact=True)).to_be_visible()
            balance('100000')
        await scene('청구 상세', '02', ['청구 100,000원과 남은 금액을 확인합니다.'], 9, save_invoice)
        await click('홈', 'tab')
        async def payment_form():
            await click('입금 추가')
            await field('금액 (원)', '40000')
            await field('입금일', '2026-11-01')
            await field('입금자 이름 (선택)', '합성 거래처')
            await click('입금 등록하기')
            await expect(page.get_by_text('입금을 등록했어요.', exact=False)).to_be_visible()
        await scene('입금 등록', '03', ['입금 40,000원을 등록합니다.'], 11, payment_form)
        create('payments', {'amount': '60000', 'bookedDate': '2026-11-02', 'payerName': '합성 거래처'})
        async def unallocated_home():
            await click('홈', 'tab')
            # Tab switch refreshes the server summary after the UI registration.
            await expect(page.get_by_text('100,000원', exact=True).first).to_be_visible()
            balance('100000')
        await scene('입금 내역', '04', ['입금 40,000원과 60,000원을 확인합니다.'], 9, unallocated_home)
        await click('입금 연결', 'tab')
        await field('입금 시작일', '2026-11-01')
        await field('입금 종료일', '2026-11-30')
        async def candidates():
            await click('후보 생성 · 성공 시 1회 사용')
            await expect(page.get_by_text('저장 당시 후보', exact=True)).to_be_visible()
            await focus('저장 당시 후보')
            assert api(prefix + '/matching-usage')['completed'] == 1
            assert len(api(prefix + '/allocations')['items']) == 0
            checks.append('oneRuleRunNoAutomaticAllocation')
        await scene('연결 후보', '05', ['입금과 청구의 연결 후보를 확인합니다.'], 11, candidates)
        async def add(amount):
            loc = page.get_by_role('button', name=re.compile(re.escape(amount + ' → 11월 합성 청구 초안에 담기') + '$'))
            await loc.scroll_into_view_if_needed()
            await page.wait_for_timeout(500)
            await loc.click()
            await page.wait_for_timeout(600)
        async def confirm():
            await click('현재 잔액 검토')
            await focus('확정 전 최종 검토')
            await page.wait_for_timeout(1800)
            await click('연결 확정')
            await expect(page.get_by_text('확정됨 · 현재 조회 버전 1', exact=True)).to_be_visible()
            await expect(page.get_by_text('연결한 입금·청구 표시 정보를 읽고 있어요.', exact=True)).to_have_count(0)
            await focus('확정 요청 후 현재 상태')
        async def first_confirm():
            await add('40,000원')
            await confirm()
            balance('60000')
        await scene('부분 입금 연결', '06', ['40,000원을 검토하고 연결합니다.'], 13, first_confirm)
        async def partial_home():
            await click('홈', 'tab')
            await expect(page.get_by_text('60,000원', exact=True).first).to_be_visible()
            balance('60000')
        await scene('남은 금액 확인', '07', ['40,000원 연결 후 미수 60,000원.'], 8, partial_home)
        await click('입금 연결', 'tab')
        async def second_confirm():
            await add('60,000원')
            await confirm()
            balance('0')
            await click('홈', 'tab')
            await expect(page.get_by_text('0원', exact=True).first).to_be_visible()
        await scene('입금 연결 완료', '08', ['나머지 60,000원 연결 후 미수 0원.'], 13, second_confirm)
        await click('입금 연결', 'tab')
        await page.get_by_label('연결 취소 사유 (필수)', exact=True).scroll_into_view_if_needed()
        async def cancel_second():
            await field('연결 취소 사유 (필수)', '합성 시연: 두 번째 연결 재검토')
            await click('전체 연결 취소 검토')
            await page.wait_for_timeout(1200)
            await click('연결 취소 확정')
            await expect(page.get_by_text('취소됨 · 현재 조회 버전 2', exact=True)).to_be_visible()
            await focus('현재 연결 상세')
            balance('60000')
            values = api(prefix + '/allocations')['items']
            assert len(values) == 2
            cancelled = [v for v in values if v['status'] == 'CANCELLED']
            assert len(cancelled) == 1 and len(cancelled[0]['audit']) == 2
            assert api(prefix + '/matching-usage')['completed'] == 1
            checks.append('cancellationAuditTwoEventsAndUsageStillOne')
        await scene('연결 취소', '09', ['60,000원 연결을 사유와 함께 취소합니다.'], 12, cancel_second)
        async def restored():
            await click('홈', 'tab')
            await expect(page.get_by_text('60,000원', exact=True).first).to_be_visible()
            balance('60000')
        await scene('취소 후 잔액', '10', ['미수 잔액이 60,000원으로 복원됩니다.'], 9, restored)
        assert not errors, 'Browser page error'
        raw_path = await page.video.path()
        await context.close()
        await browser.close()
        return Path(raw_path)

FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
BOLD = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size, index=1)

def wrap(draw, text, size, width):
    lines, current = [], ''
    for char in text:
        if draw.textlength(current + char, font=font(size)) > width:
            lines.append(current)
            current = char
        else:
            current += char
    return lines + [current]

PRESENTATION = [
    ('청구 등록', '거래처와 청구 금액 100,000원을 입력합니다.'),
    ('청구 상세', '청구 100,000원과 남은 금액을 확인합니다.'),
    ('입금 등록', '입금 40,000원을 등록합니다.'),
    ('입금 내역', '입금 40,000원과 60,000원을 확인합니다.'),
    ('연결 후보', '입금과 청구의 연결 후보를 확인합니다.'),
    ('부분 입금 연결', '40,000원을 검토하고 연결합니다.'),
    ('남은 금액 확인', '40,000원 연결 후 미수 60,000원.'),
    ('입금 연결 완료', '나머지 60,000원 연결 후 미수 0원.'),
    ('연결 취소', '60,000원 연결을 사유와 함께 취소합니다.'),
    ('취소 후 잔액', '미수 잔액이 60,000원으로 복원됩니다.'),
]

def presentation(scene, index):
    title, result = PRESENTATION[index]
    return {**scene, 'title': title, 'lines': [result]}

def frame(scene, index):
    scene = presentation(scene, index)
    image = Image.new('RGB', (1280, 960), '#F3F5F1')
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 1280, 12), fill='#086255')
    d.text((66, 66), '차곡', font=font(28, True), fill='#086255')
    d.text((66, 313), f'{index + 1:02d}', font=font(20), fill='#60796F')
    d.text((66, 363), scene['title'], font=font(42, True), fill='#173B34')
    for n, line in enumerate(wrap(d, scene['lines'][0], 23, 610)):
        d.text((66, 451 + n * 37), line, font=font(23), fill='#4C655D')
    for n in range(10):
        x = 66 + n * 61
        d.rounded_rectangle((x, 872, x + 45, 878), radius=3, fill='#086255' if n <= index else '#D5DFD8')
    d.rounded_rectangle((783, 39, 1207, 915), radius=28, fill='#173B34')
    path = RAW / f'frame-{index:02d}.png'
    image.save(path)
    return path

def srt_time(seconds):
    ms = round(seconds * 1000)
    return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'

def compose(raw_path):
    outputs, subtitles, timeline = [], [], 0
    for i, original in enumerate(scenes):
        scene = presentation(original, i)
        scenes[i] = scene
        backdrop = frame(scene, i)
        output = RAW / f'chapter-{i:02d}.mp4'
        duration = round(scene['duration'] * 25) / 25
        scene['duration'] = duration
        run(['ffmpeg', '-y', '-v', 'error', '-ss', f"{scene['start']:.3f}", '-t', f'{duration:.3f}', '-i', str(raw_path), '-loop', '1', '-i', str(backdrop), '-filter_complex', '[0:v]fps=25,setpts=PTS-STARTPTS[phone];[1:v][phone]overlay=800:55:shortest=1,format=yuv420p[out]', '-map', '[out]', '-t', f'{duration:.3f}', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '25', '-an', '-movflags', '+faststart', str(output)])
        outputs.append(output)
        subtitles.append(f"{i+1}\n{srt_time(timeline)} --> {srt_time(timeline+duration)}\n{scene['title']}\n" + '\n'.join(scene['lines']) + '\n')
        scene['outputStart'] = round(timeline, 3)
        timeline += duration
    concat = RAW / 'concat.txt'
    concat.write_text(''.join("file '" + str(path).replace("'", "'\\''") + "'\n" for path in outputs))
    final = OUT / 'receivables-demo.mp4'
    run(['ffmpeg', '-y', '-v', 'error', '-f', 'concat', '-safe', '0', '-i', str(concat), '-c', 'copy', '-movflags', '+faststart', str(final)])
    (OUT / 'captions.srt').write_text('\n'.join(subtitles), encoding='utf-8')
    run(['ffmpeg', '-y', '-v', 'error', '-ss', '67', '-i', str(final), '-frames:v', '1', str(OUT / 'poster.png')])
    info = json.loads(run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration,size:stream=codec_name,width,height,pix_fmt,avg_frame_rate', '-of', 'json', str(final)]))
    assert 90 <= float(info['format']['duration']) <= 120
    stream = info['streams'][0]
    assert (stream['codec_name'], stream['width'], stream['height'], stream['pix_fmt']) == ('h264', 1280, 960, 'yuv420p')
    run(['ffmpeg', '-v', 'error', '-i', str(final), '-f', 'null', '-'])
    checks.append('completeVideoDecode')
    assert final.stat().st_size < 50 * 1024 * 1024
    manifest = {'sourceCommit': run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).strip(), 'disclosure': '브라우저 모바일 화면 · 합성 자료 · 실제 API 연결 · 음성 없음(자막) · 대기 구간 편집', 'seedOutsideCamera': ['합성 계정과 사업장', '거래처', '두 번째 60,000원 입금'], 'checks': checks, 'pageErrors': len(errors), 'media': info, 'chapters': [{'start': s['outputStart'], 'duration': round(s['duration'], 3), 'title': s['title']} for s in scenes]}
    (OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': 'complete', 'duration': info['format']['duration'], 'bytes': final.stat().st_size, 'chapters': len(scenes), 'checks': checks}, ensure_ascii=False))

def reedit(source):
    source = source.resolve()
    manifest_path = args.manifest or source.with_name('manifest.json')
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    chapters = manifest['chapters']
    if len(chapters) != len(PRESENTATION):
        raise SystemExit('Expected the existing ten-chapter demo.')
    source_info = json.loads(run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration:stream=width,height,avg_frame_rate', '-of', 'json', str(source)]))
    stream = source_info['streams'][0]
    if (stream['width'], stream['height'], stream['avg_frame_rate']) != (1280, 960, '25/1'):
        raise SystemExit('Expected the original 1280 × 960, 25 fps demo.')
    duration = float(source_info['format']['duration'])
    if abs(sum(c['duration'] for c in chapters) - duration) > 0.001:
        raise SystemExit('Chapter timeline must cover the complete source.')
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    backdrops, subtitles = [], []
    for i, chapter in enumerate(chapters):
        scene = presentation(chapter, i)
        backdrops.append((frame(scene, i), chapter['duration']))
        subtitles.append(f"{i+1}\n{srt_time(chapter['start'])} --> {srt_time(chapter['start']+chapter['duration'])}\n{scene['title']}\n{scene['lines'][0]}\n")
        chapter.update(title=scene['title'], result=scene['lines'][0])
    concat = RAW / 'clean-backdrops.txt'
    def entry(path):
        return "file '" + str(path).replace("'", "'\\''") + "'\n"
    concat.write_text(''.join(entry(p) + f'duration {d}\n' for p, d in backdrops) + entry(backdrops[-1][0]))
    edited = RAW / 'clean-demo.mp4'
    run(['ffmpeg', '-y', '-v', 'error', '-i', str(source), '-f', 'concat', '-safe', '0', '-i', str(concat), '-filter_complex', '[0:v]crop=390:844:800:54:exact=1[phone];[1:v]fps=25,setpts=PTS-STARTPTS[backdrop];[backdrop][phone]overlay=800:54:eof_action=repeat,format=yuv420p[out]', '-map', '[out]', '-frames:v', str(round(duration * 25)), '-r', '25', '-fps_mode', 'cfr', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '18', '-an', '-movflags', '+faststart', str(edited)])
    edited_info = json.loads(run(['ffprobe', '-v', 'error', '-count_frames', '-show_entries', 'format=duration:stream=nb_read_frames,avg_frame_rate', '-of', 'json', str(edited)]))
    assert float(edited_info['format']['duration']) == duration
    assert edited_info['streams'][0]['avg_frame_rate'] == '25/1'
    assert int(edited_info['streams'][0]['nb_read_frames']) == round(duration * 25)
    run(['ffmpeg', '-v', 'error', '-i', str(edited), '-f', 'null', '-'])
    final = OUT / 'receivables-demo.mp4'
    shutil.copyfile(edited, final)
    (OUT / 'captions.srt').write_text('\n'.join(subtitles), encoding='utf-8')
    run(['ffmpeg', '-y', '-v', 'error', '-ss', '67', '-i', str(final), '-frames:v', '1', str(OUT / 'poster.png')])
    manifest['media'] = json.loads(run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration,size:stream=codec_name,width,height,pix_fmt,avg_frame_rate', '-of', 'json', str(final)]))
    manifest['presentationEdit'] = {'sourceSha256': source_hash, 'phoneRegion': {'x': 800, 'y': 54, 'width': 390, 'height': 844}, 'timeline': 'Preserved all source frames at 25 fps; replaced presentation backdrop only.', 'command': 'python scripts/record-portfolio-demo.py --reedit SOURCE.mp4 --manifest SOURCE_MANIFEST.json'}
    manifest['checks'] = [c for c in manifest['checks'] if c != 'sixSampleFramesReviewed'] + ['offlinePresentationReedit', 'completeEditedVideoDecode', 'completeSourceFrameCountPreserved']
    (OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'complete', 'duration': duration, 'bytes': final.stat().st_size, 'chapters': len(chapters)}))

if args.reedit:
    reedit(args.reedit)
else:
    try:
        raw = asyncio.run(record())
    finally:
        cleanup()
    compose(raw)
