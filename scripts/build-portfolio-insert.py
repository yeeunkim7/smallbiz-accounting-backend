#!/usr/bin/env python3
"""Build only Chagok's insert, matching the supplied portfolio's project layouts."""
from pathlib import Path
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE,MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN
from pptx.oxml.xmlchemy import OxmlElement
from PIL import Image
R=Path(__file__).resolve().parents[1];O=R/'docs/portfolio'
p=Presentation();p.slide_width=Inches(20);p.slide_height=Inches(11.25)
p.core_properties.title='차곡 — 기존 포트폴리오 삽입용';p.core_properties.author='김예은'
C={'bg':'F5F5F5','text':'333333','blue':'3E92CC','indigo':'3A59D1','line':'777777','white':'FFFFFF'}
def col(c):return RGBColor.from_string(C.get(c,c))
def tx(s,t,x,y,w,h,z=20,c='text',bold=False,center=False):
 q=s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h));f=q.text_frame;f.word_wrap=True
 f.margin_left=f.margin_right=f.margin_top=f.margin_bottom=0
 for i,t in enumerate(t.split('\n')):
  a=f.paragraphs[0] if i==0 else f.add_paragraph();a.space_after=Pt(7);a.line_spacing=1.12
  if center:a.alignment=PP_ALIGN.CENTER
  r=a.add_run();r.text=t;r.font.name='JASO Sans Bold' if bold else 'JASO Sans';r.font.size=Pt(z);r.font.bold=bold;r.font.color.rgb=col(c)
 return q
def box(s,x,y,w,h,fill='white',stroke='line'):
 q=s.shapes.add_shape(MSO_SHAPE.RECTANGLE,Inches(x),Inches(y),Inches(w),Inches(h));q.fill.solid();q.fill.fore_color.rgb=col(fill)
 q.line.color.rgb=col(stroke);q.line.width=Pt(.7);q._element.spPr.append(OxmlElement('a:effectLst'))
 for a in q._element.xpath('.//a:effectRef'):a.set('idx','0')
 return q
def ln(s,x,y,x2,y2,c='line',width=1):
 q=s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT,Inches(x),Inches(y),Inches(x2),Inches(y2));q.line.color.rgb=col(c);q.line.width=Pt(width)
 for a in q._element.xpath('.//a:effectRef'):a.set('idx','0')
def base(title,sub=None):
 s=p.slides.add_slide(p.slide_layouts[6]);s.background.fill.solid();s.background.fill.fore_color.rgb=col('bg')
 ln(s,1.13,.85,2.8,.85,'blue',3);tx(s,'PROJECT 02',2.98,.61,4,.5,23,'indigo');tx(s,'Portfolio',17.3,.49,2.0,.5,24,'indigo')
 tx(s,title,1.13,1.3,17.8,.96,48,'text',True)
 if sub:tx(s,sub,1.13,2.48,17.75,.55,20)
 box(s,0,11.17,20,.075,'indigo','indigo');return s
def label(s,t,x,y,w=8):
 box(s,x,y+.04,.25,.25,'text','text');tx(s,t,x+.42,y-.03,w,.5,26,bold=True)
def photo(s,name,x,y,w,h):
 a=R/'docs/screenshots'/('portfolio-nov05-'+name+'.png');im=Image.open(a);k=min(w/im.width,h/im.height)
 s.shapes.add_picture(str(a),Inches(x+(w-im.width*k)/2),Inches(y),width=Inches(im.width*k),height=Inches(im.height*k))
def note(s,t):s.notes_slide.notes_text_frame.text=t
def card(s,n,title,shot,body,x,y=6.03,w=5.65):
 box(s,x,y,w,4.48);photo(s,shot,x+.2,y+.18,w-.4,2.1)
 tx(s,n+'. '+title,x+.18,y+2.43,w-.36,.45,23,'blue',True,True);ln(s,x+.35,y+3.02,x+w-.35,y+3.02)
 tx(s,body,x+.29,y+3.24,w-.58,1.05,17)

s=base('차곡 — AI 에이전트로 확장한 미수금 확인 서비스')
label(s,'프로젝트 개요',1.13,2.72);label(s,'기술 스택',9.4,2.72)
box(s,1.13,3.2,7.8,2.05,fill='bg');box(s,9.4,3.2,9.45,2.05,fill='bg')
tx(s,'01.  배경     기존 소규모 사업자용 잔액 대사 시스템\n02.  목적     AI 에이전트 개발 학습과 미수금 기능 확장\n03.  구성     개발자 1인 + 총괄·역할별 AI 에이전트',1.45,3.48,7.15,1.65,20)
tx(s,'▪ Backend : Java 21, Spring Boot, Spring Security\n▪ DBMS : PostgreSQL 17 / Flyway · JDBC · 기존 JPA\n▪ Frontend & Tools : Expo, React Native, TypeScript, Codex',9.72,3.48,8.83,1.65,20)
label(s,'수행 내용',1.13,5.55)
card(s,'01','계정·사업체 접근 제어','account','• 세션·사업체 멤버십을 서버에서 확인\n• 사업체별 접근 제한과 성공 실행 이용량 관리',1.13)
card(s,'02','청구·입금 원본 등록','registration','• 거래처·청구액·지급기한과 입금을 별도 등록\n• 같은 요청 키의 재전송은 기존 결과 재사용',7.18)
card(s,'03','규칙 기반 매칭 후보','candidates','• 기간·금액·입금자명에 따른 후보와 근거 조회\n• 후보 생성과 사용자의 확정을 분리',13.23)
note(s,'기존 잔액 대사 시스템으로 AI 에이전트 개발을 학습하려는 사용자 동기를 반영했다. 계정·원본·후보 기능은 에이전트 구현이며 사용자 목표·업무 기준과 구분한다. 이미지: 2026-11-05 실제 Java API에 연결한 브라우저 캡처. 시연용 데이터이며 휴대폰 실기기 검증은 아니다. 원본 PPT와 같은20×11.25인치. 기존ERP 뒤 삽입 기준 PROJECT02; 뒤 프로젝트 번호는 원본에서 조정한다.')

s=base('핵심 개발 내용 & 시스템 구성','기존 대사 기능을 보존하고, 입금과 청구 사이의 배분 기록으로 미수금을 관리')
for x,n,title,shot,body in [(1.13,'04','배분·미수금 계산','confirmed-outstanding','• 부분·묶음 입금을 확정하고 남은 금액 조회\n• 부모 행 잠금 후 현재 잔액을 다시 검사'),(7.18,'05','연결 취소·감사 이력','cancellation-audit','• 취소 사유·시각·이력을 기록하고 잔액 복원\n• 청구·입금·기존 배분 원본은 보존')]:
 card(s,n,title,shot,body,x,3.42)
label(s,'실행 아키텍처',13.23,3.4,5.3)
for i,(a,b) in enumerate([('Expo · React Native','등록·검토·결과 표시'),('Java · Spring Boot','권한·잔액 검사·확정'),('PostgreSQL · Flyway','원본·배분·취소·감사')]):
 y=4.12+i*1.43;box(s,13.25,y,5.6,1.02);tx(s,a,13.5,y+.15,5.1,.36,22,'blue',True,True);tx(s,b,13.5,y+.59,5.1,.3,17,center=True)
 if i<2:tx(s,'↓',15.77,y+1.06,.55,.3,20,'blue',center=True)
box(s,1.13,8.58,17.72,1.39,fill='bg')
tx(s,'Controller → Service → JdbcTemplate(SQL) → PostgreSQL',1.5,8.83,16.95,.45,27,'indigo',True,True)
tx(s,'입금 1 : N 배분 라인 N : 1 청구   /   기존 대사 모듈의 JPA 구조 유지',1.5,9.48,16.95,.35,20,center=True)
note(s,'배분 금액은 Java와 DB가 처리한다. AI가 확정하거나 거래를 변경하지 않는다. 실제 콜플로우는 신규미수금JdbcTemplate/기존대사JPA로 구분. docs/architecture.md와 receivables-erd.md 근거. 사진은확정후미수6만원,취소감사. 취소는환불이아니다.')

s=base('역할 & 핵심 기여','개발 목표와 업무 기준을 정하고, 총괄 에이전트에 구현·검토 조율을 맡긴 뒤 직접 실행하며 학습')
items=[('개발자\n목표·검증','기존 원본 보존·Java 금액 계산·사용자 확정 원칙 지정\n개인 PC에서 개발·테스트 DB 분리와 전체 테스트 실행\n안내에 따라 Hermes 예제 수정·실제 Java 조회 확인','업무 기준 · 직접 실행'),('총괄\n작업 조율','기획·조사·디자인·개발·검토의 역할과 산출물 배분\n선행 결과 연결, 기능별 수정 파일 충돌 방지\n독립 작업은 병행하고 하위 에이전트는 최대 2개','역할 분담 · 결과 통합'),('개발·검토\n분리','기능 단위 구현 후 별도 에이전트가 오류 상황 재현\n검토에서 발견한 요청 키 문제를 수정·재검증\n코드·실행 근거·한계를 작업 문서에 기록','구현 · 독립 검토'),('학습\n도구 연결','Hermes 설치·예제 적용·거래처 필드 수정\n읽기 도구로 인증된 Java GET을 호출해 근거 확인\n오류 응답과 자료 없음, 현재 원장과 과거 잔액 구분','Python · Tool Calling')]
for i,(title,body,tag) in enumerate(items):
 x=1.13+(i%2)*9.1;y=3.4+(i//2)*3.43
 box(s,x,y,8.62,2.94);tx(s,title,x+.17,y+.85,2.12,1.1,23,'blue',True,True);ln(s,x+2.4,y+.25,x+2.4,y+2.67)
 tx(s,body,x+2.65,y+.36,5.65,1.95,18);box(s,x+2.65,y+2.4,4.85,.32,'blue','blue');tx(s,tag,x+2.76,y+2.43,4.63,.28,12,'white',center=True)
note(s,'본인기여: 목표·규칙·역할구성요청 및 안내받은 설치·필드수정·명령실행·assertion한줄. 세부DDL·잠금·제품코드·독립검토는AI가수행했다. 독자구현으로표현하지않는다. Hermes는별도local실습, 제품FREE는AI호출없음. 근거PORTFOLIO_LEARNING.md, AGENTS.md.')

s=base('기술 결정의 이유','AI 도구의 성능 순위보다 기존 프로젝트의 재사용, 실행 검증과 학습 흐름을 기준으로 선택')
choices=[('Codex','이미 연결된 저장소·실행 환경에서 분석과 수정을 이어감\n\n총괄·하위 역할의 결과를 같은 프로젝트에 통합\n\nClaude Code 등과 성능 비교를 한 선택은 아니며, 실제 활용할 작업 환경을 기준으로 판단','에이전트 협업'),('Java / Spring Boot','기존 대사 코드와 API를 보존하며 새 미수금 기능 추가\n\n금액 계산과 확정을 서버·DB 트랜잭션에 유지\n\n후보 제안과 확정을 나눠 사용자가 최종 판단','기존 기능 재사용'),('PostgreSQL / Flyway','청구·입금·배분의 관계와 사업체 소유권 관리\n\n부모 행 잠금과 잔액 재검사로 동시 과배분 방지\n\nFlyway로 스키마 변경을 기록하고 개발·테스트 DB 분리','데이터 정합성'),('Hermes / 조회 도구','고정 합성 응답 실습에서 실제 Java GET 연결로 확장\n\n모델에는 날짜만 입력받고 URL·사업체·토큰은 도구에서 제한\n\n서버 금액·건수·API 경로를 설명 근거로 전달','도구 호출 학습')]
for i,(title,body,tag) in enumerate(choices):
 x=1.13+i*4.47;box(s,x,3.47,4.32,6.67);tx(s,title,x+.19,3.87,3.94,.47,23,'indigo',True,True);ln(s,x+.28,4.67,x+4.04,4.67,'blue')
 tx(s,body,x+.34,5.08,3.64,4.25,19);box(s,x+.42,9.55,3.48,.36,'indigo','indigo');tx(s,tag,x+.5,9.59,3.32,.29,13,'white',center=True)
note(s,'Codex만 가능한기능이라고주장하지않는다. Claude Code 등다른도구와실측성능/비용비교없음. 기존연결환경·역할분담·수정/실행/검증을활용한선택이다. 구체DB설계는AI구현,개발자는후속학습. Hermes native plugin이며MCP/RAG구현이라고말하지않는다. asOf는현재원장연체판정기준일이며과거잔액/특정일거래아님. GET제한은서버토큰자체읽기전용권한과동일하지않다.')

s=base('프로젝트 문제 해결 & 배운 점','AI의 구현 결과를 실패 조건과 실행 근거로 확인하며, 금액 처리와 조회 범위를 학습')
for x,title in [(1.13,'문제점 / 확인할 상황'),(10.25,'해결 방법 / 확인 결과')]:
 box(s,x,3.4,8.61,.48,fill='bg');tx(s,title,x+.2,3.48,8.21,.33,20,bold=True,center=True);box(s,x,4.02,8.61,5.91)
left=[('응답 유실 후 재시도','공백만 수정해도 요청 키가 바뀌어, 이미 저장한 입금이 중복될 위험'),('실패 요청의 데이터 변경 여부','충돌 응답만으로는 이전 확정 금액과 잔액이 보존됐는지 알 수 없음'),('AI 조회 결과의 범위','자료 없음과 접속 실패, 현재 원장과 과거 잔액을 혼동할 수 있음')]
right=[('정규화한 내용에 같은 요청 키 유지','저장 후 503 응답을 재현하고 재시도해 입금 1건 확인 · 에이전트 수정/검증'),('잔액 assertion 추가','안내받아 미배분 잔액 60원 검사를 작성하고, 거절 직후 검사하는 이유 설명'),('실제 조회와 모의 오류 검사를 구분','Hermes 실제 GET·답변 확인 / 인증·접속 실패는 모의 검사로 구분')]
for x,items in [(1.13,left),(10.25,right)]:
 for i,(a,b) in enumerate(items):
  y=4.4+i*1.73;tx(s,a,x+.36,y,7.88,.43,23,'blue',True);tx(s,b,x+.36,y+.65,7.88,.9,20)
box(s,1.13,10.24,17.73,.48,fill='bg',stroke='blue');tx(s,'개인 PC 백엔드 186개 통과(11.04)  ·  핵심 코드 설명·실기기·운영 검증은 후속',1.4,10.34,17.19,.33,18,'indigo',True,True)
note(s,'백엔드186은11/04사용자PC XML합산 실패/오류/스킵0. 요청키결함은11/01에이전트실DB브라우저재현, assertion은안내받아사용자채팅작성(파일반영/실행AI), Hermes정상호출은사용자실행확인, 오류는mock. 자료이번생성때전체테스트재실행없음. 테스트assertion60원과실습미수60000원은별개축소금액이다.')

s=base('개발 시연 영상')
video=R/'docs/demo/receivables-demo.mp4';poster=R/'docs/demo/poster.png'
s.shapes.add_picture(str(poster),Inches(5.13),Inches(2.65),width=Inches(9.73),height=Inches(7.3))
s.shapes.add_movie(str(video),Inches(5.13),Inches(2.65),Inches(9.73),Inches(7.3),poster_frame_image=str(poster),mime_type='video/mp4')
t=tx(s,'영상 파일로 열기 ↗',15.62,9.64,3.18,.45,21,'blue',True);t.click_action.hyperlink.address='../demo/receivables-demo.mp4'
tx(s,'청구 등록 → 후보 검토 → 부분 입금 확정 → 잔액 확인 → 연결 취소',2.5,10.32,15,.45,23,center=True)
note(s,'영상106초·2026-11-05 실제JavaAPI와Chromium모바일viewport 녹화. 시연용계정과A거래처,11월청구. 계정·거래처·두번째6만원입금은화면밖준비. 개인실제자료나실기기촬영이아님. 미수100000→60000→0→60000서버검증. PowerPoint에MP4내장,PDF는정지포스터. 영상재생지원은뷰어별차이가있어외부MP4링크도제공. Docker·공개출시는미완료.')
# Visible wording remains plain; data provenance stays in notes and demo documentation.
for sl in p.slides:
 for sh in sl.shapes:
  if sh.has_text_frame:
   for par in sh.text_frame.paragraphs:
    for run in par.runs:run.text=run.text.replace('고정 합성 응답','고정 예제 응답')
O.mkdir(parents=True,exist_ok=True);p.save(O/'chagok-insert.pptx');print('6 slides, 20 x 11.25 inches, embedded video')
