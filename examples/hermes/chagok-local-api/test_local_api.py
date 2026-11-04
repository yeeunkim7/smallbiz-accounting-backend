import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError, URLError

spec = importlib.util.spec_from_file_location("chagok_local_api", Path(__file__).with_name("__init__.py"))
plugin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plugin)
TENANT = "11111111-1111-4111-8111-111111111111"
TOKEN = "A" * 43


def snapshot(**changes):
    value = dict(tenantId=TENANT, asOf="2026-11-04", allocationState="CURRENT",
                 invoiceCount=1, paymentCount=1, activeAllocationLineCount=1,
                 invoiceAmount="100000", allocatedAmount="40000", outstandingAmount="60000",
                 overdueAmount="0", paymentAmount="40000", unallocatedAmount="0")
    value.update(changes)
    return value


class Response(io.BytesIO):
    pass


class ReadOnlyAdapterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        folder = self.home / ".config/chagok-lab"
        folder.mkdir(parents=True)
        self.session = folder / "session.json"
        self.session.write_text(json.dumps(dict(tenant_id=TENANT, access_token=TOKEN)))
        self.home_patch = patch.object(plugin.Path, "home", return_value=self.home)
        self.home_patch.start()
        self.addCleanup(self.home_patch.stop)

    def invoke(self, payload=None, error=None, args=None):
        raw = payload if isinstance(payload, bytes) else json.dumps(payload or snapshot()).encode()
        with patch.object(plugin, "build_opener") as make:
            if error:
                make.return_value.open.side_effect = error
            else:
                make.return_value.open.return_value = Response(raw)
            result = json.loads(plugin.lookup(args if args is not None else {"as_of": "2026-11-04"}))
            call = make.return_value.open.call_args
        return result, call

    def test_pins_get_origin_tenant_and_preserves_server_money(self):
        result, call = self.invoke(args={"as_of": "2026-11-04", "url": "https://elsewhere.invalid", "tenant_id": "other"})
        request = call.args[0]
        self.assertEqual(request.get_method(), "GET")
        self.assertEqual(request.full_url, f"http://127.0.0.1:8080/api/v1/tenants/{TENANT}/receivables/summary?asOf=2026-11-04")
        self.assertEqual(request.get_header("Authorization"), "Bearer " + TOKEN)
        self.assertEqual(call.kwargs["timeout"], 10)
        self.assertEqual(result["summary"]["outstandingAmount"], "60000")
        self.assertEqual(result["scope"], "CURRENT_LEDGER")

    def test_does_not_return_tokens_or_unknown_upstream_fields(self):
        result, _ = self.invoke(snapshot(accessToken=TOKEN, extra="private"))
        self.assertNotIn(TOKEN, json.dumps(result))
        self.assertNotIn("extra", result["summary"])

    def test_no_data_is_not_zero_outstanding_with_existing_records(self):
        empty = snapshot(invoiceCount=0, paymentCount=0, activeAllocationLineCount=0,
                         invoiceAmount="0", allocatedAmount="0", outstandingAmount="0", paymentAmount="0")
        self.assertEqual(self.invoke(empty)[0]["status"], "NO_DATA")
        self.assertEqual(self.invoke(snapshot(allocatedAmount="100000", outstandingAmount="0"))[0]["status"], "DATA")

    def test_invalid_date_never_opens_network(self):
        for value in [None, 4, "2026-02-30", "20261104", "https://elsewhere.invalid"]:
            with self.subTest(value=value), patch.object(plugin, "build_opener") as make:
                self.assertEqual(json.loads(plugin.lookup({"as_of": value}))["status"], "INVALID_DATE")
                make.assert_not_called()

    def test_invalid_local_session_never_opens_network(self):
        self.session.unlink()
        with patch.object(plugin, "build_opener") as make:
            self.assertEqual(json.loads(plugin.lookup({"as_of": "2026-11-04"}))["status"], "LOCAL_SESSION_INVALID")
            make.assert_not_called()

    def test_auth_failures_and_redirects_are_not_no_data(self):
        for code, status in [(401, "AUTH_REQUIRED"), (403, "ACCESS_DENIED"), (302, "UPSTREAM_HTTP_ERROR")]:
            with self.subTest(code=code):
                result, _ = self.invoke(error=HTTPError("http://127.0.0.1", code, "private", {}, None))
                self.assertEqual(result["status"], status)
                self.assertNotIn("private", json.dumps(result))
        self.assertIsNone(plugin.NoRedirect().redirect_request(None, None, 302, "", {}, "https://elsewhere.invalid"))

    def test_network_failure_is_not_no_data(self):
        self.assertEqual(self.invoke(error=URLError("private"))[0]["status"], "UPSTREAM_UNAVAILABLE")

    def test_oversize_or_malformed_json_is_rejected(self):
        for raw in [b"x" * 65537, b"bad-json", b"[]"]:
            with self.subTest(size=len(raw)):
                self.assertEqual(self.invoke(raw)[0]["status"], "UPSTREAM_INVALID_RESPONSE")

    def test_response_identity_and_types_are_checked(self):
        for changes in [dict(tenantId="other"), dict(asOf="2026-11-05"), dict(allocationState="OLD"),
                        dict(invoiceCount=True), dict(outstandingAmount=60000), dict(overdueAmount="-1")]:
            with self.subTest(changes=changes):
                self.assertEqual(self.invoke(snapshot(**changes))[0]["status"], "UPSTREAM_INVALID_RESPONSE")

    def test_registers_only_summary_tool(self):
        from unittest.mock import Mock
        ctx = Mock()
        plugin.register(ctx)
        ctx.register_tool.assert_called_once()
        values = ctx.register_tool.call_args.kwargs
        self.assertEqual(values["name"], "chagok_receivables_summary")
        self.assertEqual(values["toolset"], "chagok_local_api")
        self.assertEqual(values["schema"]["function"]["parameters"]["required"], ["as_of"])
        self.assertFalse(values["schema"]["function"]["parameters"]["additionalProperties"])


if __name__ == "__main__":
    unittest.main()
