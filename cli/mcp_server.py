#!/usr/bin/env python3
"""Thin MCP shim over the `bricktown` CLI, for the voice agent.

CLI-first: this server reimplements nothing. Each tool runs bricktown.py with
--json, so the voice agent and a terminal give the same answer, and the
numbers come from the server's own summary, the same one the Month screen
shows.

Nothing runs on initialize or tools/list: `claude mcp list` spawns this server
to health-check it.

Zero third-party deps: newline-delimited JSON-RPC 2.0 over stdio, the same
shape as any minimal MCP stdio server.
"""

import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
# Overridable so a test can point the shim at a stub instead of the real CLI.
BRICKTOWN_BIN = os.environ.get("BRICKTOWN_BIN", str(ROOT / "bricktown.py"))
PROTOCOL_FALLBACK = "2024-11-05"
SERVER_INFO = {"name": "bricktown", "version": "1.0.0"}
INSTRUCTIONS = ("Brick Town is the user's spending town: every 25 euros spent is one brick, and each category "
                "(groceries, eating out, home, shopping...) is a tower. Use it when the user asks how much money "
                "they have left today or this month, how much they spent on a category, what they spent "
                "recently, or says they spent an amount on something (add 12 euros groceries, I spent 30 on "
                "eating out). Amounts are in the town's currency. bricktown_summary answers with one sentence "
                "to read out plus the numbers per tower. After bricktown_add, read back what was added; if the "
                "user says it was wrong, call bricktown_undo. If a tool says the user is not signed in, tell "
                "them to run 'bricktown login' once in a terminal on the Mac.")

_READ_ONLY = {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False}
_DATE = {"type": "string", "description": "YYYY-MM-DD. Leave out for today in the town's time zone."}

TOOLS = [
    {
        "name": "bricktown_summary",
        "title": "Money left",
        "description": "What is left today and this month, what was spent so far, the streak, and each tower's "
                       "total this pay month. 'sentence' is ready to read aloud; tower totals answer 'how much "
                       "did we spend on eating out'.",
        "inputSchema": {"type": "object", "properties": {"date": _DATE}},
        "annotations": _READ_ONLY,
    },
    {
        "name": "bricktown_recent",
        "title": "Recent bricks",
        "description": "The newest bricks (expenses), newest first, with date, tower, note, amount and who paid.",
        "inputSchema": {"type": "object",
                        "properties": {"limit": {"type": "integer", "description": "How many, default 5."}}},
        "annotations": _READ_ONLY,
    },
    {
        "name": "bricktown_add",
        "title": "Add a brick",
        "description": "Records money the user spent. tower is the category as they said it (groceries, food, "
                       "eating out, transport...); an unknown tower comes back as an error that lists the towers, "
                       "so ask which one they meant. Answers what was added and what is left today.",
        "inputSchema": {"type": "object",
                        "properties": {"amount": {"type": ["number", "string"],
                                                  "description": "The amount spent, like 12 or 12.50."},
                                       "tower": {"type": "string", "description": "The category name."},
                                       "note": {"type": "string", "description": "Where or what, like Lidl."},
                                       "date": _DATE,
                                       "monthly": {"type": "boolean", "description": "A bill that repeats every month."},
                                       "shared": {"type": "boolean", "description": "Split with the other person in the town."}},
                        "required": ["amount", "tower"]},
        "annotations": {"readOnlyHint": False, "destructiveHint": False, "idempotentHint": False,
                        "openWorldHint": False},
    },
    {
        "name": "bricktown_undo",
        "title": "Undo the last brick",
        "description": "Removes the newest brick that this voice tool added. Use it when the user says the brick "
                       "just added was wrong.",
        "inputSchema": {"type": "object", "properties": {}},
        "annotations": {"readOnlyHint": False, "destructiveHint": True, "idempotentHint": False,
                        "openWorldHint": False},
    },
]


def cli_argv(argv):
    """--json goes right after the verb: anything after the "--" in an add is read as a positional."""
    return [argv[0], "--json", *argv[1:]]


def run_cli(argv):
    """(payload, ok). ok is False for a refused request or a failing server."""
    try:
        p = subprocess.run([sys.executable, BRICKTOWN_BIN, *cli_argv(argv)],
                           capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.SubprocessError) as e:
        return {"error": f"bricktown CLI did not run: {e}"}, False
    try:
        payload = json.loads(p.stdout) if p.stdout.strip() else None
    except ValueError:
        payload = None
    if p.returncode != 0:
        message = payload.get("error") if isinstance(payload, dict) else None
        return {"error": message or (p.stderr or "bricktown CLI failed").strip()}, False
    if payload is None:
        return {"error": "bricktown CLI returned no JSON"}, False
    return payload, True


def call_tool(name, args):
    args = args or {}
    if name == "bricktown_summary":
        argv = ["summary"] + (["--date", str(args["date"])] if args.get("date") else [])
    elif name == "bricktown_recent":
        argv = ["recent"] + (["--limit", str(int(args["limit"]))] if args.get("limit") else [])
    elif name == "bricktown_add":
        amount, tower = str(args.get("amount") or "").strip(), str(args.get("tower") or "").strip()
        if not amount or not tower:
            return _err_text("bricktown_add needs an amount and a tower")
        argv = ["add", "--", amount, tower]
        if args.get("note"):
            argv[1:1] = ["--note", str(args["note"])]
        if args.get("date"):
            argv[1:1] = ["--date", str(args["date"])]
        if args.get("monthly"):
            argv[1:1] = ["--monthly"]
        if args.get("shared"):
            argv[1:1] = ["--shared"]
    elif name == "bricktown_undo":
        argv = ["undo"]
    else:
        return _err_text(f"unknown tool: {name}")

    payload, ok = run_cli(argv)
    return _json_text(payload) if ok else _err_text(payload.get("error", "failed"))


def _json_text(obj):
    return {"content": [{"type": "text", "text": json.dumps(obj, indent=2, ensure_ascii=False)}]}


def _err_text(msg):
    return {"content": [{"type": "text", "text": msg}], "isError": True}


def _result(mid, result):
    return {"jsonrpc": "2.0", "id": mid, "result": result}


def handle(msg):
    """Return a JSON-RPC response dict, or None for notifications."""
    method = msg.get("method")
    mid = msg.get("id")
    if method == "initialize":
        pv = (msg.get("params") or {}).get("protocolVersion")
        return _result(mid, {
            "protocolVersion": pv if isinstance(pv, str) else PROTOCOL_FALLBACK,
            "capabilities": {"tools": {}},
            "serverInfo": SERVER_INFO,
            "instructions": INSTRUCTIONS,
        })
    if method == "notifications/initialized":
        return None
    if method == "ping":
        return _result(mid, {})
    if method == "tools/list":
        return _result(mid, {"tools": TOOLS})
    if method == "tools/call":
        params = msg.get("params") or {}
        return _result(mid, call_tool(params.get("name"), params.get("arguments")))
    if mid is None:
        return None
    return {"jsonrpc": "2.0", "id": mid,
            "error": {"code": -32601, "message": f"method not found: {method}"}}


def main():
    while True:
        line = sys.stdin.readline()
        if not line:
            return
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except ValueError:
            resp = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "parse error"}}
        else:
            resp = handle(msg)
        if resp is not None:
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
