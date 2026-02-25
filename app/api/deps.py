from __future__ import annotations

from typing import Generator

from sqlalchemy.orm import Session

from app.core.db import get_session


def get_db() -> Generator[Session, None, None]:
    """
    FastAPI dependency for providing a DB session.

    Usage:
        db: Session = Depends(get_db)
    """
    yield from get_session()

