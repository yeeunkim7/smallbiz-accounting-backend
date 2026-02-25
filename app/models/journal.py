from __future__ import annotations

from datetime import date

from sqlalchemy import Date, ForeignKey, String, BigInteger
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import BaseEntity


class Journal(BaseEntity):
    """
    분개 엔티티.

    Java `Journal`과 필드/관계를 맞춘 SQLAlchemy 모델입니다.
    """

    __tablename__ = "journal"

    journal_date: Mapped[date] = mapped_column(Date, nullable=False)
    description: Mapped[str] = mapped_column(String(200), nullable=False)
    debit_amount: Mapped[int] = mapped_column(BigInteger, nullable=False)
    credit_amount: Mapped[int] = mapped_column(BigInteger, nullable=False)

    vendor_id: Mapped[int | None] = mapped_column(
        ForeignKey("vendor.id"),
        nullable=True,
    )
    account_subject_id: Mapped[int] = mapped_column(
        ForeignKey("account_subject.id"),
        nullable=False,
    )

    vendor: Mapped["Vendor | None"] = relationship(
        back_populates="journals",
    )
    account_subject: Mapped["AccountSubject"] = relationship(
        back_populates="journals",
    )


from app.models.vendor import Vendor  # noqa: E402  # isort: skip
from app.models.account_subject import AccountSubject  # noqa: E402  # isort: skip

