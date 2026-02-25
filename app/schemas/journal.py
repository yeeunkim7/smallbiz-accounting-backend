from __future__ import annotations

from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict


class JournalBase(BaseModel):
    journal_date: date
    description: str
    debit_amount: int
    credit_amount: int
    vendor_id: Optional[int] = None
    account_subject_id: int


class JournalCreate(JournalBase):
    """
    분개 생성 요청 스키마.
    Java Controller의 입력 파라미터(journalDate, description, debitAmount, creditAmount, vendorId, accountSubjectId)에 대응합니다.
    """

    pass


class JournalRead(JournalBase):
    """
    분개 응답 스키마.
    Java Controller가 반환하던 Journal 엔티티를 기반으로 한 단순화된 표현입니다.
    """

    id: int
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

