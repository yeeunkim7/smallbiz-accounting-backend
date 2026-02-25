from __future__ import annotations

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import BaseEntity


class AccountSubject(BaseEntity):
    """
    계정과목 엔티티.

    Java `AccountSubject`와 필드 스펙을 맞춘 SQLAlchemy 모델입니다.
    """

    __tablename__ = "account_subject"

    code: Mapped[str] = mapped_column(String(50), nullable=False)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    type: Mapped[str] = mapped_column(String(20), nullable=False)

    # 관계: Journal(N) <-> AccountSubject(1)
    journals: Mapped[list["Journal"]] = relationship(
        back_populates="account_subject",
        cascade="all, delete-orphan",
    )


from app.models.journal import Journal  # noqa: E402  # isort: skip

