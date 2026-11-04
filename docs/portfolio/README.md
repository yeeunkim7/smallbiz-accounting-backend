# 차곡 발표 자료

[발표 PPT · 5장](chagok-project.pptx) · [PDF](chagok-project.pdf) · [시연 영상 · 1분 37초](../demo/receivables-demo.mp4)

2026-11-04까지의 개발과 개인 PC 실습을 반영했습니다. 각 장의 발표자 노트에 설명과 코드·실행 근거가 있습니다.

| 장 | 내용 |
| --- | --- |
| 1 | 차곡이 해결하는 문제와 청구·입금·미수금 |
| 2 | Codex 선택 이유, 총괄·하위 역할, 구현·검토·수정 절차 |
| 3 | 제품 아키텍처·기술 스택과 별도 Hermes 조회 실습 |
| 4 | 개인 PC 테스트186개, 실제 Java 조회·도구 호출, 중복 저장 수정 사례 |
| 5 | 업무 규칙·실패 처리·조회 범위에서 배운 점과 직접 수행한 일 |

1장 화면을 클릭하면 상대 경로 `../demo/receivables-demo.mp4`를 엽니다. 내려받을 때 portfolio와 demo 폴더를 함께 유지하세요. PDF 뷰어에 따라 파일 링크를 막을 수 있으므로 영상 파일을 직접 열어도 됩니다.

## 기존 포트폴리오에 넣기

ERP 프로젝트 뒤에 5장을 넣고 ‘원본 서식 유지’를 선택하면 됩니다. 기존 면접 프로젝트·중고거래 프로젝트 순서와 목차 번호를 맞추세요. 실제 설명은 발표자 노트와 [학습 기록](../PORTFOLIO_LEARNING.md)을 바탕으로 본인 말로 정리하면 됩니다.

제품 코드·세부 설계에는 AI 에이전트를 활용했고, 개인 PC 환경 설치·실행·필드 수정·테스트는 안내를 받아 직접 수행했습니다. PPT에 이 역할을 구분했습니다. 실제 모델 조회는 로컬 실습 범위이며 제품 FREE의 AI 기능이 아닙니다.

## 다시 생성하기

```sh
python scripts/build-portfolio-slides.py
soffice --headless --convert-to pdf --outdir docs/portfolio docs/portfolio/chagok-project.pptx
```

Python의 python-pptx·Pillow와 Noto Sans CJK KR 글꼴이 필요합니다. PDF는 LibreOffice 또는 PowerPoint에서 내보냅니다. 영상 재편집 절차와 녹화·편집 검증 범위는 [영상 안내](../demo/README.md)를 따릅니다.

[이전 소개서2쪽](overview.pdf) · [HTML 원본](overview.html)은 기존 요약 자료이며 이번 Hermes 실습을 반영하지 않았습니다. 최신 발표는 위5장 자료를 사용하세요.
