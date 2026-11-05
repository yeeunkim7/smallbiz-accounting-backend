#!/usr/bin/env python3
"""Build the editable Korean project presentation from repository evidence.

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
from pptx.oxml.xmlchemy import OxmlElement

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'portfolio'
REPO = 'https://github.com/yeeunkim7/smallbiz-accounting-backend'
BASE = REPO + '/blob/main/'
VIDEO = '../demo/receivables-demo.mp4'
FONT = 'Noto Sans CJK KR'
C = dict(bg='FAFAFA', navy='23334D', text='34435A', muted='65738A', blue='308CC0',
         pale='EEF5FA', line='DDE4F0', light='F7F9FC', green='087C70')

prs = Presentation()
prs.slide_width, prs.slide_height = Inches(13.333333), Inches(7.5)
props = prs.core_properties
props.title = '차곡 프로젝트 포트폴리오'
props.subject = '미수금 확인 서비스 · AI 에이전트 협업과 로컬 도구 연동'
props.author = '차곡'
props.keywords = '차곡, Java, Spring Boot, PostgreSQL, Expo, AI 에이전트 협업'
props.comments = ''


def rgb(key):
    return RGBColor.from_string(C.get(key, key))


def rect(s, x, y, w, h, fill='light', line=None, radius=False):
    sh = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE,
                            Inches(x), Inches(y), Inches(w), Inches(h))
    sh.fill.solid(); sh.fill.fore_color.rgb = rgb(fill)
    sh._element.spPr.append(OxmlElement('a:effectLst'))
    for effect in sh._element.xpath('.//a:effectRef'):
        effect.set('idx', '0')
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
    for effect in sh._element.xpath('.//a:effectRef'):
        effect.set('idx', '0')
    return sh


def arrow(s, x, y, w=.4, h=.25):
    return rect_arrow(s, x, y, w, h)


def rect_arrow(s, x, y, w, h):
    sh = s.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, Inches(x), Inches(y), Inches(w), Inches(h))
    sh.fill.solid(); sh.fill.fore_color.rgb = rgb('blue'); sh.line.fill.background()
    for effect in sh._element.xpath('.//a:effectRef'):
        effect.set('idx', '0')
    return sh


def base(title, sub=None, ai=False):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid(); s.background.fill.fore_color.rgb = rgb('bg')
    line(s,.7,.51,1.65,.51,'blue',2)
    text(s, 'PROJECT 02  /  차곡', 1.83, .35, 8, .34, 15, 'blue', True)
    text(s, f'{len(prs.slides):02d}', 11.9, .35, .7, .34, 15, 'muted', align=PP_ALIGN.RIGHT)
    text(s, title, .7, .97, 12, .68, 30, 'navy', True)
    if sub:
        text(s, sub, .73, 1.72, 11.9, .52, 18, 'muted')
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



def small_label(s, label, x, y, w=5):
    return text(s, label, x, y, w, .32, 15, 'blue', True)


def block(s, title, body, x, y, w, h, fill='light'):
    rect(s,x,y,w,h,fill,radius=True)
    text(s,title,x+.2,y+.17,w-.4,.42,21,'navy',True)
    text(s,body,x+.2,y+.77,w-.4,h-.85,17)


def notes(s, spoken, evidence):
    note(s, '발표\n'+spoken+'\n\n근거와 범위\n'+evidence)


def photo(s, name, x, y, w, h):
    im=Image.open(ROOT / ('docs/screenshots/portfolio-nov05-'+name+'.png'))
    scale=min(w/im.width,h/im.height)
    image(s,'docs/screenshots/portfolio-nov05-'+name+'.png',x+(w-im.width*scale)/2,y,w=im.width*scale,h=im.height*scale)


def feature(s, number, title, shot, bullets, x):
    rect(s,x,2.43,5.76,4.66,'FFFFFF','line')
    text(s,number+'  '+title,x+.22,2.65,5.32,.46,22,'blue',True)
    line(s,x+.22,3.23,x+5.54,3.23)
    photo(s,shot,x+.18,3.48,1.7,3.35)
    for i,(head,body) in enumerate(bullets):
        y=3.53+i*1.03
        text(s,head,x+2.08,y,3.43,.35,18,'navy',True)
        text(s,body,x+2.08,y+.45,3.43,.58,16)

# 1. The starting project and the learning motivation, in the user's words.
s=base('기존 대사 시스템을 AI 에이전트 개발의 학습 대상으로 삼았습니다',
       '소규모 사업자를 위한 Java 프로젝트를 실제로 확장하며 작업 방식을 익혔습니다.')
small_label(s,'출발점',.83,2.62)
text(s,'이미 개발한\n잔액 대사 시스템',.83,3.14,5.1,1.13,32,'navy',True)
text(s,'날짜별 예정 금액과 은행 입출금 비교',.85,4.63,5.4,.48,20)
arrow(s,6.1,3.65,.6,.36)
small_label(s,'학습 목표',7.32,2.62)
text(s,'AI 에이전트를 구성하고\n개발·검토를 연결하기',7.32,3.14,5.2,1.13,30,'navy',True)
text(s,'기존 코드를 보존하면서 미수금 관리로 확장',7.33,4.63,5.2,.7,20)
rect(s,.83,5.98,11.65,.9,'pale')
text(s,'코드 생성뿐 아니라 작업 분배 → 실행 → 검토 → 수정까지 경험하는 것이 목적',1.04,6.23,11.2,.42,23,'navy',True)
notes(s,'기존에 소규모 사업자가 사용할 잔액 대사 시스템을 개발했습니다. 이번에는 AI 에이전트 개발을 공부하기 위해 그 프로젝트를 학습 대상으로 선택했습니다. '
      '단순히 코드를 만들어 달라고 하는 데서 끝내지 않고, 작업을 나누고 실제 실행과 검토를 이어가는 과정을 경험하고 싶었습니다. 기존 기능을 보존하면서 청구별 미수금 관리로 확장했습니다.',
      '2026-11-05 사용자 설명에 따른 동기. 기존 코드 전부를 AI 없이 직접 작성했다고 확대하지 않습니다. 기존 Java 대사에서 FREE 미수금으로 확장한 이력은 docs/PORTFOLIO.md와 AI_REVIEW_WORK.md.')

# 2. Selection rationale, without invented competitor benchmarks.
s=base('왜 Claude Code가 아닌 Codex를 선택했나',
       '선택 기준은 이번 프로젝트에 연결된 작업 환경과 역할 분담을 활용한 학습이었습니다.')
for title,x in [('필요했던 작업',.86),('Codex에서 활용한 방식',4.48),('프로젝트에 맞았던 이유',8.75)]:small_label(s,title,x,2.53,3.8)
rows=[('기존 코드·DB 파악','저장소와 실행 환경에서 함께 확인','처음부터 다시 만들지 않아도 됨'),('역할별 작업 분담','총괄·하위 역할로 파일 범위 배분','조사 결과를 구현과 검토에 연결'),('결과를 실행으로 확인','수정·테스트·Git 기록 연결','설명과 실제 동작을 함께 확인')]
for i,(a,b,c) in enumerate(rows):
 y=3.08+i*.98
 text(s,a,.86,y,3.35,.65,21,'navy',True);text(s,b,4.48,y,4.0,.7,19);text(s,c,8.75,y,3.6,.7,18)
 line(s,.86,y+.74,12.46,y+.74)
rect(s,.83,6.43,11.66,.65,'pale')
text(s,'선택의 근거: 이미 연결된 Codex 작업 환경 + 총괄·하위 역할을 활용한 개발 흐름',1.04,6.61,11.2,.35,18,'blue',True)
notes(s,'왜 Claude나 다른 도구 대신 Codex였는지에 대한 제 기준은 성능 순위가 아니라 작업 환경과 진행 방식이었습니다. 이 프로젝트는 Codex에 저장소와 실행 환경이 이미 연결되어 있었고, '
      '총괄 아래 역할을 나누어 코드 수정과 테스트 실행을 이어갈 수 있었습니다. 같은 저장소에서 결과를 모으는 방식을 활용하기 위해 선택했습니다. '
      'Claude Code 등에도 저장소 작업 기능이 있으므로 이런 기능이 Codex에만 있다고 말하지 않습니다. 별도 도구 비교 실험이나 속도·비용 우위를 측정한 것은 아닙니다.',
      '사용자 지시와 AGENTS.md, 실제 작업 이력에 근거한 선택 배경. 경쟁 제품의 미지원 기능이나 측정하지 않은 성능 차이를 주장하지 않습니다. Codex가 모든 실행환경에서 동일한 기능을 제공한다는 일반화도 하지 않습니다.')

# 3. Development organization and handoffs.
s=base('총괄 아래 기획·조사·디자인·개발·검토 역할을 구성했습니다',
       '제가 목표와 업무 기준을 정하고, 총괄 에이전트가 작업 배분과 결과 통합을 맡았습니다.')
rect(s,3.75,2.54,5.85,.7,'blue')
text(s,'총괄 에이전트 · 범위 관리와 결과 통합',3.94,2.74,5.48,.37,21,'FFFFFF',True,align=PP_ALIGN.CENTER)
line(s,6.67,3.24,6.67,3.52,'blue');line(s,1.87,3.52,11.44,3.52,'blue')
roles=[('기획·분석','업무·기존 코드\n재사용 경계'),('자료조사','사용자 문제\n우선 기능'),('디자인','등록·확정 흐름\n금액과 상태'),('개발','담당 파일 구현\n실행·테스트'),('독립 검토','오류 재현\n수정 후 확인')]
for i,(title,body) in enumerate(roles):
 x=.83+2.39*i;line(s,x+1.04,3.52,x+1.04,3.86,'blue')
 rect(s,x,3.86,2.1,1.55,'FFFFFF','line')
 text(s,title,x+.12,4.06,1.86,.4,20,'blue',True,align=PP_ALIGN.CENTER)
 text(s,body,x+.12,4.67,1.86,.62,16,align=PP_ALIGN.CENTER)
text(s,'분석 결과 → 범위·설계 → 기능별 구현 → 독립 검토 → 수정·재검증',.86,5.98,11.6,.43,23,'navy',True)
text(s,'독립 작업은 병행하고, 선행 결과가 필요한 작업은 순서대로 진행',.87,6.69,11.5,.38,19,'muted')
notes(s,'총괄이 기획·조사·디자인·개발·검토를 조율하도록 요청했습니다. 역할 이름만 정하는 대신 각 역할이 넘길 결과를 정했습니다. 기존 코드 분석은 재사용 범위로, 디자인은 화면 흐름으로, 검토는 재현 절차와 수정 요구로 연결했습니다. '
      '동시에 모든 역할을 실행하지 않았고 하위 에이전트는 최대2개로 제한했습니다. 작업 파일 범위를 나눠 충돌을 줄이고 중요한 변경은 개발자 판단을 거치도록 작업 규칙을 정리했습니다.',
      'AGENTS.md와 AI_REVIEW_WORK.md. 표는 책임 구조이며 실제 팀 인원 또는 항상5개 동시 실행을 의미하지 않습니다. 사용자 목표·규칙 설정과 에이전트 세부 설계·구현은 구분합니다.')

# 4. Product architecture and technical stack.
s=base('작업 결과는 청구·입금·배분을 관리하는 서비스로 확장됐습니다',
       '기존 대사 기능을 유지하고, 사업체별 미수금 모델과 모바일 화면을 추가했습니다.')
block(s,'모바일·웹 화면','TypeScript · React Native\nExpo · React',.83,2.52,3.23,1.73)
block(s,'Spring Boot API','Java 21 · Spring Security\n세션·사업체 권한 검사',5.05,2.52,3.23,1.73,'pale')
block(s,'PostgreSQL 17','원본 · 배분 · 취소·감사\nFlyway V1–V13',9.27,2.52,3.23,1.73)
arrow(s,4.31,3.19,.47);arrow(s,8.53,3.19,.47)
text(s,'Controller → Service → JdbcTemplate(SQL) → DB',.87,4.68,11.5,.45,25,'navy',True)
small_label(s,'핵심 데이터 관계',.87,5.55)
text(s,'입금  1 : N  배분 라인  N : 1  청구',.87,6.08,8.15,.43,25,'blue',True)
text(s,'부분·묶음 입금을 표현',9.1,6.13,3.35,.4,18)
text(s,'기존 대사 JPA 구조 유지    |    빌드·관리: Gradle · Git/GitHub',.88,6.96,11.4,.33,17,'muted')
notes(s,'제품은 Expo 클라이언트에서 Spring Boot API를 거쳐 PostgreSQL에 접근합니다. 신규 미수금 모듈은 JdbcTemplate SQL 구조이며 기존 대사 JPA Repository는 그대로 유지했습니다. '
      '입금과 청구 사이의 배분 라인으로 부분·묶음 입금을 표현합니다. 확정은 사용자가 요청하고 금액 계산과 잔액 검사는 Java와 DB 트랜잭션에서 처리합니다. FREE 흐름에는 모델 호출이 없습니다.',
      'docs/architecture.md, receivables-erd.md, backend-java/build.gradle, mobile/package.json. 모바일 공통 코드와 브라우저 검증 경로이며 iOS/Android스토어 출시 완료는 아닙니다. DDL·잠금은 에이전트 구현입니다.')

# 5 and 6. Five concrete development items, each shown once with a real photo.
s=base('핵심 개발 01–02 | 계정과 거래 원본을 관리합니다',
       '서버의 사업체 권한 검사부터 청구·입금 등록까지')
feature(s,'01','계정·사업체 접근 제어','account',[
 ('인증과 소유권','세션과 멤버십을 서버에서 확인'),('사업체별 데이터','다른 사업체 자료 접근 차단'),('등급·사용량','성공한 후보 실행만 집계')],.83)
feature(s,'02','청구·입금 등록','registration',[
 ('청구 조건','거래처·금액·지급기한 입력'),('입금 원본','입금자·날짜·금액 별도 기록'),('재요청 처리','같은 키의 요청은 결과 재사용')],6.74)
notes(s,'첫 번째는 인증과 사업체 접근 제어입니다. 화면만 나누는 것이 아니라 API가 세션과 멤버십을 확인합니다. 두 번째는 청구와 입금 원본을 따로 등록하는 기능입니다. 입금 등록만으로 미수가 줄지 않도록 했습니다. '
      '표시된 계정과 거래는 시연용 데이터입니다. 계정 화면 자체가 권한 격리의 증거는 아니며 서버 통합 테스트를 근거로 설명합니다.',
      '실제 브라우저 캡처 portfolio-nov05-account/registration.png. AuthController, TenantAccess와 ReceivablesService 등 기존 계정·등록 모듈; docs/RECEIVABLES_REGISTRATION.md, RECEIVABLES_USAGE.md. 테스트 계정 토큰·비밀번호는 자료에 포함하지 않습니다.')
s=base('핵심 개발 03–04 | 후보를 검토하고 연결 금액을 확정합니다',
       '추천과 확정을 분리해 현재 잔액을 기준으로 처리합니다.')
feature(s,'03','규칙 기반 매칭 후보','candidates',[
 ('조회 범위','선택한 기간의 거래를 조회'),('후보 근거','날짜·금액·입금자명으로 제안'),('검토 단계','후보 생성만으로 배분하지 않음')],.83)
feature(s,'04','배분·미수금 계산','confirmed-outstanding',[
 ('부분·묶음 연결','여러 입금과 청구에 금액 배분'),('확정 직전 검사','부모 행 잠금 후 잔액 재검사'),('서버 계산 결과','확정된 금액만 미수에 반영')],6.74)
notes(s,'세 번째는 규칙 기반 후보 생성입니다. 날짜·금액·이름이 같아도 같은 거래라고 단정하지 않고 사용자가 검토하게 했습니다. 네 번째는 배분 확정과 미수금 계산입니다. '
      '검토 중 잔액이 바뀔 수 있기 때문에 확정 시 서버가 부모 행을 잠그고 다시 검사합니다. 후보 조회의 기간 조건과 현재 미수 요약의 asOf 의미는 다릅니다.',
      'portfolio-nov05-candidates/confirmed-outstanding.png; MatchingService, AllocationService.confirm/lockAndValidate/checkAvailable. 사용자 확정·Java 금액 계산 원칙은 개발자 지정, 구현은 에이전트 수행. 실제 화면과 서버 잔액 검증 근거는 docs/demo/manifest.json.')
s=base('핵심 개발 05 | 연결 취소와 감사 이력을 남깁니다',
       '입금 원본과 확정 기록을 보존하면서 취소된 배분을 잔액 계산에서 제외합니다.')
rect(s,.83,2.43,4.1,4.66,'FFFFFF','line')
photo(s,'cancellation-audit',1.02,2.62,3.72,4.26)
small_label(s,'취소 요청에 남기는 정보',5.5,2.68)
text(s,'누가 · 언제 · 어떤 사유로 취소했는가',5.5,3.23,6.7,.48,26,'navy',True)
for i,(a,b) in enumerate([('원본 보존','입금·청구·기존 배분 라인을 삭제하지 않음'),('잔액 복원','취소한 연결을 제외해 미수금을 다시 계산'),('한 번에 저장','취소 기록·감사·명령 결과를 함께 커밋')]):
 y=4.17+i*.86
 text(s,a,5.52,y,1.8,.4,20,'blue',True)
 text(s,b,7.42,y,4.9,.62,18)
text(s,'시연 영상 보기 ↗',10.0,6.93,2.45,.34,18,'blue',True,link=VIDEO)
notes(s,'다섯 번째는 취소와 감사 이력입니다. 잘못 연결했다면 사유를 남기고 취소합니다. 실제 통장의 입금 원본을 지우거나 돈을 환불하는 기능은 아닙니다. '
      '배분이 취소됐다는 기록을 추가하고 활성 배분에서 제외해 미수를 복원합니다. 저장 과정이 실패하면 취소와 감사 기록이 따로 남지 않도록 같은 트랜잭션에서 처리합니다.',
      'portfolio-nov05-cancellation-audit.png; AllocationService.cancel/audit; V10; docs/RECEIVABLES_ALLOCATION.md. 취소 이력과 현재 상태 조회는 과거 멱등 명령 응답과 구분합니다.')

# 8. Concrete benefit of independent agent review.
s=base('독립 검토는 실제 수정으로 이어졌습니다',
       '저장 후 응답이 끊긴 상황에서 발견한 중복 저장 위험')
for i,(a,b) in enumerate([('DB 저장은 성공','화면은 응답을 받지 못함'),('공백만 수정해 재시도','요청 키가 새로 만들어짐'),('서버는 다른 명령으로 판단','중복 저장 가능성 발생')]):
 x=.85+4.17*i;text(s,a,x,2.94,3.65,.62,23,'navy',True);text(s,b,x,3.86,3.6,.75,18)
 if i<2:arrow(s,x+3.7,3.24,.3,.2)
line(s,.85,4.95,12.44,4.95)
small_label(s,'수정',.85,5.41,1.3);text(s,'정규화한 내용이 같으면 기존 요청 키 유지',2.35,5.35,9.8,.48,24,'navy',True)
small_label(s,'검증',.85,6.39,1.3);text(s,'저장 후 503 응답을 재현 → 같은 키 재시도 → 입금 1건 확인',2.35,6.33,9.9,.68,21,'blue',True)
notes(s,'구현 담당과 검토 담당을 분리한 효과를 보여주는 사례입니다. 검토에서 공백만 바꿔도 요청 키가 바뀌는 문제가 드러났습니다. 저장은 이미 됐는데 새 키로 다시 요청하면 중복 위험이 생깁니다. '
      '정규화된 내용이 같으면 키를 유지하도록 수정하고 실제 DB 저장 뒤 응답만503으로 바꿔 검증했습니다. 이 결함 발견과 코드 수정·재현은 에이전트가 수행했습니다.',
      'docs/receivables-mobile-core-review.md, Registration.submit, CommandKey.forPayload, scripts/verify-receivables-mobile-browser.py. 2026-11-01 검증 기록이며 이번 자료 작업에서 새로 재현했다고 주장하지 않습니다.')

# 9. Agent development learning beyond using the coding assistant.
s=base('직접 설치한 Hermes에는 읽기 전용 조회 도구를 연결했습니다',
       '에이전트 활용에서 도구 입력·권한·응답 범위를 직접 확인하는 실습으로 이어갔습니다.')
for i,(a,b) in enumerate([('Hermes','날짜를 받아 도구 호출'),('Python 도구','인증된 GET만 실행'),('Java API + DB','현재 원장 금액 반환')]):
 x=.83+4.22*i;block(s,a,b,x,2.6,3.22,1.5,'pale' if i==1 else 'light')
 if i<2:arrow(s,x+3.46,3.22,.45)
small_label(s,'확인한 응답',.88,4.65)
for x,label,value in [(.88,'청구액','100,000원'),(4.32,'확정 배분','40,000원'),(7.76,'미수금','60,000원')]:
 text(s,label,x,5.24,3.2,.32,17,'muted');text(s,value,x,5.76,3.2,.62,32,'navy',True)
text(s,'현재 원장 요약과 API 경로를 근거로 설명 · 제품 FREE 기능과 별도 실습',.88,6.94,11.6,.34,18,'blue',True)
notes(s,'Hermes는 제 PC에 직접 설치하고 에이전트가 제공한 예제를 적용했습니다. 거래처 필드를 수정한 뒤 실제 Java GET을 연결했습니다. 직접 함수 출력과 Hermes의 실제 도구 실행 표시를 확인했습니다. '
      '도구는 주소·사업체·토큰을 모델이 지정하지 못하게 하고 날짜만 받습니다. asOf는 현재 원장의 연체 판정 기준일이며 특정일 거래나 과거 잔액 조회가 아닙니다. '
      '이 슬라이드의 금액은 대화에 공유한 실제 실습 응답을 정리한 것이고 새 터미널 캡처를 만들어 넣지 않았습니다.',
      'examples/hermes/chagok-local-api; docs/PORTFOLIO_LEARNING.md. 11/04 사용자 제공 실행 로그·호출 표시 확인. native Hermes 플러그인이며 MCP 구현으로 표현하지 않습니다. 세션은 쓰기 권한도 있으므로 도구 GET 제한과 서버 읽기전용 토큰을 혼동하지 않습니다. PRO 제품 출시 검증은 미완료.')

# 10. Outcomes with bounded evidence rather than productivity claims.
s=base('기존 기능을 보존하고, 검토 가능한 결과를 남겼습니다')
for i,(a,b) in enumerate([('기존 시스템 재사용','Java·PostgreSQL·기존 대사 API 유지'),('기능별 검토·수정','독립 검토의 재현 절차를 구현에 반영'),('개인 PC 실행 확인','환경 구성부터 전체 테스트·도구 호출까지 수행')]):
 y=2.5+i*.78;text(s,a,.86,y,3.6,.45,23,'blue',True);text(s,b,4.8,y,7.6,.48,21)
line(s,.86,5.02,12.42,5.02)
small_label(s,'검증 근거',.86,5.35)
text(s,'백엔드 186개 통과',.86,5.94,5.8,.5,28,'navy',True)
text(s,'11.04 · 사용자 PC / 실패·오류·스킵 0',.88,6.58,5.9,.34,17,'muted')
text(s,'클라이언트 13개 · 브라우저 9 + 9개',7.04,5.94,5.42,.7,22,'navy',True)
text(s,'11.01 · 기존 에이전트 실행 기록',7.05,6.66,5.4,.34,17,'muted')
notes(s,'좋았던 점은 기존 시스템을 유지하면서 기능별 구현과 검토를 이어갈 수 있었다는 점입니다. 생산성 몇 배나 시간 절감률은 측정하지 않았습니다. 대신 재사용한 기술과 실제 수정 사례, 실행 결과를 근거로 설명합니다. '
      '제 PC에서는 테스트 DB를 분리해 백엔드186개를 실행했고 실패·오류·스킵0을 확인했습니다. 클라이언트와 브라우저 수치는 기존 에이전트 실행 기록입니다.',
      'docs/PORTFOLIO_LEARNING.md, RECEIVABLES_MOBILE_MATCHING.md. 총 테스트 횟수로 합산하지 않습니다. 새영상 녹화/문구변경 검사는 docs/demo/manifest.json에 별도 기록합니다. 실기기·공개운영·고객파일럿은 미완료입니다.')

# 11. Learning and honest contribution, with the one concrete authored assertion.
s=base('업무 규칙을 정하고, 실패 뒤의 데이터까지 확인하는 법을 배웠습니다')
rect(s,.83,2.42,11.66,.91,'navy')
text(s,'assertMoney(paymentBalance(owner, p1), "unallocatedAmount", "60");',1.06,2.7,11.14,.42,21,'FFFFFF')
text(s,'같은 키로 금액을 바꾼 요청이 거절된 뒤, 미배분 잔액 60원이 유지되는지 검사',.87,3.65,11.5,.72,22,'navy',True)
small_label(s,'직접 수행한 일',.87,4.93)
text(s,'목표·업무 기준과 에이전트 역할 구성\n안내에 따른 assertion 작성·검증 위치 설명\n환경 설치·테스트·Hermes 예제 수정과 실행',.87,5.48,6.2,1.35,19)
small_label(s,'배운 점',7.85,4.93,4.5)
text(s,'요청 키와 동시성 문제를 구분\n원본 보존과 배분 취소를 구분\n조회 범위를 넘는 AI 설명을 경계',7.85,5.48,4.6,1.35,19)
notes(s,'저는 업무 기준과 역할을 정하고 실제 환경에서 실행했습니다. 이 assertion은 안내된 메서드와 필드를 사용해 직접 채팅에 작성한 한 줄이며 파일 반영과 해당 테스트 실행은 에이전트가 했습니다. '
      '성공 응답뿐 아니라 실패 후 잔액이 변하지 않았는지 확인해야 한다는 점을 배웠습니다. 같은 키의 재시도와 다른 키의 동시 배분도 서로 다른 문제임을 설명하는 연습을 했습니다. '
      '세부 설계·제품 코드·검토 실행에는 AI를 활용했고 전체를 독자 구현했다고 표현하지 않습니다. 다음 단계는 핵심 코드를 직접 설명하는 것과 실기기·운영 검증입니다. Docker는 보류했습니다.',
      'docs/PORTFOLIO_LEARNING.md; AllocationIntegrationTest.idempotentConfirmationNormalizesOrderAndPreservesOriginalSnapshotAfterCancellation. 이 테스트는 원금100/배분40/잔여60원으로 축소한 값이며 앞 실습의10만/4만/6만과 구분합니다.')

OUT.mkdir(parents=True,exist_ok=True)
prs.save(OUT/'chagok-project.pptx')
print(f'{len(prs.slides)} slides: {OUT / "chagok-project.pptx"}')
