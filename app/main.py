from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routers import account_subjects, journals, vendors


def create_app() -> FastAPI:
    app = FastAPI(
        title="SmallBiz Accounting Backend",
        version="0.1.0",
    )

    # CORS 설정: 로컬 파일(file://)에서 127.0.0.1:8000 호출 허용
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],  # 필요시 특정 도메인으로 제한 가능
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 라우터 등록
    app.include_router(account_subjects.router)
    app.include_router(vendors.router)
    app.include_router(journals.router)

    @app.get("/health")
    def health_check() -> dict[str, str]:
        return {"status": "ok"}

    return app


app = create_app()

