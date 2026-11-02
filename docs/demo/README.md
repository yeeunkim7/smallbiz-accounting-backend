# 실제 화면 시연 영상

[▶ MP4 시청·다운로드](./receivables-demo.mp4) · [한국어 자막](./captions.srt) · [검증 기록](./manifest.json)

[![입금 연결 시연 영상 포스터](./poster.png)](./receivables-demo.mp4)

**브라우저 모바일 화면 · 합성 자료 · 실제 API 연결 · 음성 없음(자막) · 대기 구간 편집**

이 프로젝트의 구현·독립 검토·검증과 영상 제작에는 AI 에이전트를 활용했습니다. [개발 역할과 검증 과정](../PORTFOLIO.md#ai-에이전트를-활용한-개발-과정)을 설명 자료에 명시했습니다. 현재 영상의 금융 업무는 AI 모델 없이 Java/DB로 처리합니다.

Chromium의 390 × 844 뷰포트에서 현재 Expo / React Native Web 화면을 직접 클릭하고, 로컬 Java API와 격리된 PostgreSQL `reconciliation_test`를 연결해 녹화했습니다. 오른쪽은 실제 앱 화면이고 왼쪽 설명과 하단 안내는 영상 편집으로 붙였습니다. 금융 API 응답 대체, 화면 수치 주입, AI 호출은 없습니다. 실기기·네이티브 앱·토스 앱 배포 시연은 아닙니다.

합성 계정·사업장·거래처와 두 번째 60,000원 입금은 장면 밖에서 실제 API로 준비합니다. 100,000원 청구와 첫 40,000원 입금 등록, 후보 생성, 두 차례 연결 확정, 두 번째 연결 취소는 실제 화면 조작입니다. 로그인 장면은 편집에서 제외하고 원본 녹화는 저장소 밖에 보관합니다. 시연 후 합성 사업장과 계정은 테스트 DB에서 삭제합니다.

**파일 규격:** 1분 46초 · 1280 × 960 · H.264 / yuv420p · 25fps · 약 1.39MiB · 오디오 트랙 없음 · faststart. FFprobe 규격 확인, 전체 영상 디코딩, 6개 표본 프레임 육안 확인을 완료했습니다.

## 시연 흐름

1. 100,000원 청구 등록 및 서버 잔액 확인
2. 40,000원 입금 등록 및 연결 전 장부 확인
3. 규칙 후보 생성: 성공 사용량 1회, 자동 연결 0건
4. 40,000원 부분 연결 확정 → 미수 60,000원
5. 60,000원 추가 연결 확정 → 미수 0원
6. 두 번째 연결 취소와 확정·취소 감사 이력 → 미수 60,000원 복원

| 시간 | 장면 |
| --- | --- |
| 00:00 | 청구 입력 |
| 00:11 | 청구 상세 100,000원 |
| 00:20 | 첫 입금 40,000원 등록 |
| 00:31 | 연결 전 장부 |
| 00:40 | 규칙 후보 생성 |
| 00:51 | 40,000원 부분 확정 |
| 01:04 | 남은 금액 60,000원 |
| 01:12 | 추가 확정과 미수 0원 |
| 01:25 | 두 번째 연결 취소와 감사 이력 |
| 01:37 | 미수 60,000원 복원 |

장면별 시작 시간과 실제 미디어 규격은 [`manifest.json`](./manifest.json)에 기록합니다. 잔액은 각 전환에서 실제 API 응답으로 검증하며 프런트엔드에서 재계산하지 않습니다. 후보 생성 사용량은 마지막에도 1회입니다.

## 다시 만들기

저장소의 Java API를 **로컬 테스트 DB**로 8091 포트에서, Expo Web을 8081 포트에서 실행합니다. 비밀값은 저장소 밖에서 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`로 주입합니다. Playwright, Pillow, Chromium, FFmpeg, ffprobe, Noto Sans CJK 글꼴이 필요합니다.

```bash
python3 scripts/record-portfolio-demo.py \
  --api http://127.0.0.1:8091 \
  --web http://127.0.0.1:8081 \
  --output docs/demo \
  --raw-dir /workspace/.local/run/portfolio-video
```

스크립트는 API와 Web이 로컬 HTTP인지, DB가 로컬 `reconciliation_test`인지 확인한 뒤에만 쓰기를 실행합니다. 기본 출력은 MP4, SRT, 포스터와 비밀값 없는 manifest입니다. 원본·편집용 중간 파일은 저장소에 추가하지 마세요. 인증 입력이 들어 있는 원본 디렉터리는 개인 접근 권한으로 생성합니다.

이 환경에서는 Playwright 녹화 helper가 없어, 설치된 시스템 FFmpeg를 전용 임시 캐시에 연결해 실행했습니다. 같은 경우 다음 준비 후 위 명령 앞에 `PLAYWRIGHT_BROWSERS_PATH=/tmp/portfolio-pw-cache`를 붙일 수 있습니다.

```bash
mkdir -p /tmp/portfolio-pw-cache/ffmpeg-1011
ln -s /usr/bin/ffmpeg /tmp/portfolio-pw-cache/ffmpeg-1011/ffmpeg-linux
```

이 영상은 해당 시연 흐름의 실제 실행 기록이며 전체 회귀 테스트 결과를 대신하지 않습니다.
