# 제출용 자료

- [프로젝트 소개 PDF · 2쪽](overview.pdf)
- [편집 가능한 HTML 원본](overview.html)
- [시연 영상·자막](../demo/README.md)
- [설계 판단과 검증 범위](../PORTFOLIO.md)
- [아키텍처](../architecture.md) · [핵심 ERD](../receivables-erd.md)

PDF는 구현 근거·테스트 종류·남은 범위를 요약한 자료입니다. 개인 이력·연락처·실제 고객·매출·실측 성능을 추가로 가정하지 않았습니다. 영상은 합성 자료와 실제 API를 사용하는 브라우저 녹화이며 실기기·앱인토스 실행과 구분합니다.

Playwright/Chromium 설치 후 저장소 루트에서 다시 생성할 수 있습니다.

```sh
python scripts/render-portfolio-pdf.py
```

인쇄 과정에서는 문서 내용을 직접 전달하고 저장소의 캡처를 인라인으로 포함하여 외부 네트워크 없이 렌더링합니다. `--output`과 `CHROMIUM_PATH`를 선택적으로 지정할 수 있습니다. 기존 기능 검사 수치는 해당 기록일의 실행 근거이며 자료 생성 때 전체 기능 테스트를 새로 실행했다는 의미가 아닙니다.
