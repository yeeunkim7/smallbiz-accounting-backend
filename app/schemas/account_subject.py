from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class AccountSubjectBase(BaseModel):
    code: str = Field(..., max_length=50, examples=["5000"])
    name: str = Field(..., max_length=100, examples=["접대비"])
    type: Literal["자산", "부채", "자본", "수익", "비용"] = Field(
        ...,
        description="계정 유형 (자산/부채/자본/수익/비용)",
    )


class AccountSubjectCreate(AccountSubjectBase):
    """계정과목 생성 요청 스키마."""

    pass


class AccountSubjectUpdate(AccountSubjectBase):
    """계정과목 전체 업데이트(PUT) 요청 스키마."""

    pass


class AccountSubjectRead(AccountSubjectBase):
    """계정과목 응답 스키마."""

    id: int
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

