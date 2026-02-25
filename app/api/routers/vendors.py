from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.api.deps import get_db
from app.models.journal import Journal
from app.models.vendor import Vendor
from app.schemas.vendor import VendorCreate, VendorRead, VendorUpdate


router = APIRouter(
    prefix="/vendors",
    tags=["vendors"],
)


@router.post(
    "",
    response_model=VendorRead,
    status_code=status.HTTP_201_CREATED,
)
def create_vendor(
    payload: VendorCreate,
    db: Session = Depends(get_db),
) -> VendorRead:
    """
    거래처 생성.
    """
    # 사업자번호 중복 체크 (입력된 경우에만)
    if payload.business_number:
        stmt = select(Vendor).where(
            func.lower(Vendor.business_number) == func.lower(payload.business_number)
        )
        existing = db.scalars(stmt).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="business_number already exists",
            )

    vendor = Vendor(
        name=payload.name,
        business_number=payload.business_number,
        ceo_name=payload.ceo_name,
        address=payload.address,
        phone=payload.phone,
    )
    db.add(vendor)
    db.flush()
    db.refresh(vendor)
    return vendor


@router.get(
    "",
    response_model=List[VendorRead],
)
def list_vendors(
    page: int = Query(1, ge=1),
    size: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
) -> List[VendorRead]:
    """
    거래처 목록 조회 (페이지네이션).
    """
    offset = (page - 1) * size
    stmt = (
        select(Vendor)
        .order_by(Vendor.id.desc())
        .offset(offset)
        .limit(size)
    )
    vendors = db.scalars(stmt).all()
    return list(vendors)


@router.get(
    "/{vendor_id}",
    response_model=VendorRead,
)
def get_vendor(
    vendor_id: int,
    db: Session = Depends(get_db),
) -> VendorRead:
    vendor = db.get(Vendor, vendor_id)
    if vendor is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="거래처를 찾을 수 없습니다.",
        )
    return vendor


@router.put(
    "/{vendor_id}",
    response_model=VendorRead,
)
def update_vendor(
    vendor_id: int,
    payload: VendorUpdate,
    db: Session = Depends(get_db),
) -> VendorRead:
    """
    거래처 전체 업데이트 (PUT).
    """
    vendor = db.get(Vendor, vendor_id)
    if vendor is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="거래처를 찾을 수 없습니다.",
        )

    # 사업자번호가 변경된 경우에만 중복 체크
    if payload.business_number and payload.business_number != vendor.business_number:
        stmt = select(Vendor).where(
            func.lower(Vendor.business_number) == func.lower(payload.business_number)
        )
        existing = db.scalars(stmt).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="business_number already exists",
            )

    vendor.name = payload.name
    vendor.business_number = payload.business_number
    vendor.ceo_name = payload.ceo_name
    vendor.address = payload.address
    vendor.phone = payload.phone

    db.flush()
    db.refresh(vendor)
    return vendor


@router.delete(
    "/{vendor_id}",
    response_model=VendorRead,
)
def delete_vendor(
    vendor_id: int,
    db: Session = Depends(get_db),
) -> VendorRead:
    """
    거래처 삭제.

    연관된 Journal이 있으면 삭제를 막습니다.
    """
    vendor = db.get(Vendor, vendor_id)
    if vendor is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="거래처를 찾을 수 없습니다.",
        )

    # 연관 Journal 존재 여부 체크
    journal_stmt = select(Journal).where(Journal.vendor_id == vendor_id)
    has_journal = db.scalars(journal_stmt).first() is not None
    if has_journal:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이 거래처를 사용하는 전표가 있어 삭제할 수 없습니다.",
        )

    db.delete(vendor)
    db.flush()
    return vendor

