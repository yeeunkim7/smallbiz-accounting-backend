# ADR 0001: local account and tenant security boundary

Accepted 2026-11-01 for the first FREE foundation feature; not a public-service launch.

Use Spring Security's stateless filter chain and BCrypt passwords (cost 12). A normalized,
case-insensitive ASCII login is an identifier, not verified email. Signup transactionally
creates one account, one FREE tenant and an OWNER membership. Only server/database policy
can change plans; no upgrade or membership mutation endpoint is exposed. No legacy financial
row is assigned to an account. No paid auth/mail provider is introduced.

Sessions use 32 secure random bytes as URL-safe opaque bearer tokens. Store only SHA-256
hashes; return the raw token once at signup/login, never in me/logs. Epoch UTC expiry uses
server time and configurable `app.auth.session-ttl-seconds` (default 86400). Logout revokes
the current session. Bearer headers, not cookies, are the only credential source; no HTTP
session, form login, basic auth or remember-me. TLS and secure device token storage are
required for a later release. Password reset, verified email, account deletion/retention,
login throttling and public registration abuse controls need a separate pre-pilot decision;
there is no insecure recovery workaround in this feature.

`/api/v1/auth/{signup,login,me,logout}` and `/api/v1/tenants/{id}` are the new boundary.
Tenant lookup always joins membership with the authenticated server principal. Client IDs
and plan fields do not grant access. Unknown signup/login JSON fields are rejected.

Legacy `/api/v1` financial and read-only review routes return 401 anonymously and 403 to
registered accounts by default. Existing response contracts stay intact only for an isolated
local development fixture: both `legacy-dev` profile AND `app.legacy.enabled=true` are
required, and only anonymous legacy calls are allowed. This opt-in exposes global legacy
data and must never be enabled on a publicly reachable server or used for tenant traffic.
Existing regression tests explicitly select that fixture; security tests override it off.

AI review and provider completion independently invoke a server policy before querying
records or reaching HTTP. Anonymous/FREE/STANDARD are denied. PRO must also pass the default
off `app.ai.enabled` kill-switch; even if enabled it remains denied because the preserved
legacy tools have no tenant ownership. There is deliberately no property to enable those
global tools for accounts. Capabilities reports `configured=false` and does not inspect the
provider. Preserved AI algorithm/transport unit tests mock policy solely to test those assets.

V8 adds only account/tenant/membership/session tables. This feature excludes receivables,
payment allocation, quota, billing and mobile implementation. Migration verification uses
only reconciliation_test; V1–V7 and development financial rows are preserved.
