from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api.deps import get_db
from app.schemas.journal import JournalCreate, JournalRead
from app.services.journal_service import JournalService


router = APIRouter(
    prefix="/journals",
    tags=["journals"],
)


@router.post(
    "",
    response_model=JournalRead,
    status_code=status.HTTP_201_CREATED,
)
def create_journal(
    payload: JournalCreate,
    db: Session = Depends(get_db),
) -> JournalRead:
    """
    분개 생성 API.
    """
    service = JournalService(session=db)
    try:
        journal = service.register_journal(
            journal_date=payload.journal_date,
            description=payload.description,
            debit_amount=payload.debit_amount,
            credit_amount=payload.credit_amount,
            vendor_id=payload.vendor_id,
            account_subject_id=payload.account_subject_id,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return journal


@router.get(
    "",
    response_model=List[JournalRead],
)
def get_journals(
    db: Session = Depends(get_db),
) -> List[JournalRead]:
    """
    전체 분개 목록 조회 API.
    """
    service = JournalService(session=db)
    journals = service.find_all_journals()
    return list(journals)

