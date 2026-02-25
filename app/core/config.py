from __future__ import annotations

import os
from pathlib import Path


# 프로젝트 루트 기준 SQLite 파일 경로 (예: ./accounting.db)
BASE_DIR = Path(__file__).resolve().parent.parent.parent
DEFAULT_SQLITE_PATH = BASE_DIR / "accounting.db"

# 환경변수로 DATABASE_URL을 오버라이드할 수 있고,
# 설정하지 않으면 로컬 개발용 SQLite를 기본으로 사용합니다.
DATABASE_URL: str = os.getenv(
    "DATABASE_URL",
    f"sqlite:///{DEFAULT_SQLITE_PATH}",
)

