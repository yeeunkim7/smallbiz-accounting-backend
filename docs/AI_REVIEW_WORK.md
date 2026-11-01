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
