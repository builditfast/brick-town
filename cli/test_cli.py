"""Tests for the bricktown CLI and its MCP shim.

Run: /opt/homebrew/bin/python3 -m unittest discover -s cli -p 'test_*.py'

The stub server copies the parts of the real one the CLI depends on: refresh
tokens are spent on use (a reused one answers 401), PUT /town/expenses needs
paidBy and createdAt in the body, and errors are {"error": sentence}.
"""

import base64
import contextlib
import io
import json
import os
import stat
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bricktown  # noqa: E402
import mcp_server  # noqa: E402


def jwt(exp):
    body = base64.urlsafe_b64encode(json.dumps({"exp": int(exp)}).encode()).decode().rstrip("=")
    return f"h.{body}.s"


class Stub:
    def __init__(self):
        self.lock = threading.Lock()
        self.refresh_token = "r0"
        self.access = jwt(time.time() + 900)
        self.refresh_calls = 0
        self.calls = []
        self.puts = []
        self.categories = [
            {"id": "c1", "name": "Groceries", "short": "Food", "colorIndex": 0, "position": 0},
            {"id": "c2", "name": "Eating out", "short": "Out", "colorIndex": 1, "position": 1},
            {"id": "c3", "name": "Subscriptions", "short": "Subs", "colorIndex": 2, "position": 2},
        ]
        self.expenses = [
            {"id": "e1", "categoryId": "c1", "amountCents": 4210, "date": "2026-11-23", "note": "Lidl",
             "recurring": False, "shared": False, "refund": False, "paidBy": "u2",
             "createdAt": "2026-11-23T10:00:00Z"},
        ]

    def snapshot(self):
        return {"town": {"id": "t1", "name": "Home", "settings": {"monthlyBudgetCents": 500000, "payday": 1,
                                                                  "currency": "EUR", "billsFirst": True,
                                                                  "timeZone": "Europe/Bucharest"}},
                "viewerId": "u1",
                "members": [{"userId": "u1", "name": "Emanuel"}, {"userId": "u2", "name": "Ana"}],
                "categories": self.categories, "expenses": self.expenses,
                "goldMarks": [], "billSkips": [], "invites": [], "serverTime": "2026-11-24T10:00:00Z"}


def make_handler(stub):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def _send(self, status, body=None):
            raw = b"" if body is None else json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def _body(self):
            n = int(self.headers.get("Content-Length") or 0)
            return json.loads(self.rfile.read(n)) if n else None

        def _authed(self):
            return self.headers.get("Authorization") == f"Bearer {stub.access}"

        def _route(self, method):
            stub.calls.append((method, self.path))
            body = self._body()
            if self.path == "/auth/login":
                if body == {"email": "me@example.com", "password": "pw"}:
                    with stub.lock:
                        stub.refresh_token = "r1"
                    return self._send(200, {"accessToken": stub.access, "refreshToken": "r1",
                                            "user": {"id": "u1", "email": "me@example.com", "name": "Emanuel"}})
                return self._send(401, {"error": "That email and password don't match. Try again, or create an account."})
            if self.path == "/auth/refresh":
                with stub.lock:
                    stub.refresh_calls += 1
                    if body.get("refreshToken") != stub.refresh_token:
                        return self._send(401, {"error": "Sign in again."})
                    stub.refresh_token = f"r{stub.refresh_calls + 1}"
                    stub.access = jwt(time.time() + 900)
                    return self._send(200, {"accessToken": stub.access, "refreshToken": stub.refresh_token})
            if self.path == "/auth/logout":
                return self._send(204)
            if not self._authed():
                return self._send(401, {"error": "Sign in again."})
            if method == "GET" and self.path == "/town":
                return self._send(200, stub.snapshot())
            if method == "GET" and self.path.startswith("/town/summary"):
                return self._send(200, {"sentence": "You have 57 euros left today and 1442 for the month.",
                                        "todayLeftCents": 5700, "towers": []})
            if method == "PUT" and self.path.startswith("/town/expenses/"):
                missing = [k for k in ("paidBy", "createdAt", "categoryId", "amountCents", "date") if k not in body]
                if missing:
                    return self._send(400, {"error": f"Missing {missing}."})
                stub.puts.append(body)
                stub.expenses.append(body)
                return self._send(200, body)
            if method == "DELETE" and self.path.startswith("/town/expenses/"):
                gone = self.path.rsplit("/", 1)[1]
                stub.expenses[:] = [e for e in stub.expenses if e["id"] != gone]
                return self._send(204)
            return self._send(404, {"error": "Not found."})

        def do_GET(self):
            self._route("GET")

        def do_POST(self):
            self._route("POST")

        def do_PUT(self):
            self._route("PUT")

        def do_DELETE(self):
            self._route("DELETE")

    return Handler


def run(argv):
    """(exit code, stdout, stderr) of the CLI run in-process."""
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            code = bricktown.main(argv)
        except SystemExit as e:
            code = e.code
    return code, out.getvalue(), err.getvalue()


class AgainstStubServer(unittest.TestCase):
    def setUp(self):
        self.stub = Stub()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.stub))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.tmp = tempfile.TemporaryDirectory()
        config = Path(self.tmp.name) / "bricktown"
        self.patch = mock.patch.multiple(
            bricktown, API=f"http://127.0.0.1:{self.server.server_port}", CONFIG_DIR=config,
            TOKEN_FILE=config / "token", LOCK_FILE=config / "token.lock", ADDED_FILE=config / "added.json")
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.server.shutdown()
        self.server.server_close()
        self.tmp.cleanup()

    def login(self):
        with mock.patch("sys.stdin", io.StringIO("pw\n")):
            return run(["login", "--email", "me@example.com", "--password-stdin", "--json"])

    def test_signed_out_is_exit_3(self):
        code, out, _ = run(["summary", "--json"])
        self.assertEqual(code, bricktown.EXIT_SIGNED_OUT)
        self.assertIn("bricktown login", json.loads(out)["error"])

    def test_wrong_password_is_bad_input_with_the_server_sentence(self):
        with mock.patch("sys.stdin", io.StringIO("nope\n")):
            code, _, err = run(["login", "--email", "me@example.com", "--password-stdin"])
        self.assertEqual(code, bricktown.EXIT_INPUT)
        self.assertIn("don't match", err)

    def test_login_stores_the_pair_privately(self):
        code, out, _ = self.login()
        self.assertEqual(code, 0, out)
        mode = stat.S_IMODE(os.stat(bricktown.TOKEN_FILE).st_mode)
        self.assertEqual(mode, 0o600)
        self.assertEqual(json.loads(bricktown.TOKEN_FILE.read_text())["refreshToken"], "r1")

    def test_a_token_saved_for_another_server_is_not_used(self):
        self.login()
        with mock.patch.object(bricktown, "API", "http://elsewhere:1"):
            code, _, _ = run(["summary"])
        self.assertEqual(code, bricktown.EXIT_SIGNED_OUT)

    def test_summary_prints_the_server_sentence(self):
        self.login()
        code, out, _ = run(["summary"])
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "You have 57 euros left today and 1442 for the month.")

    def test_add_matches_the_tower_and_sends_a_full_body(self):
        self.login()
        code, out, _ = run(["add", "12,50", "food", "--note", "Lidl", "--shared", "--json"])
        self.assertEqual(code, 0, out)
        body = self.stub.puts[-1]
        self.assertEqual((body["categoryId"], body["amountCents"], body["shared"], body["paidBy"]),
                         ("c1", 1250, True, "u1"))
        self.assertTrue(body["createdAt"].endswith("Z"))
        result = json.loads(out)
        self.assertEqual(result["added"]["amount"], "€12.50")
        self.assertIn("57 euros", result["summary"])

    def test_unknown_tower_is_exit_2_and_lists_the_towers(self):
        self.login()
        code, _, err = run(["add", "12", "rent"])
        self.assertEqual(code, bricktown.EXIT_INPUT)
        self.assertIn("Groceries, Eating out, Subscriptions", err)
        self.assertEqual(self.stub.puts, [])

    def test_undo_removes_only_what_the_cli_added(self):
        self.login()
        self.assertEqual(run(["undo"])[0], bricktown.EXIT_INPUT)
        run(["add", "30", "eating out"])
        added = self.stub.puts[-1]["id"]
        code, out, _ = run(["undo", "--json"])
        self.assertEqual(code, 0, out)
        self.assertNotIn(added, [e["id"] for e in self.stub.expenses])
        self.assertIn("e1", [e["id"] for e in self.stub.expenses])
        self.assertEqual(run(["undo"])[0], bricktown.EXIT_INPUT)

    def test_undo_of_a_brick_removed_in_the_app_says_so(self):
        self.login()
        run(["add", "30", "eating out"])
        self.stub.expenses.pop()
        code, out, _ = run(["undo"])
        self.assertEqual(code, 0)
        self.assertIn("already removed", out)

    def test_recent_names_tower_and_payer(self):
        self.login()
        code, out, _ = run(["recent", "--limit", "1", "--json"])
        self.assertEqual(code, 0)
        row = json.loads(out)["expenses"][0]
        self.assertEqual((row["tower"], row["paidBy"], row["amount"]), ("Groceries", "Ana", "€42.10"))

    def test_an_expired_access_token_is_refreshed_once_and_rotated(self):
        self.login()
        tokens = json.loads(bricktown.TOKEN_FILE.read_text())
        tokens["accessToken"] = jwt(time.time() - 10)
        bricktown.TOKEN_FILE.write_text(json.dumps(tokens))
        self.assertEqual(run(["summary"])[0], 0)
        self.assertEqual(self.stub.refresh_calls, 1)
        self.assertEqual(json.loads(bricktown.TOKEN_FILE.read_text())["refreshToken"], self.stub.refresh_token)

    def test_parallel_calls_refresh_once_and_stay_signed_in(self):
        self.login()
        tokens = json.loads(bricktown.TOKEN_FILE.read_text())
        tokens["accessToken"] = jwt(time.time() - 10)
        bricktown.TOKEN_FILE.write_text(json.dumps(tokens))
        results = []
        threads = [threading.Thread(target=lambda: results.append(bricktown.access_token())) for _ in range(4)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        self.assertEqual(len(results), 4)
        self.assertEqual(self.stub.refresh_calls, 1)
        self.assertEqual(run(["summary"])[0], 0)

    def test_a_spent_refresh_token_signs_out(self):
        self.login()
        tokens = json.loads(bricktown.TOKEN_FILE.read_text())
        tokens["accessToken"] = jwt(time.time() - 10)
        tokens["refreshToken"] = "spent"
        bricktown.TOKEN_FILE.write_text(json.dumps(tokens))
        self.assertEqual(run(["summary"])[0], bricktown.EXIT_SIGNED_OUT)
        self.assertFalse(bricktown.TOKEN_FILE.exists())

    def test_unreachable_server_is_exit_4(self):
        self.login()
        self.server.shutdown()
        self.server.server_close()
        code, _, _ = run(["summary"])
        self.assertEqual(code, bricktown.EXIT_SERVER)


class Parsing(unittest.TestCase):
    def test_amounts(self):
        for text, cents in [("12", 1200), ("12.5", 1250), ("12,50", 1250), ("€12", 1200),
                            ("12 euros", 1200), ("312 lei", 31200), (12.5, 1250), ("0.99", 99)]:
            self.assertEqual(bricktown.parse_amount(text), cents, text)
        for bad in ["", "0", "-5", "abc", "12.345", "99999999"]:
            with self.assertRaises(bricktown.CliError, msg=bad):
                bricktown.parse_amount(bad)

    def test_tower_matching(self):
        towers = Stub().categories
        self.assertEqual(bricktown.match_tower(towers, "grocery")["id"], "c1")
        self.assertEqual(bricktown.match_tower(towers, "FOOD")["id"], "c1")
        self.assertEqual(bricktown.match_tower(towers, "Eating  Out")["id"], "c2")
        self.assertEqual(bricktown.match_tower(towers, "subscription")["id"], "c3")
        self.assertEqual(bricktown.match_tower(towers, "eat")["id"], "c2")
        with self.assertRaises(bricktown.CliError):
            bricktown.match_tower(towers, "rent")

    def test_money_format(self):
        self.assertEqual(bricktown.fmt(131200, "EUR"), "€1 312")
        self.assertEqual(bricktown.fmt(7010, "EUR"), "€70.10")
        self.assertEqual(bricktown.fmt(31200, "RON"), "312 lei")
        self.assertEqual(bricktown.fmt(-1200, "GBP"), "-£12")


class McpShim(unittest.TestCase):
    def test_initialize_and_tools_list_do_no_work(self):
        with mock.patch.object(mcp_server, "run_cli", side_effect=AssertionError("ran the CLI")):
            init = mcp_server.handle({"jsonrpc": "2.0", "id": 1, "method": "initialize",
                                      "params": {"protocolVersion": "2025-06-18"}})
            listed = mcp_server.handle({"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
        self.assertEqual(init["result"]["protocolVersion"], "2025-06-18")
        self.assertIn("left today", init["result"]["instructions"])
        self.assertEqual({t["name"] for t in listed["result"]["tools"]},
                         {"bricktown_summary", "bricktown_recent", "bricktown_add", "bricktown_undo"})

    def test_every_argv_the_server_sends_parses_in_the_real_cli(self):
        calls = []
        with mock.patch.object(mcp_server, "run_cli", lambda argv: calls.append(argv) or ({}, True)):
            mcp_server.call_tool("bricktown_summary", {})
            mcp_server.call_tool("bricktown_summary", {"date": "2026-11-24"})
            mcp_server.call_tool("bricktown_recent", {"limit": 3})
            mcp_server.call_tool("bricktown_add", {"amount": 12.5, "tower": "eating out", "note": "Pizza",
                                                   "date": "2026-11-24", "monthly": True, "shared": True})
            mcp_server.call_tool("bricktown_add", {"amount": "30", "tower": "-weird"})
            mcp_server.call_tool("bricktown_undo", {})
        parser = bricktown.build_parser()
        for argv in calls:
            args = parser.parse_args(mcp_server.cli_argv(argv))
            self.assertTrue(args.json)
        add = parser.parse_args(mcp_server.cli_argv(calls[3]))
        self.assertEqual((add.amount, add.tower, add.note, add.date, add.monthly, add.shared),
                         ("12.5", "eating out", "Pizza", "2026-11-24", True, True))
        self.assertEqual(parser.parse_args(mcp_server.cli_argv(calls[4])).tower, "-weird")

    def test_refused_calls_come_back_as_errors(self):
        self.assertTrue(mcp_server.call_tool("bricktown_add", {"amount": "12"})["isError"])
        self.assertTrue(mcp_server.call_tool("nope", {})["isError"])
        with mock.patch.object(mcp_server, "run_cli", lambda argv: ({"error": "There is no tower called rent."}, False)):
            result = mcp_server.call_tool("bricktown_add", {"amount": 12, "tower": "rent"})
        self.assertTrue(result["isError"])
        self.assertIn("no tower", result["content"][0]["text"])

    def test_run_cli_reads_the_json_error_of_a_failed_run(self):
        with tempfile.TemporaryDirectory() as d:
            config = Path(d) / "cfg"
            env = {**os.environ, "BRICKTOWN_CONFIG": str(config), "BRICKTOWN_API": "http://127.0.0.1:9"}
            with mock.patch.dict(os.environ, env):
                payload, ok = mcp_server.run_cli(["summary"])
        self.assertFalse(ok)
        self.assertIn("not signed in", payload["error"])


if __name__ == "__main__":
    unittest.main()
