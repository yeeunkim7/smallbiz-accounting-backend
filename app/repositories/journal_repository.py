from __future__ import annotations

from datetime import date
from typing import Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.journal import Journal


class JournalRepository:
    """
    분개(Journal) 엔티티에 대한 DB 접근 레이어.

    Java `JournalRepository`의 기능을 SQLAlchemy 2.0 스타일로 구현합니다.
    """

    def __init__(self, session: Session) -> None:
        self._session = session

    def save(self, journal: Journal) -> Journal:
        """
        새로운 분개를 저장합니다.
        """
        self._session.add(journal)
        # flush까지는 여기서, commit은 상위 트랜잭션 관리자가 담당
        self._session.flush()
        return journal

    def find_all(self) -> Sequence[Journal]:
        """
        전체 분개 목록 조회.
        """
        stmt = select(Journal)
        return list(self._session.scalars(stmt).all())

    def find_by_date_range(self, start_date: date, end_date: date) -> Sequence[Journal]:
        """
        기간별 분개 조회.
        """
        stmt = (
            select(Journal)
            .where(Journal.journal_date.between(start_date, end_date))
            .order_by(Journal.journal_date)
        )
        return list(self._session.scalars(stmt).all())

    def find_by_vendor_id(self, vendor_id: int) -> Sequence[Journal]:
        """
        거래처별 분개 조회.
        """
        stmt = select(Journal).where(Journal.vendor_id == vendor_id)
        return list(self._session.scalars(stmt).all())

    def find_by_id(self, journal_id: int) -> Journal | None:
        """
        단일 분개 조회.
        """
        return self._session.get(Journal, journal_id)

