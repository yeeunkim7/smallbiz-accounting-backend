# 발표·소개 자료

| 자료 | 파일 |
| --- | --- |
| 발표 자료 5장 | [PPTX](chagok-project.pptx) · [PDF](chagok-project.pdf) |
| 소개서 2쪽 | [PDF](overview.pdf) · [HTML 원본](overview.html) |
| 시연 영상 | [MP4](../demo/receivables-demo.mp4) · [자막](../demo/captions.srt) |

## 기존 포트폴리오에 넣기

ERP 시연 다음에 5장을 넣고, 붙여넣을 때 **원본 서식 유지**를 선택하면 됩니다. 새 장표는 PROJECT 02로 구성했습니다. 기존 면접 프로젝트는 PROJECT 03, 중고거래는 PROJECT 04로 바꾸고 목차와 타임라인도 맞춰 주세요.

마지막 장의 영상 포스터와 버튼에는 GitHub 시연 영상 링크가 연결돼 있습니다. 발표 전에 링크를 열어 재생하거나 영상을 별도로 내려받아 두면 됩니다.

## 자료 다시 만들기

```sh
python scripts/build-portfolio-slides.py
python scripts/render-portfolio-pdf.py
```

PPT 생성에는 python-pptx와 Pillow, 소개서 PDF 생성에는 Playwright와 Chromium이 필요합니다. 발표 PDF는 PPT를 LibreOffice 또는 PowerPoint에서 내보내면 됩니다.

2026-11-02 Markdown 문서 검토에서는 PPT·PDF·영상 파일을 재생성하지 않았다. 최신 역할·설계 판단의 출처와 미검증 범위는 [PORTFOLIO.md](../PORTFOLIO.md)를 기준으로 하며, 발표 자료를 다시 배포할 때 이 내용과 대조한다.
