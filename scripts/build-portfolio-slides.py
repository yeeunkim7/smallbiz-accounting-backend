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
BASE = REPO + '/blob/main/'
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


# 1. Problem, choice of development environment, and actual product screen.
s = base('차곡 | AI 에이전트와 개발한 대사 시스템', ai=True)
text(s, '하루의 금액 비교에서\n청구별 미수금 확인으로', .75, 1.99, 7.65, 1.1, 28, 'navy', True)
text(s, '합계가 같아도 어떤 청구가 지급됐는지는 알 수 없습니다.\n기존 Java 대사 기능을 보존하고 청구·입금 연결로 확장했습니다.', .77, 3.23, 7.65, .92, 19)
text(s, 'Codex를 선택한 이유', .77, 4.42, 7.5, .43, 23, 'blue', True)
for i, label in enumerate(['기존 코드·DB·테스트를 같은 작업 공간에서 분석',
                           '수정 → 명령 실행 → 검증 → Git 기록까지 연결',
                           '총괄과 하위 에이전트의 결과를 한 프로젝트에 통합']):
    text(s, label, .79, 5.03+.49*i, 7.5, .39, 18)
rect(s, 9.09, 1.93, 3.48, 4.99, 'light', 'line', True)
image(s, 'docs/screenshots/mobile-core-live-home.png', 9.19, 2.03, w=3.28, h=4.84, crop=(0,80,390,655))
note(s, '발표: 기존 입출금 대사는 날짜별 금액 차이를 확인하는 시스템이었습니다. 저는 이 프로젝트를 소규모 사업자의 청구별 미수금 확인으로 확장하도록 목표를 정했습니다. '
     '기존 코드를 보존하면서 분석, 수정, 테스트 실행을 이어갈 수 있어 Codex를 활용했습니다. 총괄 아래 역할을 나누고 그 결과를 같은 저장소에 통합하는 방식으로 진행했습니다. '
     '근거: README.md, docs/PORTFOLIO.md. 캡처는 합성 자료/실제 Java API에 연결한 Chromium 모바일 viewport이며 실기기 증거가 아닙니다. '
     '다른 AI 도구와의 성능 비교나 생산성 배수는 측정하지 않았습니다.')

# 2. Development organization: developer decisions and delegated execution.
s = base('총괄 아래 역할을 나누고, 검토 결과를 다시 구현에 반영', '개발자: 목표·업무 규칙·완료 기준  /  에이전트: 분석·설계·구현·검증 실행')
rect(s, 3.82, 2.35, 5.68, .69, 'blue', radius=True)
text(s, '총괄 에이전트 · 작업 배분과 결과 통합', 3.95, 2.53, 5.42, .36, 20, 'FFFFFF', True, align=PP_ALIGN.CENTER)
line(s, 6.66, 3.04, 6.66, 3.34, 'blue')
line(s, 1.81, 3.34, 11.53, 3.34, 'blue')
roles=[('기획·분석','기존 코드·DB\n범위·도메인'),('자료조사','사용자 문제\n대체 서비스'),('디자인','업무·화면 흐름\n상태·근거 표시'),('개발','기능별 변경\nAPI·모바일'),('독립 검토','오류 재현\n수정·재검증')]
for i,(title,body) in enumerate(roles):
    x=.75+2.43*i
    line(s,x+1.06,3.34,x+1.06,3.6,'blue')
    rect(s,x,3.6,2.11,1.49,'light','line',True)
    text(s,title,x+.1,3.78,1.91,.4,21,'blue',True,align=PP_ALIGN.CENTER)
    text(s,body,x+.1,4.28,1.91,.66,17,align=PP_ALIGN.CENTER)
text(s,'조사·분석 → 범위·설계 → 기능별 구현 → 독립 검토 → 수정·재검증', .8,5.5,11.7,.5,21,'navy',True,align=PP_ALIGN.CENTER)
text(s,'업무 원칙  |  Java 금액 계산 · 사용자 확정 · 원본 보존 · FREE AI 호출 없음', .81,6.27,11.65,.45,19)
note(s, '발표: 저는 업무 규칙과 완료 기준을 정하고, 총괄이 기획·조사·디자인·개발·검토를 조율하도록 요청했습니다. '
     '조사와 코드 분석처럼 독립적인 일은 병행하고, 화면 구현은 도메인과 API 설계 결과를 받아 연결했습니다. 구현 담당과 검토 담당을 나누고 발견된 오류를 다시 수정하도록 했습니다. '
     '역할은 책임 구분이며 모든 역할이 동시에 상시 실행된 것은 아닙니다. 기획은 총괄·업무 분석과 함께 수행했습니다. '
     '구체 DDL과 잠금 전략은 에이전트 설계/구현이며 사용자 직접 선택으로 소급하지 않습니다. 근거: docs/AI_REVIEW_WORK.md, AGENTS.md, docs/adr/0002-receivables-decisions.md.')

# 3. Runtime architecture and the resulting features.
s = base('결과물 | 청구·입금 연결과 미수금 확인', '기존 대사 데이터를 보존하고, 사업체별 청구·입금·배분 모델을 추가했습니다.')
for x,title,body in [(.75,'React Native · Expo','입력 · 근거 확인 · 확정 요청'),(4.91,'Java · Spring Boot','권한 · 잔액 검사 · 확정'),(9.07,'PostgreSQL · Flyway','원본 · 배분 · 취소 · 감사')]:
    rect(s,x,2.39,3.51,1.25,'pale',radius=True)
    text(s,title,x+.16,2.61,3.19,.4,20,'navy',True)
    text(s,body,x+.16,3.13,3.19,.35,17)
arrow(s,4.4,2.91);arrow(s,8.56,2.91)
text(s,'서버 호출 흐름  Controller → Service → JdbcTemplate(SQL) → DB',.78,3.98,11.8,.45,20,'muted')
features=[('등록과 원본','청구·입금 직접 등록\n은행 CSV API·중복 추적'),('연결과 잔액','부분·묶음·분할 입금\n미수·기한 초과 잔액'),('취소와 권한','사유 있는 취소·감사\n사업체 격리·성공 이용량')]
for i,(title,body) in enumerate(features):
    x=.75+4.16*i
    rect(s,x,4.7,3.51,1.5,'light','line',True)
    text(s,title,x+.18,4.88,3.15,.4,21,'blue',True)
    text(s,body,x+.18,5.41,3.15,.64,18)
text(s,'청구 100,000원 → 40,000원 연결 → 미수 60,000원', .81,6.5,10,.38,21,'navy',True)
text(s,'시연 영상 ↗',10.7,6.5,1.88,.4,19,'blue',True,link=VIDEO)
note(s,'발표: 화면은 입력과 검토를 담당하고 금액과 확정은 서버·DB가 처리합니다. 신규 미수금 모듈은 Controller에서 Service, JdbcTemplate SQL로 이어집니다. '
     '기존 대사 모듈의 JPA Repository와 구분합니다. 입금을 등록하거나 후보를 만든 것만으로 미수가 줄지 않으며 사용자가 연결을 확정해야 반영됩니다. '
     '원본과 배분 line은 보존하고 취소·감사를 추가하며 조회 상태와 버전을 파생합니다. 은행 CSV는 API 구현이며 모바일 업로드 화면은 후속입니다. '
     '근거: docs/architecture.md, docs/receivables-erd.md, docs/RECEIVABLES_ALLOCATION.md. 영상: '+VIDEO)

# 4. Concrete defect, repair, and bounded verification evidence.
s=base('검토가 실제 수정으로 이어진 사례','서버가 저장한 뒤 응답이 끊기면, 재시도를 새 거래로 저장할 위험이 있습니다.')
steps=[('발견','공백만 수정해도\n요청 키가 바뀜'),('수정','정규화한 내용이 같으면\n같은 요청 키 유지'),('재검증','DB 저장 후 503 재현\n재시도해도 입금 1건')]
for i,(title,body) in enumerate(steps):
    x=.75+4.16*i
    rect(s,x,2.48,3.51,1.65,'light','line',True)
    text(s,title,x+.18,2.68,3.15,.42,23,'blue',True)
    text(s,body,x+.18,3.25,3.15,.7,19)
    if i<2:arrow(s,x+3.66,3.17)
text(s,'금액 정합성은 별도의 서버 검증으로 확인',.79,4.51,11.8,.46,23,'navy',True)
text(s,'같은 입금의 동시 배분 · 잔액 초과 거부 · 취소 복원 · 감사 저장 실패 롤백',.8,5.1,11.7,.45,20)
rect(s,.75,5.86,11.83,.93,'pale',radius=True)
text(s,'백엔드 186개  ·  클라이언트 13개  ·  실제 브라우저 9 + 9개',.97,6.04,11.4,.39,22,'blue',True)
text(s,'2026.11.01 실행 기록',.99,6.5,11.1,.29,16,'muted')
note(s,'발표: 검토에서 찾은 문제는 사용자 입력의 공백 변화가 멱등성 키까지 바꾸는 것이었습니다. 내용이 같아도 서버가 새 요청으로 볼 수 있어 정규화한 입력으로 키를 유지하도록 수정했습니다. '
     '에이전트가 실제 DB 저장 후 응답만 503으로 바꾸고 같은 키로 재시도해 입금 한 건을 확인했습니다. 발견·구현·테스트 실행은 에이전트가 수행했습니다. '
     '소스: mobile/src/forms.tsx Registration.submit, mobile/src/commands.ts CommandKey.forPayload, ReceivablesService.replayOrCreate. '
     '동시 배분과 롤백은 AllocationIntegrationTest로 별도 검증했습니다. 숫자는 2026-11-01 기록이며 이번 PPT 작업의 새 실행 결과가 아닙니다. '
     '실제 브라우저는 신규 matching9+core9이며 독립 UI fixture11과 구분합니다. docs/receivables-mobile-core-review.md, docs/RECEIVABLES_MOBILE_MATCHING.md 참고.')

# 5. Lessons grounded in the project and accurately attributed contribution.
s=base('배운 점 | 업무 규칙과 검증 기준을 먼저 정한다','AI의 결과를 코드·데이터 흐름·재현 가능한 실행 근거로 확인하는 과정')
lessons=[('업무 의미가 모델을 결정','합계 일치와 청구 지급은 다름\n후보·확정·취소를 구분'),('실패 상황까지 설계','재시도는 같은 요청 키\n동시 확정은 잠금·잔액 검사'),('검증도 범위를 구분','모의 응답과 실제 DB 구분\n브라우저와 실기기 구분')]
for i,(title,body) in enumerate(lessons):
    x=.75+4.16*i
    rect(s,x,2.42,3.51,1.78,'light','line',True)
    text(s,title,x+.17,2.64,3.17,.4,21,'blue',True)
    text(s,body,x+.17,3.22,3.17,.73,18)
text(s,'제가 맡은 일',.78,4.6,5.7,.43,23,'navy',True)
text(s,'서비스 목표·업무 규칙 설정\n총괄·하위 역할 구성 요청\n우선순위와 완료 기준 결정',.8,5.2,5.6,1.22,20)
text(s,'다음 확인',7.0,4.6,5.5,.43,23,'navy',True)
text(s,'핵심 코드 설명·개발자 검토 기록\niPhone·Android 실기기 검증\n계정 보호·공개 운영·사용자 파일럿',7.02,5.2,5.4,1.22,20)
note(s,'발표용 정리: 프로젝트에서 확인한 핵심은 합계 비교와 개별 지급 확인이 다르고, 멱등성과 동시성 제어도 서로 다른 문제라는 점입니다. '
     '저는 목표·업무 규칙·역할 구성과 우선순위를 정했고, 코드 설계·구현·실행 검증에는 에이전트를 활용했습니다. '
     '학습 문서 docs/PORTFOLIO_LEARNING.md의 질문을 직접 답한 뒤 본인이 이해한 내용으로 이 장의 발표를 조정합니다. 학습 완료나 직접 구현했다고 미리 기록하지 않습니다. '
     '실제 모델 호출, 모바일 실기기, 공개 운영은 검증 완료가 아닙니다. 주요 업무·DB·아키텍처 변경은 앞으로 사용자 결정 후 진행합니다.')

OUT.mkdir(parents=True, exist_ok=True)
prs.save(OUT / 'chagok-project.pptx')
print(OUT / 'chagok-project.pptx')
