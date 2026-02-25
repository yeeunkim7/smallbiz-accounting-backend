from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class VendorBase(BaseModel):
    name: str = Field(..., max_length=100, examples=["테스트 거래처"])
    business_number: Optional[str] = Field(
        None,
        max_length=20,
        examples=["123-45-67890"],
    )
    ceo_name: Optional[str] = Field(None, max_length=50)
    address: Optional[str] = Field(None, max_length=200)
    phone: Optional[str] = Field(None, max_length=20)


class VendorCreate(VendorBase):
    """거래처 생성 요청 스키마."""

    pass


class VendorUpdate(VendorBase):
    """거래처 전체 업데이트(PUT) 요청 스키마."""

    pass


class VendorRead(VendorBase):
    """거래처 응답 스키마."""

    id: int
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

