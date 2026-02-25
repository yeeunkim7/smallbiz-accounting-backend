from __future__ import annotations


class JournalValidator:
    """
    분개 관련 도메인 검증 로직.

    Java `JournalValidator`의 금액 검증 규칙을 그대로 옮긴 구현입니다.
    """

    @staticmethod
    def validate_amount(debit_amount: int | None, credit_amount: int | None) -> None:
        """
        차변/대변 금액 검증.

        - 둘 다 null 불가
        - 음수 불가
        - 차변과 대변이 동시에 양수일 수 없음
        - 둘 다 0일 수 없음
        """
        if debit_amount is None or credit_amount is None:
            raise ValueError("차변/대변 금액은 null일 수 없습니다.")

        if debit_amount < 0 or credit_amount < 0:
            raise ValueError("금액은 음수일 수 없습니다.")

        if debit_amount > 0 and credit_amount > 0:
            raise ValueError("차변과 대변 중 하나만 금액을 가질 수 있습니다.")

        if debit_amount == 0 and credit_amount == 0:
            raise ValueError("차변 또는 대변 중 하나는 금액이 있어야 합니다.")

