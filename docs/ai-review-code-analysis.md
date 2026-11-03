# Existing code and integration boundary

> 문서 범위: 초기 코드 분석 이력이다. 아래 HEAD·테스트 수와 기능 미존재는 당시 상태다. 988a983은 사용자가 미커밋 작업이었다고 확인해 GitHub 기준으로 진행했으며 추가 SHA 요청은 해결됐다. 현재 구조는 [아키텍처](architecture.md)를 따른다.

Inspected 2026-10-31. Starting HEAD: `580adf5d496b5c59093d3075548dd7897eed8c23`.

## Requested commit comparison

`git cat-file -t 988a983` reports no object. The checkout is not shallow. `git ls-remote origin` advertises only HEAD/main at 580adf5; `git fetch --no-tags origin 988a983` fails with `couldn't find remote ref`. A full SHA or accessible branch/PR is needed for an actual diff. No replacement SHA or reset was used.

## Existing flows

Paths below are relative to `backend-java/src/main/java/com/smallbiz/reconciliation`.

| Flow | Existing methods | Reuse / constraint |
| --- | --- | --- |
| Upload | upload/BusinessUploadFacade.upload, BankUploadFacade.upload → BusinessUploadService.save, BankUploadService.save | Read/parse/hash first; save metadata and source rows in a transaction; duplicate protection preserved |
| Reconciliation | reconciliation/ReconciliationQueryService.daily/monthly → ReconciliationAggregationRepository.findDailyTotals | Existing Java amount differences and statuses; daily inclusive limit 366 days |
| Source rows | reconciliation/ReconciliationOriginalQueryService.businessDay/bankDay | Single date only; pagination 20 default/100 max enforced in controller; new service callers must validate independently |
| Upload history | upload/UploadQueryService.findPage/findById | Type filter and newest-first metadata; source upload IDs already available |
| Review | reconciliation/DailyReviewService.get/statusesFor vs put/markOriginalsChanged | AI may read only; writes remain outside tool registry |

Before this implementation there was no `DailySummaryQueryService` or corresponding test. Existing coverage includes ReconciliationQueryIntegrationTest (5), ReconciliationOriginalQueryIntegrationTest (4), UploadQueryIntegrationTest (3), within 56 existing tests.

## Business semantics

- Cash IN: CHARGE_EXPECTED + SETTLEMENT_EXPECTED on expected_cash_date; OUT: REFUND_EXPECTED. USAGE is excluded. Bank side uses booked_date and IN/OUT.
- Existing SQL aggregates each side before joining to prevent row multiplication. Actual minus expected is computed by existing Java logic. Reuse rather than reproduce calculations in the model.
- Existing sourcePresence is date-level, not direction-level. Zero OUT difference on an IN-only day does not establish OUT source presence.
- Empty daily period returns `days: []`; empty month returns zero totals and dataDayCount=0. TOTAL_EQUAL alone cannot establish data presence.
- Existing test compares business 500000+800000 with bank 400000+900000 as equal totals. That does not perform individual matching.
- Related uploads must come from DISTINCT source upload IDs over the complete requested cash-date range. Upload timestamp and one page of originals are insufficient substitutes.

## Added boundary design

New query code belongs under `aireview/query`; existing endpoint contracts remain intact. Summary composes existing Java amounts with direction-specific counts. New source queries validate dates, range, direction and pagination at the service boundary, return stable date/row/id order, and retain uploadId/sourceLineId/sourceRowNumber provenance.

The AI controller belongs under `aireview/ai` and registers only four read queries. No repository, SQL string, arbitrary URL, upload or review-write operation is exposed as a model tool. Source notes and filenames are untrusted text. Citations must refer only to returned records. Missing rows never establish that no file was uploaded.
