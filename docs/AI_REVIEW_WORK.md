# 개발 작업 기록

## 현재 상태와 기록 읽는 법

2026-11-04 문서 갱신 기준. 기존 입출금 대사에서 FREE 미수금 기능으로 확장했다. V8~V13 인증·등록·배분·후보·CSV·사용량과 Expo 등록/확정/취소 흐름, 포트폴리오·영상이 추가됐다. 현재 API·범위는 [개발 계획](RECEIVABLES_FREE_MVP_PLAN.md), 판단 주체는 [ADR](adr/0002-receivables-decisions.md)에 있다.

2026-11-01 최종 기능 검증 기록은 backend 186, client 13, 실제 브라우저 신규 9+기존 9다. 각 단계의 56/88/104 등 수치는 당시 실행 기록이며 이번 문서 수정에서 재실행하지 않았다. FREE/STANDARD는 모델을 호출할 수 없고 PRO도 tenant 전용 도구 미비로 차단한다. 제품 PRO 실제 모델·실기기·공개 서비스 검증은 미완료다. 별도 로컬 Hermes 실습에서는 사용자 PC의 실제 도구 호출과 Java API 연결을 확인했다. 개인 PC에서도 백엔드186개 통과를 확인했으며 상세 근거는 하단 2026-11-04 기록을 따른다. Docker 설치·배포는 보류했다.

아래 원문은 초기 AI 검토 개발부터 이어진 이력이다. 과거의 “현재”, “다음”, 미구현 상태는 당시 시점이며 현재 계획을 뜻하지 않는다. 기존 영어 기록과 날짜·수치·오류 근거를 보존한다. 최초 상단의 갱신일은 2026-11-01 (Asia/Seoul)이었고, 방향 전환 시점의 “전체 FREE 기능 개발 중” 설명은 이 현재 요약으로 대체한다.

## 보존된 단계별 작업 이력

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

## Portfolio materials and recorded demonstration

Functional baseline remains54fa68d; no application logic or schema change. Reorganized README as portfolio entry while preserving previous README in LEGACY_RECONCILIATION.md with corrected relative links. Added PORTFOLIO.md, architecture/ERD grounded in V8–V13, and a2-pageA4 overview PDF with editableHTML/reproduction script. Root corrected the diagram to show onePostgreSQL and independent legacyUI (Expo nevercallslegacy), and matching resultwrites distinct from read-onlyfinancialsource access. Existing backend186/client13/actualbrowser9+9/fixture11 counts remain attributed to their recorded functional runs, not claimed fresh today.

Recorded10 actual Chromium scenes using realJavaAPI+localreconciliation_test and synthetictenant. FinalMP4:106s,1280x960,H264yuv420p,25fps,1459145bytes, noaudio,faststart; Koreaneditorialcaptions,SRT,poster,andmanifestprovided. RealUI registered100000invoice and40000payment, generatedonecandidate run, confirmed40000 then60000, cancelledsecondbatch. Serverassertions outstanding100000→60000→0→60000; usage1; cancellationaudit2events; no pageerrors; synthetictenantremoved. Account/tenant/counterparty/second60000payment seededoffcamera explicitlydisclosed; loginframesexcluded. Nofinancialresponsefixtures/modelcalls/nativeTossclaim. Rawclips outsidegit; finalonlymedia andsafe metadata committed. Entirevideo decode and selectedframes reviewed independently byroot; PDF2pagesrenderedandvisuallychecked. Repro scripts record-portfolio-demo.py/render-portfolio-pdf.py; linksanddiffcheckpassed. Applicationtestssuitenotrerunforthisdocumentation-onlychange. Native/Appsintoss/publicoperation/security/marketvalidation remaingates.

## AI-agent development disclosure and portfolio integration advice

UserrequestedexplicitAI-agentdevelopmentdescription. Updated README,PORTFOLIO.md,demoREADME and2-pageoverviewHTML/PDF to state actual roles: userproblem/requirements/constraints/priorities; AIorchestration/analysis/implementation/independentreview/test/assetcreation. Runtime FREE modelcalls remain0; do notconflate developmenttoolswithAIproductcapabilityorclaimunverifiedmanualcoding/productivitypercentages. Reviewed prioruploadedportfolio privately andproduced integrationoutline outside repository; originalslides,portrait,contactsandcompanymaterials notaddedtorepo. Rootvisuallychecked updatedPDF2pagesanddiffcheck. Noapplicationlogicorvideochanged; nofunctionaltestsrerunforthisdocumentationupdate.

포트폴리오 문서·PDF와 시연 영상의 반복 안내 및 편집 주석을 정리했다. 기존 화면·길이는 유지하고 제목과 결과 설명을 줄였다.

발표용 PPT5장과 PDF를 추가했다. README 상단에 AI 에이전트 활용을 명시하고, 소개·설계·검증 문서의 중복 설명과 포트폴리오 계획을 기준 문서 링크로 정리했다.

AI 활용 설명을 실제 사용자 지시(업무 규칙·역할 분담·모델/동시 실행 제한·실행 검증 요청)와 에이전트 수행 내용으로 구체화했다. README와 PORTFOLIO.md, 발표 PPT 4장을 갱신하고 PDF를 다시 내보냈다. 사용자 판단과 에이전트의 구현·결함 발견·검증 실행을 구분했다. 5장 구성과 렌더링을 확인했으며 기능 코드는 변경하지 않아 애플리케이션 테스트는 재실행하지 않았다.

사용자 피드백에 따라 AI 활용 설명의 중심을 Codex 선택 이유, 총괄과 역할별 하위 에이전트의 책임·산출물, 조사/분석부터 구현·독립 검토·수정·재검증까지의 진행 방식으로 재작성했다. 모델 종류·사용량 중심 설명을 걷어내고 기존 대사 시스템 보존과 미수금 확장, 실제 결함 재현 사례 및 근거 문서를 연결했다. README·PORTFOLIO.md·PPT 4장/PDF에 반영했다. 문서 링크·5장 구성·PDF 렌더링 확인 완료. 기능 변경과 애플리케이션 테스트 재실행은 없다.

## 2026-11-02 문서·개발 절차 정리

README는 서비스·기술 중심, PORTFOLIO는 문제·판단 주체·코드·검증 근거 중심으로 재구성했다. AGENTS에 주요 변경 승인과 구현 후 개발자 검토 절차를 추가했다. 기존 AI 설계 선택에 사용자 승인을 소급하지 않았다. 현재 구현과 과거 계획을 구분하고 기존 테스트·날짜·결함 기록을 보존했다. 범위는 Markdown이며 기능·DB·테스트 데이터·PPT·영상은 변경하지 않았다. 문서 점검 결과는 DOCUMENTATION_REVIEW.md를 참조한다.

## 2026-11-03 발표 자료 갱신과 학습 준비

사용자 요청으로 전날 문서 정리를 한국어 메시지로 커밋했다. PPT5장/PDF는 Codex 선택·역할 구성·실제 구조/기능·검증 사례·배운 점 순서로 재구성했다. 직접 수행한 근거가 있는 목표·규칙·역할 구성·우선순위는 개발자 기여로 명시하고 에이전트 구현을 수동 구현으로 바꾸지 않았다. 핵심 코드·질문·답이 있는 PORTFOLIO_LEARNING.md와 발표자 노트를 추가했으며 사용자 학습 완료는 미확인이다. 기존2026-11-01 테스트 기록을 인용했고 기능·DB 변경이나 테스트 재실행은 없다. 최종 PPT/PDF5장 렌더링·발표자 노트·도형 경계와 문서 링크를 확인했다. 이번 커밋·푸시는 사용자의 명시적 요청에 따라 진행한다.

## 2026-11-03 멱등성 학습 테스트 보강

사용자가 선택한 40/60 기대값으로 기존 AllocationIntegrationTest의 내용 불일치409 직후 allocatedAmount/outstandingAmount 보존 검사를 추가했다. 기능 코드는 변경하지 않았다. 실행: backend-java에서 `bash gradlew --offline --no-daemon --project-cache-dir /workspace/.local/gradle-project test --tests '*AllocationIntegrationTest.idempotentConfirmationNormalizesOrderAndPreservesOriginalSnapshotAfterCancellation'`. TEST_DB_URL이 DB_URL과 다르고 reconciliation_test임을 확인했다. 최초 Gradle wildcard IP 제한, 다음 DB 연결 실패를 확인했으며 권한 조정과 기존 PostgreSQL17 재시작 후 최종1개 통과/실패0/오류0/스킵0(17s). Gradle의 AI_API_KEY 비움 가드를 유지했다. 새 기능·마이그레이션 파일·유료 호출은 없고 전체 회귀는 재실행하지 않았다. 사용자 기대값 판단과 에이전트 편집·실행을 PORTFOLIO_LEARNING.md에 구분해 기록했다.

2026-11-03 같은 학습 테스트에 사용자가 작성한 paymentBalance/unallocatedAmount=60 assertion을 추가했다. 청구·입금 양쪽의 충돌 후 잔액 보존을 확인한다. 같은 단일 테스트 명령으로 reconciliation_test에서1개 통과/실패0/오류0/스킵0(17s). 에이전트가 파일 반영·실행했고 사용자의 코드 작성 범위와 이해 확인은 PORTFOLIO_LEARNING.md에 기록했다. 기능 코드·전체 회귀 변경은 없다.

## 2026-11-03 사용자 PC의 Hermes 합성 조회 실습

사용자 제공 로그·화면으로 Hermes v0.22.6 설치, Codex 인증, 포그라운드 Gateway, 플러그인1도구 등록과 실제 모델 호출을 확인했다. 제공한 합성 조회 예제로 정상 DATA·NO_DATA 답변을 확인하고, 사용자가 안내에 따라 counterparty_name 필드를 추가한 뒤 직접 함수 실행·새 모델 세션에서 샘플상점 응답을 검증했다. 안내 오류였던 --list-tools는 지원하지 않아 제거했다. 원시 인증/모델 로그나 사용자 PC에 직접 접근한 검증은 아니며, 차곡 Java API·PRO 실제 모델 검증과 구분한다. 학습 근거·기여 범위·후속 작업은 PORTFOLIO_LEARNING.md에 남겼다. 이번 반영은 문서만이며 신규 모델 호출·금융 기능 변경·테스트 재실행·커밋/푸시는 수행하지 않았다.

## 2026-11-04 사용자 PC 백엔드 환경 검증

사용자가 WSL에서 JDK21·PG17.11을 준비하고 개발/테스트 DB와 역할·교차 CONNECT 차단을 확인했다. /mnt/c 빌드 보고서 권한 오류 뒤 Windows 원본을 보존하고 Ubuntu홈 별도 복제에서 서버를 시작했다. 공유 로그에 개발 DB V1~V13 적용·8080 시작 성공이 있다. 테스트 명령 `bash gradlew test --rerun-tasks --console=plain` 실행 후 사용자 XML 집계186/0failure/0error/0skip 확인. 전용 reconciliation_test를 사용했고 실제 모델 호출은 기존 자동 테스트 가드로 차단한다. 본 기록은 사용자 실행 근거이며 총괄의 신규 전체 테스트 실행이 아니다. 다음은 로컬 합성 데이터 대상 기존 인증된 Java GET을 Hermes 도구로 연결하는 실습이며 FREE 앱의 AI 기능 개방이나 권한 우회가 아니다.

## 2026-11-04 로컬 Java 조회 플러그인 준비

사용자 합성 계정의 Java summary 응답에서 청구100000/배분40000/미수60000/미배분0을 확인했다. 예제 examples/hermes/chagok-local-api는 고정 localhost GET, 비공개 세션 파일 tenant, redirect/proxy 차단, 응답64KiB 및 필드 검증을 사용하고 asOf를 현재원장 연체판정일로 명시한다. 오프라인 단위10개 통과·별도 코드 검토에서 차단 결함 없음. 개인PC의 새 플러그인 등록·실제API·모델호출은 아직 미검증이며 기존 고정 합성도구 검증과 구분한다. 서버기능·권한·DB마이그레이션·유료호출 변경 없고 커밋/푸시하지 않았다. 설치ZIP은 workspace artifacts에 소스4파일만 묶고 인증파일·개인원본은 포함하지 않았다.

## 2026-11-04 실제 조회 실습 완료와 발표 자료 갱신

사용자가 chagok-local-api lookup 직접 실행으로 DATA·현재 원장·미수60,000원·Java API 경로를 공유했다. 이어 Hermes 실제 조회 도구 실행 표시가 있었음을 확인하고 서버 값·건수·조회 범위를 설명한 답변을 공유했다. 잘못된 날짜 검사와 모의 401/연결 실패 검사도 각각 통과했다. 모의 오류를 실제 인증 만료·서버 중단 테스트로 기록하지 않는다. 사용자 설치·실행과 에이전트 예제 제공을 구분해 학습 기록·플러그인 README·아키텍처에 저장했다.

발표 PPT5장/PDF를 프로젝트 문제 → Codex 선택과 역할 구성 → 실행 구조·기술 스택 → 직접 실행한 결과 → 학습·기여로 갱신했다. 각 장에 발표자 노트가 있다. 기존 실제 API 브라우저 녹화를97초로 재편집했으며 Hermes 촬영이나 이번 금융 통합 재검증으로 표현하지 않는다. README·PORTFOLIO·학습 기록을 최신 상태로 연결했다. 기존2쪽 소개서는 이전 요약본으로 유지한다. Docker는 사용자 요청으로 보류했다.

검증: 클라우드 플러그인 오프라인10개 통과(모델 호출 없음), PPT/PDF5장 렌더·대표 화면 시각 확인, 독립 검토에서 차단·중요 결함 없음. 영상 H.264/1280×960/25fps/97초/2,425프레임·전체 디코딩·faststart 확인. 날짜별 이전 테스트 기록을 합산하지 않았으며 이번에 백엔드 전체를 재실행하지 않았다. 제품 기능·DB·외부 서비스는 변경하지 않았고 커밋·푸시·공개 배포는 수행하지 않았다.

사용자의 후속 요청으로 위 변경과 앞서 검증한 학습용 잔액 assertion·조회 플러그인을 함께 한국어 메시지로 커밋하고 main에 푸시한다. 인증 파일·빌드 캐시·개인 원본은 포함하지 않는다.

## 2026-11-05 발표 구성 재정리

사용자가 장수 제한보다 중복 제거를 요청해 발표 자료를5장→9장으로 재구성했다. 제품 문제·도메인·실행 구조·확정 트랜잭션·Codex 협업·결함 수정·Hermes 실습·검증 근거·학습 사례를 각 장에 배치했다. 역할 설명과 개인 수행 범위, 검증 수치를 여러 장에 반복하지 않고 기존 실행 근거는 발표자 노트에 연결했다. 독립 검토에서 지적한 도메인 카드 넘침과 축약 테이블명 표기를 수정했다.

PPT/PDF9장 렌더와 시각 검사, 발표자 노트·도형/PDF 텍스트 경계·문서 링크·diff 검사를 완료했다. 기존97초 영상과 제품 코드·DB는 변경하지 않았고 새 금융 테스트·모델 호출도 수행하지 않았다. 산출물과 재생성 스크립트·현재 안내 문서를 갱신했다.

## 2026-11-05 사용자 발표 순서·핵심5가지·시연 이름 반영

발표를 학습 동기 → Codex 선택 이유 → 에이전트 구성·제품 아키텍처 → 개발5가지 → 검토 사례·Hermes 실습 → 성과·학습의11장으로 구성했다. 기존 사업자용 대사 시스템을 AI 에이전트 개발 학습에 활용했다는 사용자 설명을 반영했다. Claude Code 등과의 성능 비교나 독점 기능을 주장하지 않고 기존 연결 환경과 역할 분담 활용을 선택 근거로 썼다. 제공된 포트폴리오 예시의 파란 제목·구획·이미지 카드를 참고했다.

핵심5가지(계정·사업체 권한/청구·입금 등록/규칙 후보/배분·미수/취소·감사)에 실제 화면을 넣었다. 영상은 새 실제JavaAPI+Expo 브라우저 녹화106초로 갱신했다. 차곡 공방·A 거래처·11월 청구·연결 내역 재확인으로 이름을 바꾸고 반복된 합성 문구·회색 안내를 제거했다. 읽기전용 샘플의 의미는 유지하고 시연 데이터 설명은 영상 README에 남겼다. UI표시4파일 외 금융로직·DB구조를 바꾸지 않았다. 과거 실습·검증 기록의 데이터 성격은 소급해 바꾸지 않는다.

재녹화의 최초 실행은 Playwright ffmpeg 경로 누락, 다음은 CORS403으로 로그인 실패였다. 기존ffmpeg 경로연결과 정확한 localhost origin 허용 후 preflight200·로그인 성공을 확인하고 녹화했다. 격리된 reconciliation_test만 사용, 자신이 생성한 계정/tenant만 정리했다. 미수100000→60000→0→60000, 후보생성1회/자동배분0, 취소감사2건·사용량1회유지·브라우저오류0을 확인했다. 모바일13개와 typecheck통과, 영상전체decode·2650프레임·106초 및 대표화면확인. PPT/PDF11장 렌더·범위·독립문서검토 완료. 기존백엔드186개는11/04 사용자PC기록이며 이번전체재실행결과가 아니다. 유료모델호출·Docker·공개배포 없음.


## 2026-11-10 테스트 DB 격리 가드 검수 1단계

사용자의 다음 단계 진행 지시로 테스트용 가드만 보강했다. 정확한 reconciliation_test 이름과 명시적 PostgreSQL URL을 접속 전에 검사하고, 지원 옵션을 제한했다. Spring 실제 datasource가 환경 변수와 다른 경우 및 별도 Flyway·Hikari·JNDI·DataSource 설정을 거절한다. 독립 검토에서 발견한 Hikari DataSource class/중첩 databaseName 우회 경로를 수정하고 재검토했다. 제품 소스·마이그레이션은 변경하지 않았다.

가드 단위 테스트 10개 통과(실패·오류·스킵 0). 명령: JAVA_HOME=/workspace/.local/jdk/usr/lib/jvm/java-21-openjdk-amd64 GRADLE_USER_HOME=/workspace/.gradle bash gradlew --offline --no-daemon --project-cache-dir /workspace/.local/gradle-project -Dorg.gradle.java.installations.paths=/workspace/.local/jdk/usr/lib/jvm/java-21-openjdk-amd64 test --tests com.smallbiz.reconciliation.vendor.TestDatabaseIsolationTest. 최초 캐시 권한·샌드박스 인터페이스 제한·시스템 JRE의 javac 누락으로 실행 전 중단됐고, 기존 작업 공간 JDK21을 확인·지정한 뒤 성공했다. 새 설치·실제 DB 연결·Flyway 실행·DB 역할 권한 확인·전체 회귀·유료 API 호출은 하지 않았다. 사용자 코드 검토와 실제 테스트 역할의 권한 확인은 남아 있으며, 다음 업무 검수는 아직 시작하지 않았다. 커밋·푸시는 하지 않았다.


## 2026-11-10 배분 검수 전 실제 DB 격리 확인

사용자의 다음 작업 진행 요청으로 기존 PostgreSQL17을 시작했다. 최초 실행은 복원 환경의 /var/run/postgresql 소켓 경로 부재로 실패했으며, 설정 파일을 바꾸지 않고 기존 /workspace/.local/run 경로를 기동 옵션으로 지정해 시작했다. reconciliation_test에 default_transaction_read_only=on으로 접속해 권한 메타데이터만 조회했다. 개발·테스트 접속 계정은 모두 agent이며, agent는 SUPERUSER·CREATEDB·CREATEROLE이고 두 DB를 소유하며 개발 DB CONNECT 권한도 있다. chagok_test/chagok_dev 역할은 존재하지 않았다. 이는 사용자 PC의 별도 계정 분리 기록과 다른 클라우드의 실제 상태다.

DB 권한 격리 미충족으로 배분 통합 테스트·Flyway·테스트 데이터 생성/삭제는 실행하지 않았다. 역할·소유권·접근 권한·접속 설정은 변경하지 않았다. 클라우드 테스트 전용 비관리자 역할 및 개발 DB 접근 차단을 제안하며, 권한/접속 설정 변경 승인 후 재검증이 필요하다. 제품 코드·테스트 코드는 변경하지 않았고 커밋·푸시도 하지 않았다.


## 2026-11-10 테스트 역할 분리 적용 및 배분 검수 완료

사용자의 명시적 승인 후 클라우드에 chagok_test LOGIN 역할을 생성했다. SUPERUSER·CREATEDB·CREATEROLE·REPLICATION·BYPASSRLS를 모두 비활성화하고 역할 멤버십은 부여하지 않았다. 테스트의 임시 ALTER TABLE 검증을 지원하도록 reconciliation_test와 public 객체 소유권만 이 역할로 변경했다. 개발 DB 소유자 agent와 개발 접속 설정은 유지했다. 개발 DB의 PUBLIC CONNECT 및 테스트 역할 권한을 회수했다. 테스트 DB PUBLIC 권한도 회수했다. 생성한 비밀번호는 화면·소스에 출력하지 않고 저장소 밖 /workspace/.local/env.sh의 TEST_DB_USERNAME/PASSWORD만 교체(권한0600). 관리자 SQL을 포함하는 보조 스크립트에도 실제 비밀번호를 하드코딩하지 않았다.

새 역할로 실제 접속하여 관리자 속성 전부false·개발 DB CONNECT false·개발 DB 로그인 permission denied를 확인했다. 개발 DB owner agent, 테스트 DB/public 테이블 owner chagok_test를 확인했다. 개발 거래 데이터는 조회·변경하지 않았다. 기존 Flyway V1~V13 성공 이력 유지, 새 마이그레이션 없음.

AllocationIntegrationTest에 청구100000/입금40000의 정확한 검수 흐름 1개를 추가했다. 확정 후 미수60000, 동일 요청 기존 결과 재사용 및 배분1건, 잔액을 넘는 추가 배분409 후 미수60000 유지, 취소 후 미수100000/미배분40000, 원본 청구·입금 보존, 배분행1개와 확정·취소 감사2개를 검사한다. 기존 테스트는 동시성·초과 배분·후반 감사 실패 롤백도 검증한다. 독립 코드 재검토에서 추가 결함 없음.

실행: source /workspace/.local/env.sh 후 JAVA_HOME=/workspace/.local/jdk/usr/lib/jvm/java-21-openjdk-amd64 GRADLE_USER_HOME=/workspace/.gradle bash gradlew --offline --no-daemon --project-cache-dir /workspace/.local/gradle-project -Dorg.gradle.java.installations.paths=/workspace/.local/jdk/usr/lib/jvm/java-21-openjdk-amd64 test --tests com.smallbiz.reconciliation.vendor.TestDatabaseIsolationTest --tests com.smallbiz.reconciliation.receivables.allocation.AllocationIntegrationTest. BUILD SUCCESSFUL、22초. XML: 가드10+배분18=총28개 통과, 실패·오류·스킵0. 테스트 후 임시 제약조건 제거·V13 성공 유지·개발 CONNECT 차단 유지 재확인. 유료 모델·전체 회귀·실기기·Supabase 연결은 미실행. 제품 소스 변경·환경 게시·커밋·푸시는 하지 않았다. 다음 검수는 별도 승인 후 진행한다.


## 2026-11-12 지난 검수 커밋 및 재시작 중복 등록 조사

사용자 지시로 지난 배분 테스트·권한 분리 안내·작업 기록 3개 파일을 5a9cea9(테스트: 배분 재요청과 취소 후 원본 보존 검수 추가)로 커밋했다. 푸시는 요청 범위에 포함하지 않았다. 클라우드 DB 계정·비밀번호 설정 파일은 저장소 밖에 유지했다.

다음 검수로 CommandKey+Api에 모의 서버를 넣은 메모리 경계 진단을 실행했다. 응답 유실 후 같은 객체로 재요청하면 모의 입금1건, 새 CommandKey 객체로 같은 입력을 보내면 다른 키가 생성돼 모의 입금2건이 됐다. 실제 앱/휴대폰 종료·시작이나 Java와 연결된 클라이언트 재현이 아니다. 실제 서버 동작은 기존 ReceivablesRegistrationIntegrationTest.normalizedIdempotentReplaysReturnOriginalAndKeysAreScopedByOperationAndTenant 1개를 전용 reconciliation_test/chagok_test로 실행해 통과(실패·오류·스킵0、12초) 확인했다. 테스트 전 비관리자·개발 CONNECT 차단을 재확인했다. 모바일 npm test13개 통과. 금액·날짜만으로 중복 삭제하면 정상 별도 입금을 훼손하므로 서버 정책은 변경하지 않았다.

요청 키·입력 복구를 위한 저장 방식 대안·영향·완료 기준을 ADR0002의 승인 전 제안으로 정리했다. 민감한 입력의 기기/브라우저 보관 정책이 바뀌므로 사용자 결정 전에는 기능 구현하지 않는다. 새 라이브러리·마이그레이션·유료 API·외부 배포 없음. 이번 조사 문서는 지난 작업 커밋 이후 미커밋 상태로 남긴다.


## 2026-11-12 입금 등록 재시작 복구 구현·검증

사용자가 요청 키·입력 보관 후 명시적 재전송 방식의 입금 등록 복구 구현을 승인했다. PendingPaymentRecovery와 플랫폼 저장 어댑터를 추가하고 App/Registration에 연결했다. 전송 전에 불변 키·입력을 보관하고 저장 실패는 POST 이전에 거절한다. 서버 origin·계정·사업체로 격리하며 저장 대기 중 계정이 바뀌면 expectedEpoch로 전송을 차단한다. 미확인 요청은 임의 삭제·자동 재시도하지 않고 사용자 확인 버튼으로만 원래 키·내용을 재전송한다. 성공 응답 후 보관 정리를 완료해야 새 입금을 허용한다.

Native는 SecureStore 단일 항목1800바이트 상한, Web은 IndexedDB 트랜잭션을 사용한다. Web 입력 원문은 앱 자체 암호화가 없으며 토큰은 보관하지 않는다. 보관·삭제·사이트 데이터 삭제 시 한계를 UI/모바일 README/ADR에 명시했다. 청구·배분·취소·후보의 영속 복구는 범위 밖이다. 새 라이브러리·서버 API·마이그레이션·제품 금액 계산 변경 없음.

독립 검토에서 ISO 제어문자를 저장한 뒤 서버400으로 복구가 막히는 경로를 발견해 저장 전 검증을 서버와 맞추고 회귀 테스트를 추가했다. 코드포인트 길이와 키 형식도 일치시켰다. 재검토에서 추가 결함 없음. 타입 검사 통과, npm test: 기존·세션14개+복구12개=26개 통과.

로컬 Java8091/Expo8081을 reconciliation_test/chagok_test로 실행하고 scripts/verify-payment-recovery-browser.py의 실제 Chromium5개 시나리오를 통과했다. 실제 서버가 입금을 저장한 뒤 응답만503으로 바꾸고, Chromium 프로세스·컨텍스트를 닫아 동일 디스크 프로필로 재시작했다. 다른 계정에는 미확인 카드가 없고 원래 계정에만 복구됐으며 자동 POST는0회였다. 명시적 재전송은 원래 키·본문과 같고 실제 payment1건/40000원 유지, 성공 후 IndexedDB0건, 저장소 차단 시 등록 버튼 비활성·POST0회, 페이지 오류0을 확인했다. 320px 화면 버튼 줄바꿈을 다듬었다. 문구 수정 후 최초 재실행은 CI Expo의 이전 번들 때문에 버튼 검색이 실패했으며 캐시 초기화·서버 재시작 후 최종5개를 다시 통과했다. Chromium은 샌드박스 밖 기존 시스템 설치본을 사용했으며 새 설치는 하지 않았다.

기존 scripts/verify-receivables-mobile-browser.py를 새 복구 버튼 흐름에 맞춰 갱신하고 기존9개 회귀도 통과(페이지 오류0)했다. 가입·거래처·청구·잔액·입금 복구·사용량·세션 만료·샘플 API0회·반응형을 확인했다. 모든 검증은 새 합성 계정만 생성·정리했으며 개발 DB는 사용하지 않았다. 캡처는 저장소 밖 /workspace/.local/run/payment-recovery-tgdjhvu5 및 payment-recovery-core-regression에 두었다.

명령: mobile에서 npm test / npm run typecheck. 루트에서 source /workspace/.local/env.sh 후 python3 scripts/verify-payment-recovery-browser.py, CHROMIUM_PATH=/usr/bin/chromium python3 scripts/verify-receivables-mobile-browser.py --screenshots /workspace/.local/run/payment-recovery-core-regression. iOS·Android SecureStore 실제 저장/재시작·전체 백엔드 회귀·유료 모델·외부 배포·Supabase는 미실행이다. 기능 코드 직접 검토는 사용자에게 남아 있으며, 이번 구현은 커밋·푸시하지 않았다.
