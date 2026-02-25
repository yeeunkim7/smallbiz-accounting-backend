from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_db
from app.models.account_subject import AccountSubject
from app.models.journal import Journal
from app.schemas.account_subject import (
    AccountSubjectCreate,
    AccountSubjectRead,
    AccountSubjectUpdate,
)


router = APIRouter(
    prefix="/account-subjects",
    tags=["account_subjects"],
)


@router.post(
    "",
    response_model=AccountSubjectRead,
    status_code=status.HTTP_201_CREATED,
)
def create_account_subject(
    payload: AccountSubjectCreate,
    db: Session = Depends(get_db),
) -> AccountSubjectRead:
    """
    계정과목 생성.
    """
    # 코드 중복 체크
    stmt = select(AccountSubject).where(AccountSubject.code == payload.code)
    existing = db.scalars(stmt).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미 존재하는 계정코드입니다.",
        )

    subject = AccountSubject(
        code=payload.code,
        name=payload.name,
        type=payload.type,
    )
    db.add(subject)
    db.flush()
    db.refresh(subject)
    return subject


@router.get(
    "",
    response_model=List[AccountSubjectRead],
)
def list_account_subjects(
    page: int = Query(1, ge=1),
    size: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
) -> List[AccountSubjectRead]:
    """
    계정과목 목록 조회 (페이지네이션).
    """
    offset = (page - 1) * size
    stmt = (
        select(AccountSubject)
        .order_by(AccountSubject.code.asc())
        .offset(offset)
        .limit(size)
    )
    subjects = db.scalars(stmt).all()
    return list(subjects)


@router.get(
    "/{account_subject_id}",
    response_model=AccountSubjectRead,
)
def get_account_subject(
    account_subject_id: int,
    db: Session = Depends(get_db),
) -> AccountSubjectRead:
    subject = db.get(AccountSubject, account_subject_id)
    if subject is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="계정과목을 찾을 수 없습니다.",
        )
    return subject


@router.put(
    "/{account_subject_id}",
    response_model=AccountSubjectRead,
)
def update_account_subject(
    account_subject_id: int,
    payload: AccountSubjectUpdate,
    db: Session = Depends(get_db),
) -> AccountSubjectRead:
    """
    계정과목 전체 업데이트 (PUT).
    """
    subject = db.get(AccountSubject, account_subject_id)
    if subject is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="계정과목을 찾을 수 없습니다.",
        )

    # 코드가 변경되는 경우 중복 체크
    if payload.code != subject.code:
        stmt = select(AccountSubject).where(AccountSubject.code == payload.code)
        existing = db.scalars(stmt).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="이미 존재하는 계정코드입니다.",
            )

    subject.code = payload.code
    subject.name = payload.name
    subject.type = payload.type

    db.flush()
    db.refresh(subject)
    return subject


@router.delete(
    "/{account_subject_id}",
    response_model=AccountSubjectRead,
)
def delete_account_subject(
    account_subject_id: int,
    db: Session = Depends(get_db),
) -> AccountSubjectRead:
    """
    계정과목 삭제.

    연관된 Journal이 있으면 삭제를 막습니다.
    """
    subject = db.get(AccountSubject, account_subject_id)
    if subject is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="계정과목을 찾을 수 없습니다.",
        )

    # 연관 Journal 존재 여부 체크
    journal_stmt = select(Journal).where(
        Journal.account_subject_id == account_subject_id
    )
    has_journal = db.scalars(journal_stmt).first() is not None
    if has_journal:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이 계정과목을 사용하는 전표가 있어 삭제할 수 없습니다.",
        )

    db.delete(subject)
    db.flush()
    return subject

