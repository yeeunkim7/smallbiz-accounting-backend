#!/usr/bin/env python3
"""Build the editable five-slide Korean project insert from repository evidence.

Requires python-pptx and Pillow. Export PDF with LibreOffice after building.
The source portfolio is used only as a visual reference and is never imported.
"""
from io import BytesIO
from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'portfolio'
REPO = 'https://github.com/yeeunkim7/smallbiz-accounting-backend'
BASE = REPO + '/blob/feat/mobile-matching-review/'
VIDEO = BASE + 'docs/demo/receivables-demo.mp4'
FONT = 'Noto Sans CJK KR'
C = dict(bg='FFFFFF', navy='23334D', text='34435A', muted='65738A', blue='3A59D1',
         pale='F1F4FD', line='DDE4F0', light='F7F9FC', green='087C70')

prs = Presentation()
prs.slide_width, prs.slide_height = Inches(13.333333), Inches(7.5)
props = prs.core_properties
props.title = '차곡 프로젝트 포트폴리오'
props.subject = '청구·입금 연결과 미수금 확인 서비스 · 프로젝트 02'
props.author = '차곡'
props.keywords = '차곡, Java, Spring Boot, PostgreSQL, Expo, AI 에이전트 협업'
props.comments = 'Repository-based editable project insert. Evidence dates and scope are in speaker notes.'


def rgb(key):
    return RGBColor.from_string(C.get(key, key))


def rect(s, x, y, w, h, fill='light', line=None, radius=False):
    sh = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE,
                            Inches(x), Inches(y), Inches(w), Inches(h))
    sh.fill.solid(); sh.fill.fore_color.rgb = rgb(fill)
    if line:
        sh.line.color.rgb = rgb(line); sh.line.width = Pt(1)
    else:
        sh.line.fill.background()
    if radius:
        sh.adjustments[0] = .08
    return sh


def text(s, content, x, y, w, h, size=20, color='text', bold=False,
         align=PP_ALIGN.LEFT, valign=MSO_ANCHOR.TOP, link=None):
    sh = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = sh.text_frame
    tf.clear(); tf.word_wrap = True
    tf.margin_left = tf.margin_right = 0
    tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = valign
    for i, line in enumerate(content.split('\n')):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align; p.space_after = Pt(3)
        p.line_spacing = 1.15
        r = p.add_run(); r.text = line
        r.font.name = FONT; r.font.size = Pt(size); r.font.bold = bold
        r.font.color.rgb = rgb(color)
    if link:
        sh.click_action.hyperlink.address = link
    return sh


def line(s, x1, y1, x2, y2, color='line', width=1.5):
    sh = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    sh.line.color.rgb = rgb(color); sh.line.width = Pt(width)
    return sh


def arrow(s, x, y, w=.4, h=.25):
    return rect_arrow(s, x, y, w, h)


def rect_arrow(s, x, y, w, h):
    sh = s.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, Inches(x), Inches(y), Inches(w), Inches(h))
    sh.fill.solid(); sh.fill.fore_color.rgb = rgb('blue'); sh.line.fill.background()
    return sh


def base(title, sub=None, ai=False):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid(); s.background.fill.fore_color.rgb = rgb('bg')
    text(s, 'PROJECT 02', .7, .36, 3, .35, 18, 'blue', True)
    if ai:
        rect(s, 9.16, .27, 3.47, .49, 'pale', radius=True)
        text(s, 'AI 에이전트 협업 개발', 9.3, .34, 3.2, .35, 18, 'blue', True,
             align=PP_ALIGN.CENTER)
    text(s, title, .7, .94, 12, .65, 32, 'navy', True)
    if sub:
        text(s, sub, .72, 1.64, 11.9, .45, 20, 'muted')
    rect(s, .7, 7.12, 11.93, .035, 'blue')
    return s


def note(s, content):
    s.notes_slide.notes_text_frame.text = content


def image(s, path, x, y, w=None, h=None, crop=None, link=None):
    im = Image.open(ROOT / path)
    if crop:
        im = im.crop(crop)
    buf = BytesIO(); im.save(buf, format='PNG'); buf.seek(0)
    shape = s.shapes.add_picture(buf, Inches(x), Inches(y),
                                 width=Inches(w) if w else None,
                                 height=Inches(h) if h else None)
    if link:
        shape.click_action.hyperlink.address = link
    return shape


# 1. Product overview: actual server-connected browser UI, cropped only for legibility.
s = base('차곡 | 미수금·입금 확인', ai=True)
text(s, '청구와 입금을 연결하고,\n남은 금액을 확인합니다.', .72, 1.98, 7.7, 1.15, 28, 'navy', True)
text(s, '부분 입금과 묶음 입금이 생기는 후불 거래에서\n근거와 현재 잔액을 보고 사용자가 직접 확정합니다.',
     .74, 3.25, 7.55, .92, 20)
steps = [('01', '청구·입금 등록'), ('02', '연결 후보 검토'), ('03', '직접 확정·취소')]
for i, (num, label) in enumerate(steps):
    x = .74 + i * 2.59
    rect(s, x, 4.55, 2.38, .91, 'pale', radius=True)
    text(s, num, x + .14, 4.67, .4, .35, 18, 'blue', True)
    text(s, label, x + .14, 5.04, 2.15, .36, 18, 'navy', True)
text(s, '서버  Java 21 · Spring Boot · PostgreSQL · Flyway', .74, 5.97, 7.7, .42, 18)
text(s, '앱  React Native · Expo · TypeScript', .74, 6.42, 7.7, .42, 18)
rect(s, 9.09, 1.89, 3.48, 4.98, 'light', 'line', radius=True)
# Crop to the complete balance card; preserve all original financial values.
image(s, 'docs/screenshots/mobile-core-live-home.png', 9.19, 1.99, w=3.28, h=4.84,
      crop=(0, 80, 390, 655))
note(s, '개요 근거: README.md, docs/PORTFOLIO.md, mobile/README.md. 차곡은 기록을 차곡차곡 정리한다는 이름이다. '
     '화면은 docs/screenshots/mobile-core-live-home.png의 상단을 크롭한 실제 Chromium 모바일 viewport 캡처이며 합성 테스트 자료/실제 Java API 연결이다. '
     '캡처의 100,000원 미수와 미연결 입금 40,000원은 배분 전 서로 다른 수치이며 재구성하지 않았다. '
     '개발은 사용자 요구·제약·우선순위에 따라 AI 에이전트의 분석·구현·독립 검토를 활용했다. 개인의 수동 코딩 비율은 주장하지 않는다. '
     '앱 소스는 iOS/Android 공용 React Native/Expo이며 이 캡처는 네이티브 실행 증거가 아니다.')

# 2. Shared architecture and the financial domain, all editable PowerPoint shapes.
s = base('아키텍처와 금융 도메인', '화면은 입력·검토를, 서버와 DB는 소유권·금액·확정을 담당합니다.')
for x, title, body in [(.74, 'React Native · Expo', '입력 · 검토 · 사용자 확정'),
                       (4.91, 'Java · Spring Boot', '인증 · 권한 · 잔액 검사'),
                       (9.08, 'PostgreSQL · Flyway', '원본 · 배분 · 취소 이력')]:
    rect(s, x, 2.42, 3.51, 1.23, 'pale', radius=True)
    text(s, title, x+.18, 2.62, 3.15, .4, 20, 'navy', True)
    text(s, body, x+.18, 3.13, 3.18, .36, 18)
arrow(s, 4.41, 2.92); arrow(s, 8.58, 2.92)
text(s, '청구와 입금 사이에 배분 원장을 둡니다.', .75, 4.03, 11.9, .48, 23, 'navy', True)
for y, title in [(4.93, '청구  Invoice'), (5.86, '입금  Payment')]:
    rect(s, .75, y, 2.55, .68, 'light', 'line', True)
    text(s, title, .92, y+.17, 2.2, .36, 19, 'navy', True)
line(s, 3.3, 5.27, 3.65, 5.27, 'blue'); line(s, 3.3, 6.2, 3.65, 6.2, 'blue')
line(s, 3.65, 5.27, 3.65, 6.2, 'blue'); arrow(s, 3.65, 5.61, .44)
rect(s, 4.23, 5.01, 3.15, 1.36, 'pale', radius=True)
text(s, '배분  Allocation', 4.43, 5.23, 2.75, .4, 21, 'blue', True)
text(s, '부분 · 묶음 · 분할 연결', 4.43, 5.83, 2.75, .36, 18)
arrow(s, 7.62, 5.61, .45)
rect(s, 8.3, 4.93, 4.28, 1.61, 'light', 'line', True)
text(s, '활성 배분 → 현재 잔액', 8.52, 5.15, 3.84, .4, 21, 'navy', True)
text(s, '취소 이력 추가 → 잔액 복구', 8.52, 5.84, 3.84, .4, 19)
note(s, '근거: docs/architecture.md, docs/receivables-erd.md, AllocationService.java. '
     '계정 세션 및 tenant membership을 서버에서 확인한다. 금액은 PostgreSQL BIGINT 원화 정수, Java BigInteger 정확 합산, API decimal string이다. '
     '취소는 원본/배분 행 삭제가 아닌 별도 cancellation 및 audit 이벤트 추가다. 배분되지 않은 입금은 미수를 줄이지 않는다. '
     '기존 전역 대사 모듈은 별도 테이블에 유지되며 이 그림은 현재 tenant 미수금 흐름을 요약한다. '
     'iOS/Android 공용 클라이언트 소스를 유지하며 이번 확인은 브라우저 경로다.')

# 3. Concrete integrity mechanisms and the actual uncertain-response test.
s = base('문제 해결 | 동시 요청과 응답 유실', '한 입금을 초과 배분하거나, 같은 요청이 중복 저장되지 않도록 설계했습니다.')
rect(s, .74, 2.38, 5.75, 4.25, 'light', radius=True)
rect(s, 6.81, 2.38, 5.78, 4.25, 'pale', radius=True)
text(s, '동시 요청의 초과 배분 방지', .98, 2.65, 5.24, .43, 23, 'navy', True)
for y, n, t in [(3.48, '01', '입금 → 청구, 같은 순서로 잠금'),
                 (4.37, '02', '현재 잔액과 요청 합계 재검사'),
                 (5.26, '03', '가용액 초과 시 전체 롤백')]:
    text(s, n, 1.0, y, .5, .4, 20, 'blue', True)
    text(s, t, 1.62, y, 4.42, .45, 20)
text(s, '응답을 받지 못해도 같은 요청으로', 7.05, 2.65, 5.3, .43, 23, 'navy', True)
text(s, 'Idempotency-Key + 정규화 요청 해시', 7.07, 3.4, 5.03, .4, 20, 'blue', True)
text(s, '실제 저장 후 응답만 503으로 변경\n탭 왕복 후 같은 키로 다시 확정', 7.07, 4.05, 4.99, .98, 20)
text(s, '요청 2회', 7.07, 5.48, 2.0, .6, 28, 'navy', True)
arrow(s, 9.29, 5.65, .5)
text(s, '배분 1개', 10.0, 5.48, 2.1, .6, 28, 'blue', True)
note(s, '근거: docs/architecture.md, docs/receivables-mobile-matching-review.md 및 '
     'scripts/verify-mobile-matching-browser.py actualPartialConfirmUncertainResponseSameKeyAcrossTabsNoDuplicate. '
     '실제 local reconciliation_test Java API에 40,000원 부분 연결 commit 후 첫 POST 응답만 Playwright route로 503으로 바꾼다. '
     '사용자가 계정 탭을 왕복한 뒤 같은 key로 재시도해 두 요청/한 allocation batch를 확인했다. '
     '실제 서버 테스트의 초기 미수는 두 청구 총 150,000원이고 부분 연결 후 110,000원, 취소 후 150,000원이다. '
     '데모 영상의 단일 청구 100,000원 시나리오와 구분한다. 잠금은 부모 입금·청구 ID 순서로 일관되게 수행한다. '
     '같은 키에 다른 내용을 보내면 충돌 처리한다. 저장된 명령 응답과 현재 상태 조회를 분리한다. '
     '확정 재생 응답이 과거 v1이더라도 현재 GET은 취소 v2를 반환할 수 있다. 성능 수치나 처리량 주장은 하지 않는다. '
     '검증 기록 기준일 2026-11-01.')

# 4. Authorship and evidence-based agent workflow.
s = base('AI 에이전트를 활용한 개발', '사용자가 요구·제약·우선순위를 정하고, 에이전트의 결과를 실행 근거로 검토했습니다.')
labels = [('요구·제약', '사용자'), ('분석', '설계·작업 분리'), ('구현', '기능 단위 변경'),
          ('독립 검토', '결함·경계 확인'), ('실행 검증', 'DB·브라우저')]
for i, (title, body) in enumerate(labels):
    x = .75 + 2.43*i
    rect(s, x, 2.46, 2.14, 1.29, 'pale' if i != 0 else 'blue', radius=True)
    text(s, title, x+.12, 2.68, 1.9, .42, 22, 'FFFFFF' if i == 0 else 'navy', True,
         align=PP_ALIGN.CENTER)
    text(s, body, x+.08, 3.23, 1.98, .34, 18, 'FFFFFF' if i == 0 else 'text', align=PP_ALIGN.CENTER)
    if i < 4:
        arrow(s, x+2.19, 2.99, .2, .19)
text(s, '실제 발견과 수정', .76, 4.13, 11.9, .45, 23, 'navy', True)
for x, title, lines in [(.75, '브라우저에서 로그인 요청 실패',
                        'fetch 호출의 receiver 문제 발견\nlexical wrapper로 호출 방식 수정\n실제 화면 전환·회귀 검사 확인'),
                       (6.82, '응답 유실 뒤 중복 저장 위험',
                        '공백 수정으로 요청 키 변경 발견\n정규화한 내용으로 같은 키 유지\n실제 저장 → 503 → 같은 키 재전송')]:
    rect(s, x, 4.83, 5.76, 1.93, 'light', 'line', True)
    text(s, title, x+.2, 5.03, 5.33, .4, 21, 'blue', True)
    text(s, lines, x+.2, 5.62, 5.33, 1.01, 18)
note(s, '개발 방식 근거: docs/AI_REVIEW_WORK.md, docs/receivables-mobile-core-review.md, '
     'docs/receivables-mobile-matching-review.md. 사용자 역할은 요구사항·제품 범위·제약·우선순위 결정이며 '
     'AI 에이전트는 분석·구현·독립 검토와 실행 검증을 수행했다. 개인이 직접 코드를 작성한 비율 또는 전부 수동 구현했다는 주장은 하지 않는다. '
     '사례1: raw fetch를 객체 메서드로 호출하면 Chromium Illegal invocation 발생, mobile/src/api.ts의 (...args) => fetch(...args) wrapper로 수정. '
     '사례2: 불명확한 등록 POST 뒤 공백만 수정해도 동일 정규화 payload key가 바뀌는 결함을 수정하고 실제 commit 뒤 503 응답/같은 key/입금 1건을 확인했다. '
     '검증 기록은 2026-11-01. 서비스 자체의 현재 FREE 미수금 기능은 모델 호출 없이 Java/DB로 계산·확정한다. '
     'AI를 활용한 개발 방법과 제품 내 AI 기능을 혼동하지 않는다.')

# 5. A large actual demo poster and clearly separated verification categories.
s = base('시연과 검증', '청구 등록부터 부분 연결·완납·취소 후 잔액 복구까지 실제 API로 확인했습니다.')
rect(s, .74, 2.32, 6.42, 4.42, 'light', 'line', True)
image(s, 'docs/demo/poster.png', 1.13, 2.41, w=5.64, h=4.23, link=VIDEO)
text(s, '검증 결과', 7.66, 2.37, 4.9, .45, 24, 'navy', True)
metrics=[('186개', '백엔드 테스트'), ('13개', '클라이언트 테스트'), ('9개 + 9개', '실제 브라우저·API 흐름 / 기존 화면')]
for i, (value, label) in enumerate(metrics):
    y = 3.09 + .85*i
    text(s, value, 7.67, y, 4.78, .49, 27, 'blue', True)
    text(s, label, 7.68, y+.49, 4.85, .34, 18)
button=rect(s, 7.67, 5.99, 4.91, .64, 'blue', radius=True)
button.click_action.hyperlink.address = VIDEO
text(s, '시연 영상 열기 · 1분 46초', 7.86, 6.13, 4.53, .37, 20, 'FFFFFF', True,
     align=PP_ALIGN.CENTER, link=VIDEO)
note(s, '영상: ' + VIDEO + '\n'
     '포스터 원본 docs/demo/poster.png. 실제 영상 docs/demo/receivables-demo.mp4 길이 106.000초, H.264 1280×960, '
     '브라우저 모바일 viewport·합성 자료·실제 API 연결·자막/무음·대기 구간 편집이다. '
     '데모는 청구 100,000원에 40,000원 연결 후 미수 60,000원, 남은 60,000원 연결 후 0원, 두 번째 연결 취소 후 60,000원 복구. '
     '합성 계정/거래처와 두 번째 입금은 미리 준비했다. poster 클릭과 파란 버튼에 GitHub branch MP4 링크를 넣었다. '
     '검증 근거: docs/PORTFOLIO.md, docs/RECEIVABLES_MOBILE_MATCHING.md, docs/receivables-mobile-matching-review.md. '
     '2026-11-01 실행 기록: backend 186개(실패/오류/skip 0), client 13개·typecheck·Expo web export 성공. '
     '실제 브라우저/API 연결 흐름 9개 + 기존 core 화면 회귀 9개, page errors 0. 별도 독립 UI fixture 11개는 합성 경합 경계 검사이며 이 숫자에 합산하지 않는다. '
     '이 장표를 만들면서 전체 테스트를 새로 실행한 것은 아니다. 다음 단계는 모바일 CSV 가져오기, iPhone/Android 실기기 확인, 앱인토스 적용 검토다. '
     '네이티브 iOS/Android 실행·스토어 제출·공개 배포·앱인토스 SDK 실행은 미검증/후속 범위다.')

OUT.mkdir(parents=True, exist_ok=True)
target = OUT / 'chagok-project.pptx'
prs.save(target)
print(target)
