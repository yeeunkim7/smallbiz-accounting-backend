# Read-only AI reconciliation review

Updated: 2026-11-01 (Asia/Seoul).

> Product direction superseded on 2026-11-01: see [FREE receivables MVP plan](RECEIVABLES_FREE_MVP_PLAN.md). This file preserves the historical AI work. AI is a future PRO asset; actual-model validation is not a FREE launch prerequisite. Current priority is tenant/authentication, exact receivables allocation, success-only usage, then shared Expo mobile. Account/tenant authentication and FREE/STANDARD AI denial are now implemented at HTTP, service and provider boundaries. PRO remains denied until tenant-owned AI tools exist; overall FREE receivables functionality is still under development.

## Objective and scope

Help a small-business bookkeeper investigate one day's expected versus actual cash movements using existing Java calculations, source rows and upload provenance. The target customer is a product hypothesis pending interviews, not measured demand.

Deliver a dedicated review screen linked from the existing app: choose date → inspect incoming/outgoing summary → explicitly request AI review → inspect facts, hypotheses, checks and source evidence. Preserve existing workflows. Do not introduce automatic matching, transaction edits or automatic review completion.

## Decisions

- Existing checkout starts at `580adf5d496b5c59093d3075548dd7897eed8c23`; `988a983` is not in local history or advertised remote refs and short-ref fetch failed. The user confirmed that it was uncommitted and requested the GitHub baseline instead; comparison is no longer a blocker.
- Reuse `ReconciliationQueryService.daily` for money and difference calculations. New amounts are serialized as decimal strings for exact browser formatting.
- Express source presence by direction independently of total comparison and matching (`NOT_EVALUATED`). A zero difference with no rows is not evidence of matched transactions.
- Related uploads are derived from matching source rows across the whole requested interval, not from a single page or upload timestamp.
- Model receives four read-only tools, scoped to the selected date. Validate tool arguments and citations server-side. No generic SQL/HTTP/write tool.
- AI execution is explicit. Missing credentials and upstream failures remain errors, never fabricated successful analyses. A deterministic test double does not count as real-model verification.
- Dedicated screen uses neutral light surfaces, restrained teal, clear number alignment and accessible text labels. Keep legacy app functions available; defer a marketing landing page until a tested acquisition need warrants it.

## Roles and ownership

| Work | Owner | Files |
| --- | --- | --- |
| Market research | market_research | docs/ai-review-research.md |
| Code analysis | code_analysis | docs/ai-review-code-analysis.md |
| Query APIs | query_development | aireview/query and matching tests |
| Model adapter/tool loop | ai_development | aireview/ai and matching tests |
| Product/UI + visual QA | product_design | ai-review.html/css/js, legacy nav link, design document |
| Integration, final checks | root | shared documentation, application integration |
| Independent review | separate reviewer | review findings after implementation |

## Verification status

- Environment snapshot contains JDK 21, PostgreSQL 17 and Gradle 9.7.1. Previous actual test result: 56 passed, 0 failed/skipped. Latest full suite on 2026-11-01: 88 passed, 0 failures/errors/skips (56 original + 8 query integration + 15 AI orchestration + 9 HTTP transport tests). Gradle tests suppress inherited AI_API_KEY; no paid API is called.
- Browser: on 2026-11-01, 12 Chromium product/regression scenarios passed with 0 page errors. A separate reviewer directly ran 3 additional fixture scenarios. Desktop/mobile/no-data/updated-summary screenshots use the nov01- prefix; old captures are preserved. All model POSTs were intercepted and actual model calls were 0.
- Actual model: not run; paid calls remain paused pending explicit cost approval. AI_API_KEY presence and the enforced api.openai.com allowlist are now confirmed, without printing values or authenticating. The code default is gpt-4.1-mini; the current environment selects gpt-6-luna, whose provider support/access/pricing remain unverified. Browser validation server explicitly disables AI authentication.
- Previously blocked official research domains are now allowed. Domestic, Excel and commercial research was refreshed from official responses on 2026-11-01; the core comparison now includes eight alternatives. Only fetched sources are treated as inspected; customer demand and acquisition performance remain hypotheses.
- No publication, public deployment or service purchase is authorized or performed.

## Completed local work and remaining gates

1. Core comparison completed for eight references: Ecount, Cashnote, Excel, Xero, QuickBooks, Wave, Actual Budget and Frappe Books. Official features/pricing/entry paths and public reports are documented; missing plan/region details are marked. Customer demand, willingness to pay and acquisition results remain unvalidated.
2. New query APIs and bounded four-tool model orchestration implemented. All 88 tests pass; existing APIs and DB isolation retained.
3. Independent final code/UI re-review completed on 2026-11-01. Earlier null/body-timeout fixes are preserved; summary synchronization and 409 day-reload flow were independently checked. No unresolved P1/P2 defects were found within this local scope; actual-model semantics and public-operation readiness remain outside that sign-off.
4. Rendered desktop/mobile UI and verified date/summary/source/evidence/error/retry/keyboard flows. AI success uses an explicit fixture and does not establish actual-model readiness.
5. Real model function-call/citation verification requires credentials and allowed network; report separately if unavailable.

## Cost decision

User policy recorded on 2026-10-31: at most 5 model API requests per review including retries; no unbounded loops/retries; automated tests default to mock responses and must not call paid APIs; API keys belong only in server-side secret settings. The 5-request limit is now implemented and boundary-tested, including retries and valid completion on the fifth response. Provider failures are not automatically retried.

No dollar amount, monthly spend cap, payment or subscription was configured. The code default model remains gpt-4.1-mini; environment overrides are preserved. A key is now present but is not cost approval, so actual paid model calls remain paused. Official current pricing and an expected per-review estimate must be presented before resuming. GitHub main is the accepted baseline.

## Artifacts

See ai-review-research.md, ai-review-design.md, ai-review-code-analysis.md, ai-review-independent-review.md and ai-review-running.md. Browser results and screenshots are under screenshots/. No commit, push, public deployment or publication was performed.

## Resume — 2026-11-01

Fresh current-environment UX and independent-review agents completed the interrupted code/UI work. Old agents were not reused across environment instances. Full backend suite passed (88 tests); Chromium passed 12 scenarios; independent review passed 3 direct UI scenarios and cross-checked the full suite. Domestic/commercial/Excel research updates are saved and reviewed; eight core alternatives are compared. bootJar packaging succeeded. The research agent hit a usage limit after writing the report, so root reviewed the saved report and cached official-source evidence rather than treating its error as completed demand validation. No actual model calls, deployment or publication are authorized by this resume instruction.

### Implementation decisions completed on resume

- The existing 5-request model cap is implemented and verified, including no sixth request and success on the fifth. Gradle tests clear inherited AI_API_KEY by default; real checks remain opt-in and paused.
- The UI now applies the returned Java summary before showing an AI result and refreshes originals/uploads. AI_DATA_CHANGED locks model retry until a fresh day query; reloading the day itself makes no model request.
- A changed date resets both review text and its color class. Mobile details retain transaction type; wording no longer promises server/cost cancellation when browser fetch is aborted.
- Supported CSV formats and transaction snapshot timing are explained. These improve first use but do not establish customer demand or eliminate format-conversion work.

### Final verification and remaining external work

- `nov01-full-tests.log`: 88 tests, 0 failures/errors/skips; `nov01-package.log`: bootJar successful. Browser 12 and independent UI 3 passed with no actual model calls.
- Research confirms competitors already offer AI and bank integration. Test the narrower value of finding daily expected-versus-actual evidence without migrating a ledger; measure CSV preparation effort as a potential reason users would not switch.
- The last separate development-DB row-count recheck was not executed: automatic escalation review could not complete because of a usage limit. This was a review failure, not an unsafe-action verdict. Package success was confirmed from the existing local log; no approval check was bypassed. No fresh DB row-count preservation claim is made for that unexecuted command.
- Actual provider authentication, selected model support/pricing, paid-call semantic evaluation, user interviews and public-operation security remain pending. No publication or purchase was performed.

## Receivables P0 baseline

Latest checkout retained the existing uncommitted work at HEAD580adf5. JDK/javac21.0.12.1, PostgreSQL17.11, dev/test databases and FlywayV1–V7 were verified. On2026-11-01 the existing88 tests were rerun: all passed, failures/errors/skips0, XML12. Development vendor/business/bank/upload counts were1/1/1/2 before and after (row-count evidence only). No new migration, financial-source edit, real model call, Expo project, commit/push or publication was performed. P0 planning files: RECEIVABLES_FREE_MVP_PLAN.md, receivables-domain-plan.md, receivables-market-plan.md and receivables-mobile-plan.md.

## Account foundation implementation

The previous work was committed as 29c7cc9 before new edits. Added standard Spring Security, BCrypt passwords, hashed opaque sessions with expiry/revocation, atomic FREE signup, tenant membership checks, default legacy denial and explicit anonymous legacy-dev fixture. V8 adds identity tables only and development financial counts remain 1/1/1/2. Existing AI entry/service/provider paths fail closed, including PRO until tenant tools exist. Public account operations still require rate limiting, recovery/deletion policy and deployment TLS. See AUTH_FOUNDATION.md and auth-security-review.md. No actual model call, push or deployment.

Final auth verification: 104 tests passed, 0 failures/errors/skips (16 XML reports); JAR packaging succeeded. Local HTTP default-denial smoke passed. V8 applied successfully; development financial counts remained unchanged. No actual model calls, push or public deployment.

## Receivables registration — feature 1

Added tenant-owned manual counterparties, invoices and payments with V9 only. Required idempotency keys, strict KRW decimal-string amounts, validated dates, membership checks, composite tenant FKs and stable paginated reads. No legacy financial rows reassigned; no allocation, balance, matching, quota or AI execution added. New14 integration tests plus existing104: full118 passed, failures/errors/skips0, XML17; build/JAR successful. Log: `/workspace/.local/run/registration-full-build.log`. Independent code review found no unresolved P1/P2. See RECEIVABLES_REGISTRATION.md and receivables-registration-review.md.

## Manual allocation — feature 2

Added V10 immutable batches, lines, cancellations, audit and idempotency commands. Parent lock ordering prevents double allocation; partial and bundled payments are supported without changing registered amounts. Current outstanding/unallocated/overdue amounts use exact arithmetic and string transport. asOf is an overdue cutoff, not a historical ledger snapshot. Correction is explicit cancellation followed by a new confirmation; no automatic matching, AI or usage charging. New17 + registration14 targeted tests passed, then full135 (XML18) passed with0 failures/errors/skips; build/JAR successful. Independent review found no unresolved P1/P2. Log `/workspace/.local/run/allocation-full-build.log`; details RECEIVABLES_ALLOCATION.md.

## Rule candidates — feature 3a

Added V11 tenant-owned immutable matching runs and result snapshots. Rules use exact remaining amount or normalized nonempty payer/counterparty name, expose reasons/mismatch/date warnings and tentative partial suggestions. One SQL snapshot supplies current sources; bounds reject oversized sources instead of truncating a successful run. Same idempotency key replays stored evidence, never reallocates money. No AI, quota, bank import or automatic confirmation is invoked. New14 tests plus prior135: full149 passed, failures/errors/skips0 (XML19); build/JAR successful. Independent final review found no unresolved actionable defect. Log `/workspace/.local/run/matching-full-build.log`. CSV import and server-side successful-run quota remain prerelease gates.

## Tenant bank CSV import — feature 3b

Added additive V12 and tenant-owned bank import/list/detail/paginated evidence APIs plus payment-to-original source lookup. Standard six-column CSV requires a nonsecret account alias and explicit stable bank transaction IDs; Woori fingerprints are excluded. Same account/file replays original results, cross-file overlaps retain each observation and reuse one payment, equal-value real transactions with distinct IDs survive, and changed stable identities reject the entire file. Strict UTF-8/BOM and shared Java manual-registration validation preserve original parsed cells without assigning legacy data. File defaults are 1MiB/1000 rows with 5MiB/10000 hard caps and actual bounded stream reads. Tenant serialization and one transaction cover source history, payments and required idempotency keys, including rollback after late conflicts/storage errors. Withdrawals are retained with explicit counts; no invoice auto-linking, AI/provider call, matching quota or mobile flow was added. API contract: RECEIVABLES_IMPORT.md. Verification status is recorded below after the targeted run; root owns the subsequent full build/HTTP smoke and final review.

Targeted verification on 2026-11-01: all 16 bank-import integration tests passed, failures/errors/skips 0, BUILD SUCCESSFUL (34s). Command: `source /workspace/.local/env.sh` followed by `bash gradlew --offline --no-daemon --project-cache-dir /workspace/.local/gradle-project test --tests '*BankImportIntegrationTest' --rerun-tasks` in backend-java. Log: `/workspace/.local/run/import-targeted.log`. Tests cover tenant/auth/direct-service revocation, exact amounts/dates and Unicode code-point limits, UTF-8/BOM/quoted LF/malformed CSV, bounded actual stream/1000-row boundary, file/idempotency/stable-ID concurrency, late failure rollback and retriable keys, source FKs, withdrawal-only data and evidence surviving allocation cancellation. Test model/query mocks had zero interactions. Full regression, package and real HTTP smoke are separate root verification below; no paid provider, mobile or public deployment check was performed.

Final feature3b verification: full165 tests passed (149prior+16new),0 failures/errors/skips, XML20; `build --rerun-tasks` succeeded in1m19s and produced the JAR. Actual local HTTP9 scenarios passed with a synthetic test tenant: authentication denial, malformed multipart safe400/no sentinel in app log, distinct equal-value incoming payments plus withdrawal, key/file replays, source lookup, invalid-file atomic rejection, candidate integration and bundled allocation leaving20,000 outstanding on100,000. Synthetic test rows were cleaned. Development V12 applied successfully, health200, legacy original row counts remained1/1/1/2. Shared MultipartException handling now avoids logging client-controlled parser exception text. Initial targeted test failure was an invalid expectedVersion field in the new cancellation test; the existing reason-only API was preserved and corrected tests passed. Full evidence and commands: RECEIVABLES_IMPORT_SMOKE.md; final independent source/artifact review: receivables-import-review.md. No actual model calls, mobile/browser check, public deployment or publication; monthly successful-run quota remains next.


## Monthly successful matching usage — feature 3c

Added V13 only, current-month tenant-authenticated GET usage API and server-configurable FREE10 hypothesis with STANDARD/PRO0 fail-closed defaults. Matching retains one bounded synchronous READ COMMITTED transaction: run/key first, monthly upsert/row lock, PostgreSQL time and fresh plan/access check, source SQL snapshot, SUCCESS result/month plus unique per-run event and counter commit together. All failure paths roll back provisional rows and key. Before-feature and cross-month same-key results replay without additional charge; conflicting payload still409 before quota; exhausted quota returns429 with safe usage/reset and Retry-After. A month transition while waiting returns safe409 and permits same-key retry. Empty normal SUCCESS counts once; import, allocation/cancellation and reads remain0. Previous async reservation/lease proposal is explicitly superseded for this synchronous bounded implementation. No prices, paid entitlement, mobile or provider execution were added. API/design/plan/README references now reflect the feature.

Targeted verification on 2026-11-01: new15 UsageIntegrationTest + existing14 MatchingIntegrationTest passed,0 failures/errors/skips, BUILD SUCCESSFUL in25s. Actual PostgreSQL tests cover default10/11, last-slot and new-month first-row races, same-key concurrency, Seoul DB clock/time seam, lock-wait month change, frozen approval month and historical replay, validation/source-bound/source-integrity failures, snapshot/event/counter rollback, live plan downgrade, direct/HTTP tenant and session revocation, DB counters/event uniqueness/composite tenant FK, and real CSV import plus allocation/cancellation after quota denial. Mock model/query interactions remain0; automated key guard retained. Log: `/workspace/.local/run/usage-targeted.log`. The final Retry-After ceiling correction is covered by the full regression below. That targeted invocation also selected3 existing read-only query tests (32 total); all passed.

Final feature3c verification: full180 tests (165prior+15new) passed,0 failures/errors/skips, XML21; `build --rerun-tasks` succeeded in52s and produced the JAR. Real HTTP11 scenarios passed with a test-only FREE limit2, including safe429/reset/Retry-After agreement, no additional charge for replay or failures, post-limit CSV import and allocation/cancellation/source reads, and tenant isolation. Synthetic test tenants were cleaned; development V13 applied, health200, legacy row counts stayed1/1/1/2. Source and execution artifacts independently reviewed in receivables-usage-review.md; commands/evidence in RECEIVABLES_USAGE_SMOKE.md. No model calls are on these paths; actual paid-model, mobile/browser, public deployment and store validation were not performed. User preference for Korean Git commit messages is recorded in AGENTS.md. Next feature is the shared Expo mobile core flow; operational account/retention controls remain release gates.

## Expo mobile core — feature 4a

Added mobile/ official Expo58/RN0.88/React19.3/TypeScript6 shared app: auth, home server balances, manual counterparties/invoices/payments, paginated ledgers, current invoice balance detail, server usage/account/logout, and isolated readonly synthetic sample. Web bearer is memory-only; native SecureStore implementation remains device-unverified. Financial totals stay Java-authoritative; client uses decimal strings/BigInt for formatting. No AI path or client plan entitlement. Explicit origin-only CORS defaults to denying browser origins and retains bearer/tenant enforcement; no financial logic or migration changed.

Verification: backend186 tests (180prior+CORS6),0 failures/errors/skips, XML22; full build54s and JAR successful. Client8 tests pass; TypeScript and offline Expo web export pass. Online RN Directory check blocked by policy; local Expo compatibility passes. Actual Chromium9 final scenarios pass,0 pageerrors: sample0API and320/390/1280 layouts, actual signup/counterparty/invoice/balance, actual committed payment with503-response fixture then canonical whitespace retry using samekey/onepayment, unallocated40000 does not reduce outstanding100000, serverusage0, no browserstorage, reload requireslogin, actual server sessionrevocation clearsworkspace. Synthetic test tenant cleaned. Independent320 fixtures preserve large sums/singleinvoice1trillion and expiry/longnames; fixtures do not establish financial calculation. Browser-discovered fetch receiver/key/nav-race/amount-wrap defects fixed and rerun. Logs mobile-{full-build,unit-final,types-final,export-final,browser-final}.log; original dev counts1/1/1/2 unchanged. Repro script scripts/verify-receivables-mobile-browser.py and docs/RECEIVABLES_MOBILE_VALIDATION.md contain commands/screenshots; independent review receivables-mobile-core-review.md. No paid calls, public deployment or store submission. Next: mobile matching review/explicit confirmation+cancellation; CSV, true pagination/multitenant rendered scenarios, native/Windows/security/retention remain gates.

## Mobile matching review/confirm/cancel — feature4b

Fresh environment checkout24af19b contained only auth, so fetched preserved remote40948fe rather than reconstructing previous features and created feat/mobile-matching-review. Added mobile matching tab with explicit charged-run generation/free saved GETs, facts/warnings and snapshot distinction, manual partial/bundled/split drafts, current server-balance preview then explicit confirm, immutable command replay vs current GET, required-reason whole-batch cancellation/audit, source lookup only on user request. No Java financial logic or new migration; source amounts and authority remain server-side. Keys/drafts survive tabs within tenant; writes lock navigation; source labels avoid proof of missing files. History metadata only; detail initially10rows+loadmore. Reuse platform adapters; no Apps in Toss SDK conversion yet. Portfolio priority and later platform plan recorded in PORTFOLIO_DELIVERY_PLAN.md/AGENTS.

Validation: backend186/0failure-error-skip/XML22/build1m3s; client13/13, tsc and webexport passed. Actual Chromium9matching+9core regression passed,0pageerrors; synthetic tenants cleaned. Real partial40k reduces150k outstanding to110k; bundle40k+60k to50k; split150k across100k/50k to0; each reasoned cancellation restores150k. Actual committed confirmation with503-response fixture retains same key across tabs and onebatch. Quota10/429 and free GETs verified; audits original lines preserved. Independent11fixtures cover race/navigation/uncertaincancel/currentv2-vs-replayv1/refreshfailure/historybounds; documented separately. Root finalscreenshots directly viewed. DevexistingV13 restored, original counts1/1/1/2 before/after (count evidence only). Runtime test server has empty model key and AI disabled; no model paths in client. No paidcalls/publication/native/store/video checks. See RECEIVABLES_MOBILE_MATCHING.md and receivables-mobile-matching-review.md for reproducible commands/screenshots/limits. Next portfolio demonstration materials/video preparation; native and Apps in Toss eligibility/SDK verification remain pending.
