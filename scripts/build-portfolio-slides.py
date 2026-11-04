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
from pptx.oxml.xmlchemy import OxmlElement

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'portfolio'
REPO = 'https://github.com/yeeunkim7/smallbiz-accounting-backend'
BASE = REPO + '/blob/main/'
VIDEO = '../demo/receivables-demo.mp4'
FONT = 'Noto Sans CJK KR'
C = dict(bg='FFFFFF', navy='23334D', text='34435A', muted='65738A', blue='3A59D1',
         pale='F1F4FD', line='DDE4F0', light='F7F9FC', green='087C70')

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
    text(s, '차곡  /  개발 기록', .7, .35, 8, .34, 15, 'blue', True)
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


# 1. The user problem and the resulting product.
s = base('청구와 입금을 연결하고, 남은 금액을 확인합니다',
         '차곡 · 소규모 사업자를 위한 미수금·입금 확인 서비스')
text(s, '합계가 맞아도\n어떤 청구가 지급됐는지는 모릅니다.', .75, 2.58, 8.1, 1.13, 27, 'navy', True)
text(s, '부분 입금과 여러 청구를 합친 입금을 연결하고,\n확정·취소 이력과 청구별 미수금을 남겼습니다.', .78, 3.98, 7.9, .9, 20)
rect(s, .75, 5.27, 7.96, 1.05, 'pale', radius=True)
for x,label,value in [(1.0,'청구','100,000원'),(3.55,'확정 배분','40,000원'),(6.1,'남은 금액','60,000원')]:
    text(s,label,x,5.44,2.2,.3,15,'muted')
    text(s,value,x,5.85,2.3,.38,22,'blue',True)
text(s, 'Codex의 총괄·하위 AI 에이전트를 활용해 개발했습니다.', .78, 6.67, 8.1, .36, 17, 'muted')
rect(s, 9.15, 2.35, 3.43, 4.75, 'light', 'line', True)
image(s, 'docs/demo/poster.png', 9.25, 2.45, w=3.23, h=4.42, crop=(721,160,1111,694), link=VIDEO)
note(s, '발표: 차곡은 기존 입출금 대사 시스템에서 출발한 미수금 확인 서비스입니다. 하루의 합계가 같아도 개별 청구가 지급됐는지는 알 수 없습니다. '
     '저는 소규모 사업자의 부분 입금과 묶음 입금을 확인하는 방향으로 목표를 정했습니다. 기존 Java 시스템을 보존하고 청구, 입금, 배분 이력을 추가하도록 했습니다. '
     '예를 들어 청구 10만 원에 입금 4만 원을 등록하는 것만으로 미수가 줄지는 않습니다. 사람이 연결을 확정하면 미수 6만 원이 됩니다. '
     '근거: docs/PORTFOLIO.md, docs/RECEIVABLES_ALLOCATION.md. 화면은 합성 자료와 실제 Java API를 사용한 Chromium 모바일 viewport입니다. 실기기 캡처가 아닙니다. '
     '화면 클릭은 같은 배포 묶음의 ../demo/receivables-demo.mp4를 엽니다. 제품 FREE는 AI를 호출하지 않습니다.')

# 2. Why Codex and how responsibility was assigned.
s = base('Codex에서 역할을 나누고, 검토 결과를 다시 구현에 반영했습니다',
         '제가 목표·업무 규칙·완료 기준을 정하고, 총괄 에이전트에 작업 조율을 맡겼습니다.')
text(s, 'Codex를 선택한 이유', .77, 2.57, 3.6, .4, 22, 'blue', True)
for i,(title,body) in enumerate([
    ('기존 프로젝트에서 시작','코드·DB·테스트를 함께 확인'),
    ('수정과 실행을 연결','파일 수정 후 테스트·Git 기록'),
    ('역할별 결과를 통합','같은 저장소에서 검토·수정 반복')]):
    y=3.24+i*1.08
    text(s,title,.79,y,3.6,.35,19,'navy',True)
    text(s,body,.79,y+.45,3.6,.4,16,'muted')
rect(s,5.0,2.55,7.5,.7,'blue',radius=True)
text(s,'총괄 · 작업 배분 / 선행 결과 연결 / 통합',5.15,2.73,7.2,.35,20,'FFFFFF',True,align=PP_ALIGN.CENTER)
roles=[('기획·분석','기존 업무와 DB\n재사용 범위'),('자료조사','사용자 문제\n기능 우선순위'),('디자인','화면 흐름\n상태와 근거'),('개발','기능별 구현\nAPI와 화면'),('독립 검토','결함 재현\n수정 후 재검증')]
for i,(title,body) in enumerate(roles):
    x=5.0+i*1.53
    line(s,x+.69,3.25,x+.69,3.64,'line')
    rect(s,x,3.64,1.38,1.49,'light','line',True)
    text(s,title,x+.04,3.85,1.3,.32,16,'blue',True,align=PP_ALIGN.CENTER)
    text(s,body,x+.04,4.39,1.3,.62,14,align=PP_ALIGN.CENTER)
text(s,'분석 → 범위·설계 → 구현 → 검토 → 수정·재검증',5.03,5.57,7.5,.4,18,'navy',True)
text(s,'필요한 역할을 단계별로 투입 · 동시 하위 에이전트 최대 2개',5.03,6.13,7.5,.36,15,'muted')
rect(s,.75,6.74,11.83,.045,'line')
text(s,'업무 기준: Java 금액 계산 · 사용자 확정 · 원본 보존 · FREE AI 호출 없음',.79,6.95,11.8,.31,16,'navy')
note(s,'발표: 기존 프로젝트를 버리고 다시 만드는 방식보다 저장소를 읽고 수정과 실행을 이어갈 작업 환경이 필요했습니다. Codex에서 총괄 아래 기획·조사·디자인·개발·검토 역할을 나누도록 했습니다. '
     '독립적인 조사와 코드 분석은 병행하고, 화면은 도메인과 API가 정리된 뒤 연결했습니다. 구현 담당과 검토 담당을 나누어 발견한 오류를 다시 수정하게 했습니다. '
     '제가 지정한 원칙은 Java 금액 계산, 사람의 확정, 원본 보존, FREE AI 호출 금지였습니다. 세부 DB·잠금·코드 설계는 에이전트가 수행했고 이를 전부 직접 설계했다고 설명하지 않습니다. '
     '근거: AGENTS.md, docs/AI_REVIEW_WORK.md, docs/adr/0002-receivables-decisions.md. 역할은 책임 구분이며 5개 에이전트 상시 동시 실행을 뜻하지 않습니다. 도구별 성능·생산성 배수 비교는 측정하지 않았습니다.')

# 3. Runtime architecture and concrete stack, with the lab path clearly separate.
s = base('금액과 확정은 서버에서, AI는 조회 결과를 설명하도록 구성했습니다',
         '제품 실행 구조와 별도의 Hermes 실습 경로')
for x,title,body in [(.75,'화면','TypeScript · React Native\nExpo · React'),(4.9,'백엔드','Java 21 · Spring Boot\nSpring Security · JDBC'),(9.05,'데이터·빌드','PostgreSQL 17\nFlyway · Gradle 빌드')]:
    rect(s,x,2.51,3.52,1.55,'pale',radius=True)
    text(s,title,x+.18,2.71,3.16,.35,21,'blue',True)
    text(s,body,x+.18,3.24,3.16,.67,18)
arrow(s,4.39,3.11);arrow(s,8.54,3.11)
text(s,'Controller → Service → JdbcTemplate(SQL) → DB',.8,4.32,11.8,.36,20,'navy',True)
text(s,'신규 청구·입금·배분은 별도 모델로 추가 · 기존 대사 모듈의 JPA 구조 유지',.8,4.86,11.8,.35,17,'muted')
rect(s,.75,5.48,11.83,1.13,'light','line',True)
text(s,'로컬 실습',.94,5.65,1.45,.34,18,'blue',True)
text(s,'Hermes → Python 조회 도구 → 인증된 Java GET → 현재 원장 요약',2.54,5.68,9.7,.36,19,'navy',True)
text(s,'모델 입력은 날짜만 · 서버 금액과 API 근거 반환 · FREE 제품과 별도',2.54,6.2,9.6,.3,16,'muted')
text(s,'검증: JUnit · PostgreSQL 통합 테스트 · Playwright · Python unittest',.8,6.94,11.8,.32,16,'muted')
note(s,'발표: 제품은 Expo 화면, Spring Boot API, PostgreSQL로 구성됩니다. 신규 미수금은 Controller-Service-JdbcTemplate SQL 흐름이며 기존 대사의 JPA Repository는 유지했습니다. '
     '서버는 인증과 사업체 권한을 확인하고, 확정 트랜잭션에서 요청 키 확인·부모 잠금·잔액 재검사·배분과 감사 저장을 수행합니다. 금액은 Java의 정확한 타입과 DB로 처리하고 API에는 문자열로 전달합니다. '
     '별도 학습으로 제가 Windows WSL2 Ubuntu에 Hermes와 Java/PostgreSQL을 설치했습니다. Python 도구가 인증 파일을 읽어 localhost의 GET만 호출하게 하고, 모델에는 날짜만 입력받게 했습니다. '
     'asOf는 현재 원장 연체 판정 기준일입니다. 특정일 거래·과거 잔액·개별 원본 조회가 아닙니다. 도구 GET 제한은 읽기 전용 서버 자격증명과 같지 않으며 제품 FREE AI 개방이 아닙니다. '
     '버전 근거: backend-java/build.gradle(Spring Boot4.1.1), mobile/package.json(Expo58/ReactNative0.88/React19/TypeScript), FlywayV1~V13. Hermes0.22.6, Windows WSL2, Git/GitHub를 사용했습니다. '
     'MCP·RAG·Fastify·WebSocket·Docker 배포 구현 경험으로 표현하지 않습니다. 세부 아키텍처 docs/architecture.md, plugin examples/hermes/chagok-local-api.')

# 4. Result: verified user execution plus one concrete development defect.
s = base('제 PC에서 전체 테스트와 실제 AI 도구 연결을 확인했습니다',
         '2026.11.04 · WSL2 / Java 21 / PostgreSQL 17 · 개발·테스트 DB 분리')
rect(s,.75,2.53,3.4,2.04,'pale',radius=True)
text(s,'186',.98,2.79,2.95,.86,54,'blue',True)
text(s,'백엔드 테스트 통과',1.0,3.77,2.94,.38,20,'navy',True)
text(s,'실패·오류·스킵 0',1.0,4.19,2.9,.3,16,'muted')
text(s,'Hermes → 실제 Java API',4.65,2.68,7.76,.4,23,'navy',True)
text(s,'청구 100,000원  /  확정 배분 40,000원  /  미수 60,000원',4.67,3.34,7.85,.41,20,'blue',True)
text(s,'도구 실행 표시와 API 근거를 확인\n현재 원장 요약과 개별 거래 조회의 범위를 구분',4.67,4.0,7.85,.73,18)
line(s,.78,5.08,12.53,5.08)
text(s,'개발 중 수정한 문제',.79,5.43,3.5,.4,22,'navy',True)
text(s,'저장 후 응답 유실 → 공백 수정으로 요청 키가 바뀌는 문제 발견',4.66,5.4,7.86,.39,18)
text(s,'정규화한 내용에 같은 키 유지 → 재시도 후 입금 1건 확인',4.66,6.0,7.86,.4,18,'blue',True)
text(s,'추가 검사: 잘못된 날짜 거절 · 모의 인증 오류와 연결 실패를 자료 없음과 구분',.8,6.88,11.75,.37,17,'muted')
note(s,'발표: 직접 실행하면서 두 가지를 확인했습니다. 먼저 개발·테스트 DB 계정을 분리하고 교차 접속을 막았습니다. 전체 테스트를 실행해 XML 보고서의 186개 모두 통과를 확인했습니다. '
     '이후 합성 계정에 10만 원 청구와 4만 원 확정 배분을 준비했습니다. Python 함수로 실제 API를 확인한 뒤 Hermes에서 조회 도구 실행 표시와 미수 6만 원, API 경로를 포함한 답변을 확인했습니다. '
     '잘못된 날짜도 직접 검사했고 인증 401·연결 실패는 모의 오류로 검사했습니다. 실제 인증 만료나 서버 중단을 모델로 재현한 것은 아닙니다. '
     '아래 중복 저장 사례의 발견·수정·브라우저 검증은 이전 에이전트 작업입니다. 사용자 PC의 전체 실행과 구분합니다. '
     '근거: 사용자 제공 로그·화면 및 도구 실행 확인, docs/PORTFOLIO_LEARNING.md, docs/receivables-mobile-core-review.md. 사용자 PC 모델 원시 로그를 총괄이 직접 수집한 것은 아닙니다. '
     '기존2026-11-01 클라이언트13개·브라우저9+9개 및 별도클라우드plugin단위10개는 이186개와 합산하지 않습니다. 정상 실습 경로 확인이지 PRO 제품 출시 검증이 아닙니다.')

# 5. Lessons and contribution in the developer's own verifiable scope.
s = base('배운 점은 업무 규칙을 코드와 실행 결과로 확인하는 방법입니다',
         'AI가 만든 결과를 받아보는 데서, 직접 실행하고 실패 조건을 확인하는 단계로')
for i,(title,body) in enumerate([
    ('합계와 연결은 다르다','입금 등록만으로 미수가 줄지 않음\n확정·취소와 원본 보존을 구분'),
    ('실패 뒤 잔액도 확인한다','재시도는 기존 요청 키로\n다른 키의 경쟁은 잠금 후 재검사'),
    ('조회 범위가 답변의 한계다','자료 없음과 조회 실패를 구분\n현재 원장과 과거 잔액을 구분')]):
    x=.75+4.16*i
    rect(s,x,2.52,3.51,1.88,'light','line',True)
    text(s,title,x+.17,2.75,3.17,.39,20,'blue',True)
    text(s,body,x+.17,3.37,3.17,.79,16)
text(s,'제가 직접 한 일',.79,4.85,5.6,.42,23,'navy',True)
text(s,'목표·업무 기준 설정, 에이전트 역할 구성\n안내에 따라 환경 설치·전체 테스트 실행\nHermes 필드 수정·조회 도구 실행·오류 검사',.8,5.51,5.75,1.26,18)
text(s,'에이전트에 맡긴 일',7.02,4.85,5.5,.42,23,'navy',True)
text(s,'세부 설계·제품 코드 구현\n독립 검토·결함 재현·수정\n학습 예제와 검사 명령 제공',7.03,5.51,5.46,1.26,18)
text(s,'다음 과제: 핵심 코드 직접 설명 · 실기기·운영 검증    /    Docker·배포는 보류',.8,7.0,11.8,.3,15,'muted')
note(s,'발표: 가장 크게 배운 것은 업무 용어를 코드와 데이터 상태로 나누는 일입니다. 입금이 있어도 연결이 확정되지 않으면 미수는 남습니다. '
     '응답이 끊겼을 때 새 키를 만들면 중복 위험이 생기고, 다른 키로 같은 입금을 동시에 사용하는 문제는 잠금과 잔액 검사로 다뤄야 한다는 차이를 학습했습니다. '
     '오류 응답 뒤 미수와 미배분 금액이 유지되는지도 확인해야 합니다. 저는 안내를 받아 assertMoney(paymentBalance(owner,p1), "unallocatedAmount", "60"); 한 줄을 작성했습니다. 파일 반영과 그 테스트 실행은 에이전트가 했습니다. '
     '이번에는 제 PC에서 설치와 명령 실행, 예제 적용과 필드 수정, 전체 테스트와 실제 도구 호출까지 수행했습니다. 코드 예제와 절차는 에이전트 도움을 받았습니다. '
     '전체 구현을 직접 설계했다고 표현하지 않고, 이제 핵심 코드와 트랜잭션을 스스로 설명할 수 있도록 학습을 이어갑니다. Docker 설치와 공개 배포는 보류했습니다. '
     'iOS/Android 실기기, 공개 운영 보안, 사용자 파일럿과 PRO 제품 연동은 별도 후속입니다. 근거 docs/PORTFOLIO_LEARNING.md.')

OUT.mkdir(parents=True, exist_ok=True)
prs.save(OUT / 'chagok-project.pptx')
print(OUT / 'chagok-project.pptx')
