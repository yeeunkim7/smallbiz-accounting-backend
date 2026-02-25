from __future__ import annotations

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import BaseEntity


class Vendor(BaseEntity):
    """
    거래처 엔티티.

    Java `Vendor`와 필드 스펙을 맞춘 SQLAlchemy 모델입니다.
    """

    __tablename__ = "vendor"

    name: Mapped[str] = mapped_column(String(100), nullable=False)
    business_number: Mapped[str | None] = mapped_column(String(20), nullable=True)
    ceo_name: Mapped[str | None] = mapped_column(String(50), nullable=True)
    address: Mapped[str | None] = mapped_column(String(200), nullable=True)
    phone: Mapped[str | None] = mapped_column(String(20), nullable=True)

    # 관계: Journal(N) <-> Vendor(1, optional)
    journals: Mapped[list["Journal"]] = relationship(
        back_populates="vendor",
        cascade="all, delete-orphan",
    )


from app.models.journal import Journal  # noqa: E402  # isort: skip

