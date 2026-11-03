# 발표·소개 자료

| 자료 | 파일 |
| --- | --- |
| 발표 자료 5장 | [PPTX](chagok-project.pptx) · [PDF](chagok-project.pdf) |
| 소개서 2쪽 | [PDF](overview.pdf) · [HTML 원본](overview.html) |
| 시연 영상 | [MP4](../demo/receivables-demo.mp4) · [자막](../demo/captions.srt) |

## 기존 포트폴리오에 넣기

ERP 시연 다음에 5장을 넣고, 붙여넣을 때 **원본 서식 유지**를 선택하면 됩니다. 새 장표는 PROJECT 02로 구성했습니다. 기존 면접 프로젝트는 PROJECT 03, 중고거래는 PROJECT 04로 바꾸고 목차와 타임라인도 맞춰 주세요.

3장의 시연 영상 링크는 GitHub의 main 브랜치 영상으로 연결됩니다. 발표 전에 링크를 열어 재생하거나 영상을 별도로 내려받아 두면 됩니다.

## 자료 다시 만들기

```sh
python scripts/build-portfolio-slides.py
python scripts/render-portfolio-pdf.py
```

PPT 생성에는 python-pptx와 Pillow, 소개서 PDF 생성에는 Playwright와 Chromium이 필요합니다. 발표 PDF는 PPT를 LibreOffice 또는 PowerPoint에서 내보내면 됩니다.

2026-11-03 발표 자료 5장과 발표 PDF를 최신 문서에 맞춰 갱신했습니다. 구성은 Codex 선택 배경 → 역할 구성·개발 절차 → 실제 시스템 구조·기능 → 검토·수정 사례 → 배운 점·다음 검증입니다. 각 장의 발표자 노트에 설명과 근거를 넣었습니다.

[핵심 코드 학습과 예상 질문](../PORTFOLIO_LEARNING.md)을 따라 본인이 설명할 수 있는 범위를 확인합니다. 개발자 목표·업무 기준과 에이전트의 구현·검증 실행은 구분합니다. 소개서 2쪽은 이전 요약 자료이며 이번에 재생성하지 않았습니다.
