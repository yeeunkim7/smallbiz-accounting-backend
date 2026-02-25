from __future__ import annotations

from datetime import date
from typing import Sequence

from sqlalchemy.orm import Session

from app.models.account_subject import AccountSubject
from app.models.journal import Journal
from app.models.vendor import Vendor
from app.repositories.journal_repository import JournalRepository
from app.services.validators import JournalValidator


class JournalService:
    """
    분개 관련 비즈니스 로직.

    Java `JournalService`의 책임을 Python/SQLAlchemy 스타일로 옮긴 서비스 레이어입니다.
    """

    def __init__(
        self,
        session: Session,
        journal_repository: JournalRepository | None = None,
        journal_validator: JournalValidator | None = None,
    ) -> None:
        self._session = session
        self._journal_repository = journal_repository or JournalRepository(session)
        self._journal_validator = journal_validator or JournalValidator()

    # 분개 등록
    def register_journal(
        self,
        journal_date: date,
        description: str,
        debit_amount: int,
        credit_amount: int,
        vendor_id: int | None,
        account_subject_id: int,
    ) -> Journal:
        """
        분개 등록.

        Java 구현과 동일하게:
        1. 금액 검증
        2. 계정과목 조회 (필수)
        3. 거래처 조회 (선택)
        4. Journal 생성 및 저장
        """
        # 1. 금액 검증
        self._journal_validator.validate_amount(debit_amount, credit_amount)

        # 2. 계정과목 조회 (필수)
        account_subject = self._session.get(AccountSubject, account_subject_id)
        if account_subject is None:
            raise ValueError("존재하지 않는 계정과목입니다.")

        # 3. 거래처 조회 (선택)
        vendor: Vendor | None = None
        if vendor_id is not None:
            vendor = self._session.get(Vendor, vendor_id)
            if vendor is None:
                raise ValueError("존재하지 않는 거래처입니다.")

        # 4. Journal 생성
        journal = Journal(
            journal_date=journal_date,
            description=description,
            debit_amount=debit_amount,
            credit_amount=credit_amount,
            vendor=vendor,
            account_subject=account_subject,
        )

        # 저장 (commit은 상위에서 관리)
        return self._journal_repository.save(journal)

    # 전체 분개 조회
    def find_all_journals(self) -> Sequence[Journal]:
        return self._journal_repository.find_all()

    # 기간별 분개 조회
    def find_journals_by_date_range(
        self,
        start_date: date,
        end_date: date,
    ) -> Sequence[Journal]:
        return self._journal_repository.find_by_date_range(start_date, end_date)

    # 거래처별 분개 조회
    def find_journals_by_vendor(self, vendor_id: int) -> Sequence[Journal]:
        return self._journal_repository.find_by_vendor_id(vendor_id)

    # 단건 조회
    def find_journal(self, journal_id: int) -> Journal:
        journal = self._journal_repository.find_by_id(journal_id)
        if journal is None:
            raise ValueError("존재하지 않는 분개입니다.")
        return journal

