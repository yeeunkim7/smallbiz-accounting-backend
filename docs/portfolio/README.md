# 차곡 발표 자료

## 기존 포트폴리오 삽입용 · 최신

[PPT · 차곡 6장](chagok-insert.pptx) · [PDF](chagok-insert.pdf)

원본의 세 프로젝트를 참고해 개요·수행 내용(2장) → 역할과 핵심 기여 → 기술 선택 이유 → 문제 해결·학습 → 시연 영상으로 맞췄습니다. 원본과 같은20×11.25인치, JASO Sans 글꼴 지정, 파란 구분선·테두리 카드·문제/해결 비교 구성을 사용합니다. 클라우드PDF는 JASO 미설치로 대체 글꼴로 렌더링됩니다. 원본을 사용하는 PC에 JASO 글꼴이 있으면 PPT에서 원래 글꼴이 표시됩니다.

ERP 프로젝트 뒤 삽입하는 PROJECT02 기준입니다. 기존 포트폴리오에서 **슬라이드 다시 사용 → 원본 서식 유지**로 이6장만 넣고 기존 모의면접/중고거래 번호와 목차를03/04로 조정하세요. 다른 프로젝트나 개인정보 페이지는 이 파일에 포함하지 않았습니다.

마지막 장에 MP4를 내장했습니다. PowerPoint 버전에 따라 재생 지원이 달라 외부 영상 링크도 제공합니다. PDF에는 정지 이미지가 표시됩니다. 재생성은 `python scripts/build-portfolio-insert.py` 후 LibreOffice/PowerPoint에서PDF로 내보내면 됩니다.

## 별도 상세 발표 자료 · 11장


[발표 PPT · 11장](chagok-project.pptx) · [PDF](chagok-project.pdf) · [시연 영상 · 1분 46초](../demo/receivables-demo.mp4)

2026-11-05 사용자 요청에 따라 학습 동기 → Codex 선택·구성 → 개발 결과 → 성과·배운 점 순서로 구성했습니다. 제공한 포트폴리오 예시의 파란 제목·선·화면 카드 구성을 참고하고 차곡의 실제 화면을 사용했습니다.

| 구간 | 설명 |
| --- | --- |
| 1 | 기존 잔액 대사 시스템을 AI 에이전트 개발 학습에 활용한 배경 |
| 2 | 다른 도구 대신 Codex를 선택한 프로젝트별 기준 |
| 3–4 | 총괄·하위 에이전트 구성과 제품 아키텍처·기술 스택 |
| 5–7 | 핵심 개발5가지와 실제 화면 |
| 8–9 | 독립 검토의 수정 사례와 개인 PC의 Hermes 조회 도구 실습 |
| 10–11 | 개발 결과의 장점, 검증 근거와 직접 배운 점 |

핵심5가지는 계정·사업체 권한 / 청구·입금 등록 / 규칙 기반 후보 / 배분·미수 계산 / 취소·감사 이력입니다. 모바일 화면의 가독성을 위해 2개·2개·1개로 나누었습니다. 캡처는 [새 시연 화면](../screenshots)에 있으며 자료 종류와 실행 근거는 [영상 안내](../demo/README.md)에 한 번 정리했습니다.

Codex의 선택 이유는 기존 연결 환경, 총괄·하위 역할 활용, 수정·실행·검토의 연결입니다. Claude Code와 비교 실험을 했거나 특정 기능이 Codex에만 있다고 주장하지 않습니다. 발표자 노트에 실제 기여 범위와 근거를 넣었습니다.

7장의 영상 링크는 `../demo/receivables-demo.mp4`입니다. portfolio와 demo 폴더를 함께 내려받거나 영상 파일을 직접 열어 주세요. 기존 포트폴리오에 넣을 때는 원본 서식 유지를 선택하세요.

## 다시 만들기

```sh
python scripts/build-portfolio-slides.py
soffice --headless --convert-to pdf --outdir docs/portfolio docs/portfolio/chagok-project.pptx
```

Python의 python-pptx·Pillow, Noto Sans CJK KR 글꼴과 LibreOffice가 필요합니다. 개인 PC 실행·학습 범위는 [학습 기록](../PORTFOLIO_LEARNING.md)을 참고하세요. [이전 소개서2쪽](overview.pdf)은 이번 변경을 반영하지 않은 과거 요약입니다.
