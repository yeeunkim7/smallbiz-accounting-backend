# Tenant bank CSV import — feature 3b

Authenticated members can import a bank statement into their tenant's payments. Incoming transactions create unallocated `BANK_CSV` payments visible through the existing payment, balance, allocation and matching APIs. No invoice is selected or allocated automatically. A withdrawal-only import is recorded source data with zero new payments; it does not establish missing incoming payments. Legacy global upload/bank tables and V1–V11 are unchanged, and no legacy rows are assigned to tenants. V12 adds the new history, identities, source evidence and payment provenance.

## API

All paths start with `/api/v1/tenants/{tenantId}` and require `Authorization: Bearer <session>`. Every service entry checks the active session and tenant membership, including direct calls. Responses use `Cache-Control: no-store`.

| Method and path | Contract |
| --- | --- |
| `POST /bank-imports` | `multipart/form-data`: required `file` part, required `bankAccountKey` text field, required `Idempotency-Key` header. Returns 201 with the saved import, including replays. |
| `GET /bank-imports?page=0&size=20` | Imports ordered by `createdAt DESC,id DESC`; page object with `items,page,size,hasNext`. |
| `GET /bank-imports/{id}` | Saved import metadata and counts. |
| `GET /bank-imports/{id}/rows?page=0&size=20` | Source records in CSV record order, including withdrawals and overlapping duplicates. |
| `GET /payments/{id}/source` | Original source record for an imported payment; 404 for manual, unknown or foreign payment IDs. |

Pagination uses zero-based pages and sizes 1–100; row offsets use checked input and long arithmetic. An inaccessible tenant returns 403; unknown IDs within the caller's tenant return the same generic 404 as foreign record IDs.

Import fields: `id,tenantId,bankAccountKey,fileSha256,filename,byteCount,rowCount,importedCount,duplicateCount,withdrawalCount,createdBy,createdAt`. `importedCount` counts newly created incoming payments; `duplicateCount` counts incoming records already imported for that account; `withdrawalCount` counts all outgoing records, including overlaps. Their sum equals `rowCount`. A duplicate file returns its original counts and metadata, rather than creating a new upload event.

Source fields: `id,importId,recordNumber,outcome,originalCells,transactionId,bankAccountKey,sourceLineId,bookedDate,direction,amount,payerName,note,paymentId,firstSourceRowId`. `outcome` is `IMPORTED`, `DUPLICATE`, or `WITHDRAWAL`. `amount` is a decimal string. `paymentId` is null for withdrawals. `firstSourceRowId` points to the first observation of the stable transaction; each later overlapping file has its own source row and original cells but links to the same transaction/payment.

## Supported CSV and identity

Only the existing standard six-column format is accepted, with this exact header and order:

```csv
booked_date,direction,amount,counterparty_name,description,source_line_id
2026-11-01,IN,100000,Kim,invoice reference,bank-txn-000001
2026-11-01,IN,100000,Kim,second real payment,bank-txn-000002
2026-11-01,OUT,3000,Bank,fee,bank-txn-000003
```

[Synthetic sample](../backend-java/docs/examples/receivables-bank-valid.csv) · [Local HTTP smoke procedure](RECEIVABLES_IMPORT_SMOKE.md).

`bankAccountKey` is a nonsecret, immutable, user-selected alias for one source bank account, unique by convention within the tenant. It is **not** verification of a bank account or bank ownership. Use the same alias for every statement from that account and different aliases for different accounts. Keys and `source_line_id` use `[A-Za-z0-9._:-]{1,100}` and are case-sensitive. Idempotency keys use the same syntax.

`source_line_id` must be the bank/exporter's stable transaction identifier for that account, invariant across overlapping exports. A row number, date/amount/name combination, statement-local sequence or generated fingerprint is not a safe substitute. The server cannot verify an exporter's claimed identity. Separate real transactions with equal date, amount, payer and description must retain distinct IDs; both become payments. Different files reusing an ID with a different normalized date, direction, amount, payer or note receive 409 and the entire import rolls back. Outgoing identities are checked too. The existing Woori seven-column format derives a fingerprint rather than a stable bank transaction ID and is unsupported here; the legacy `W1.<64 lowercase hex>` fingerprint namespace is rejected. Legacy upload support remains available under its existing development boundary.

The importer strictly decodes UTF-8, accepting an optional leading UTF-8 BOM. It reuses the standard six-column contract and Apache Commons CSV RFC4180 library from `BankCsvParser`, and the existing `ReceivablesValidation` Java amount/date/text rules. The legacy parser itself uses permissive UTF-8 decoding, date-year and UTF-16 text rules unsuitable for the new contract, so it is unchanged and is not called by the tenant importer. Embedded LF/tab in text is allowed; carriage returns inside text cells and other control characters are rejected. CRLF or LF record separators are accepted. Empty files, header-only files, blank records, duplicate IDs within a file, inconsistent columns, extra headers and malformed encoding/CSV are rejected atomically.

Amounts are exact positive integer KRW strings from `1` through `1000000000000`: no whitespace, grouping commas, fractions, signs, exponents or leading zeroes. Dates must be real exact `YYYY-MM-DD` dates with years 0001–9999. `direction` is exactly `IN` or `OUT`. Payer and note are stripped using the manual registration validator; payer length is at most100 Unicode code points, note at most1000, including astral Unicode characters. IDs, dates, direction and amount have the stricter exact syntax above. Original cells retain their exact parsed strings, including whitespace and quoted LF, independently of normalized payment fields; total original evidence is bounded by the configured file-byte limit without truncation.

## Bounds, atomicity and evidence

Defaults are **1MiB and1000 data records per file**. Configure `app.receivables.bank-import.max-bytes` and `.max-rows`; startup rejects values outside1–5242880 bytes or1–10000 rows. The importer reads at most configured bytes plus1 from the actual stream, even if multipart metadata lies. Existing transport limits remain6MB per part/7MB per request to preserve legacy upload contracts; the tenant service enforces the smaller import limit. Filenames use their basename, default to `bank.csv`, and reject control characters or lengths over255 UTF-16 units. Filenames are display metadata, excluded from request meaning, so a safe rename can replay the same import.

The raw-byte SHA-256 is stored, but the raw file is not downloadable or retained as a byte-for-byte backup. `originalCells` is the ordered array of the six original parsed values; it preserves cell content, not CSV quote syntax, delimiter spelling, BOM or record line endings. `recordNumber` counts CSV logical records including the header as1; it is not a physical line number when a cell spans lines. Header names are fixed by this version's contract. Treat original cells and text fields as untrusted data: render as text, and escape/neutralize appropriately if a future spreadsheet export is added. No source content is included in error messages or logs by this importer.

Validation completes before writes. A bounded synchronous transaction locks the tenant row to serialize imports, rechecks session/membership, and commits the import, all source observations, stable identities, payments and idempotency record together. Tenant composite FKs connect imports, source rows, transactions and payment provenance; `MANUAL` payments require null bank source and `BANK_CSV` requires a source. Allocation confirmation/cancellation never overwrites payment originals or source evidence. Any late identity/storage failure rolls back the whole import and its key, permitting a corrected retry. Tenant serialization is a deliberate bounded MVP throughput constraint; this endpoint has no background worker.

Identity rules:

- Same `(tenant,Idempotency-Key)` and same `(bankAccountKey,raw file SHA-256)` replays the original result. Different request content for an existing key returns `IDEMPOTENCY_CONFLICT`409.
- Same `(tenant,bankAccountKey,raw file SHA-256)` under a new key replays the saved import and reserves the new key atomically. Byte-identical files under two different account aliases are distinct imports.
- Different files deduplicate only `(tenant,bankAccountKey,source_line_id)`, checking normalized content for conflicts; all per-file observations survive. Raw whitespace changes that normalize identically can overlap safely. Changing CSV bytes/BOM/line endings changes the file hash and requires a new idempotency key, while stable transaction identity still prevents duplicate payments.

Errors contain safe `code,message` and, where available, `recordNumber,field`. `BANK_IMPORT_INVALID`400 covers encoding/format/input, `BANK_IMPORT_LIMIT`413 covers configured bytes/rows, `BANK_TRANSACTION_CONFLICT`409 covers changed stable identities, `IDEMPOTENCY_CONFLICT`409 covers changed requests, `BANK_IMPORT_NOT_FOUND`404 covers unavailable imports/sources and `BANK_IMPORT_STORAGE_UNAVAILABLE`503 covers storage errors without SQL details. Pagination validation uses existing `INVALID_INPUT`400. Authentication and multipart transport errors use the existing server boundary. No partial import or failed attempt is recorded as a successful import.

Import does not call AI/provider code, charge monthly matching usage, allocate money, verify bank identity, reinterpret zero incoming as missing payments, create a mobile flow or make the FREE service release-ready. Matching's separate existing safety cap of200 eligible payments/200 invoices still applies: importing1000 records successfully does not guarantee a matching run across that entire date range. Narrow the matching range; oversized candidate runs fail without silent truncation. Server successful-run quota, mobile, retention/deletion/storage budgets and operational controls remain separate work.
