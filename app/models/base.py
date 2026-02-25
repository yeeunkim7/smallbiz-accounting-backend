from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, func, Integer
from sqlalchemy.orm import Mapped, mapped_column, declared_attr

from app.core.db import Base


class BaseEntityMixin:
    """
    Mixin that provides id, created_at, updated_at fields.

    Mirrors the Java BaseEntity: auto-increment PK and audit timestamps.
    """

    @declared_attr
    def id(cls) -> Mapped[int]:  # type: ignore[override]
        return mapped_column(Integer, primary_key=True, autoincrement=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )


class BaseEntity(Base, BaseEntityMixin):
    """
    Concrete abstract mapped base that combines the SQLAlchemy Base
    with the BaseEntityMixin fields.
    """

    __abstract__ = True

