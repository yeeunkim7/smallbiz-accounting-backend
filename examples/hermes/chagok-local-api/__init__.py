import json
import re
from datetime import date
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, build_opener, HTTPRedirectHandler, ProxyHandler
from uuid import UUID


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def reply(status, **fields):
    return json.dumps({"status": status, **fields}, ensure_ascii=False)


def lookup(args, **kwargs):
    requested = args.get("as_of") if isinstance(args, dict) else None
    try:
        valid = isinstance(requested, str) and date.fromisoformat(requested).isoformat() == requested
    except ValueError:
        valid = False
    if not valid:
        return reply("INVALID_DATE")
    try:
        session = json.loads((Path.home() / ".config/chagok-lab/session.json").read_text())
        tenant = str(UUID(session["tenant_id"]))
        token = session["access_token"]
        if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9_-]{43}", token):
            return reply("LOCAL_SESSION_INVALID")
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        return reply("LOCAL_SESSION_INVALID")

    path = f"/api/v1/tenants/{tenant}/receivables/summary?" + urlencode({"asOf": requested})
    request = Request("http://127.0.0.1:8080" + path,
                      headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
                      method="GET")
    try:
        opener = build_opener(ProxyHandler({}), NoRedirect())
        with opener.open(request, timeout=10) as response:
            raw = response.read(65537)
        if len(raw) > 65536:
            return reply("UPSTREAM_INVALID_RESPONSE")
        result = json.loads(raw)
    except HTTPError as error:
        return reply({401: "AUTH_REQUIRED", 403: "ACCESS_DENIED"}.get(error.code, "UPSTREAM_HTTP_ERROR"))
    except (URLError, OSError):
        return reply("UPSTREAM_UNAVAILABLE")
    except ValueError:
        return reply("UPSTREAM_INVALID_RESPONSE")

    money = ("invoiceAmount", "allocatedAmount", "outstandingAmount", "overdueAmount", "paymentAmount", "unallocatedAmount")
    counts = ("invoiceCount", "paymentCount", "activeAllocationLineCount")
    if not isinstance(result, dict) or result.get("tenantId") != tenant or result.get("asOf") != requested:
        return reply("UPSTREAM_INVALID_RESPONSE")
    if result.get("allocationState") != "CURRENT":
        return reply("UPSTREAM_INVALID_RESPONSE")
    if any(not isinstance(result.get(k), str) or not re.fullmatch(r"[0-9]+", result[k]) for k in money):
        return reply("UPSTREAM_INVALID_RESPONSE")
    if any(type(result.get(k)) is not int or result[k] < 0 for k in counts):
        return reply("UPSTREAM_INVALID_RESPONSE")
    status = "NO_DATA" if result["invoiceCount"] == result["paymentCount"] == 0 else "DATA"
    safe = {k: result[k] for k in ("tenantId", "asOf", "allocationState", *counts, *money)}
    return reply(status, source="LOCAL_JAVA_API", scope="CURRENT_LEDGER", evidence=path, summary=safe)


def register(ctx):
    ctx.register_tool(
        name="chagok_receivables_summary", toolset="chagok_local_api",
        schema={"type": "function", "function": {
            "name": "chagok_receivables_summary",
            "description": "조회 전용. 로컬 Java API의 현재 원장 미수금 요약을 읽습니다. as_of는 연체 판정 기준일이며 하루 거래 필터나 과거 잔액이 아닙니다. 금액은 서버 반환값을 사용합니다. NO_DATA는 미입금·미업로드를 뜻하지 않습니다. 개별 거래 원본은 이 도구로 조회하지 않습니다.",
            "parameters": {"type": "object", "properties": {
                "as_of": {"type": "string", "description": "연체 판정 기준일 YYYY-MM-DD"}
            }, "required": ["as_of"], "additionalProperties": False}
        }}, handler=lookup)
