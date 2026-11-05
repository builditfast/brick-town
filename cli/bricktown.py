#!/usr/bin/env python3
"""bricktown: your Brick Town town from a terminal or the voice agent.

Every verb takes --json. Exit codes: 0 done, 2 bad input (the server's own
sentence is printed), 3 not signed in, 4 server unreachable or failing.

The server is the only source of numbers. `summary` reads GET /town/summary,
which runs the same domain code as the Month screen, so nothing here derives
a budget of its own.

The refresh token is single use: the server spends it on every refresh and
refuses a second use. A voice turn can run two tools at once, so the token
file is read, refreshed and rewritten under one file lock, and the access
token is cached until shortly before it expires.

No third-party dependencies.
"""

import argparse
import base64
import contextlib
import datetime as dt
import fcntl
import getpass
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

API = os.environ.get("BRICKTOWN_API", "http://127.0.0.1:8090").rstrip("/")
CONFIG_DIR = Path(os.environ.get("BRICKTOWN_CONFIG") or Path.home() / ".config" / "bricktown")
TOKEN_FILE = CONFIG_DIR / "token"
LOCK_FILE = CONFIG_DIR / "token.lock"
ADDED_FILE = CONFIG_DIR / "added.json"
TIMEOUT_S = 20
# Refresh a token with less than this left, so a slow call cannot expire mid-flight.
ACCESS_MARGIN_S = 60
# The server refuses more than 999 999 900 cents; refuse it here with a clearer sentence.
MAX_AMOUNT_CENTS = 999_999_900
ADDED_KEPT = 50

EXIT_OK, EXIT_INPUT, EXIT_SIGNED_OUT, EXIT_SERVER = 0, 2, 3, 4
SIGNED_OUT = "You are not signed in to Brick Town. Run: bricktown login"
SYMBOLS = {"EUR": "€", "USD": "$", "GBP": "£"}


class CliError(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code
        self.message = message


# ---------------------------------------------------------------- transport

def http(method, path, body=None, token=None):
    """(status, parsed JSON or None). Raises CliError when the server cannot be reached."""
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
            status, raw = resp.status, resp.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    except (urllib.error.URLError, OSError) as e:
        reason = getattr(e, "reason", e)
        raise CliError(EXIT_SERVER, f"The Brick Town server at {API} did not answer ({reason}).")
    try:
        payload = json.loads(raw) if raw else None
    except ValueError:
        payload = None
    return status, payload


def _server_sentence(payload):
    return payload.get("error") if isinstance(payload, dict) else None


def check(status, payload):
    """The payload of a 2xx answer; otherwise the CliError its status means."""
    if 200 <= status < 300:
        return payload
    if status == 401:
        raise CliError(EXIT_SIGNED_OUT, SIGNED_OUT)
    sentence = _server_sentence(payload)
    if status >= 500 or sentence is None:
        raise CliError(EXIT_SERVER, sentence or f"The Brick Town server answered {status}.")
    raise CliError(EXIT_INPUT, sentence)


# ---------------------------------------------------------------- tokens

@contextlib.contextmanager
def _token_lock():
    CONFIG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    with open(LOCK_FILE, "a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def _write_private(path, obj):
    tmp = path.with_name(path.name + ".tmp")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(obj, f)
    os.chmod(tmp, 0o600)
    os.replace(tmp, path)


def _load_tokens():
    """The stored pair for this server, or None. A pair saved for another server does not count."""
    try:
        tokens = json.loads(TOKEN_FILE.read_text())
    except (OSError, ValueError):
        return None
    if not isinstance(tokens, dict) or tokens.get("api") != API or not tokens.get("refreshToken"):
        return None
    return tokens


def _save_tokens(access, refresh):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    _write_private(TOKEN_FILE, {"api": API, "accessToken": access, "refreshToken": refresh})


def _expires_at(jwt):
    try:
        part = jwt.split(".")[1]
        part += "=" * (-len(part) % 4)
        return int(json.loads(base64.urlsafe_b64decode(part))["exp"])
    except (AttributeError, IndexError, ValueError, KeyError, TypeError):
        return 0


def access_token(force_refresh=False):
    with _token_lock():
        tokens = _load_tokens()
        if tokens is None:
            raise CliError(EXIT_SIGNED_OUT, SIGNED_OUT)
        if not force_refresh and _expires_at(tokens.get("accessToken")) - time.time() > ACCESS_MARGIN_S:
            return tokens["accessToken"]
        status, payload = http("POST", "/auth/refresh", {"refreshToken": tokens["refreshToken"]})
        if status == 401:
            TOKEN_FILE.unlink(missing_ok=True)
        pair = check(status, payload)
        _save_tokens(pair["accessToken"], pair["refreshToken"])
        return pair["accessToken"]


def api(method, path, body=None):
    """One signed-in call. A 401 refreshes once and retries once, then means signed out."""
    status, payload = http(method, path, body, access_token())
    if status == 401:
        status, payload = http(method, path, body, access_token(force_refresh=True))
    return check(status, payload)


# ---------------------------------------------------------------- parsing and formatting

_AMOUNT = re.compile(r"^\d{1,7}(?:[.,]\d{1,2})?$")


def parse_amount(text):
    """'12', '12.5', '12,50', '€12', '12 euros' to cents. Refuses zero, negatives and junk."""
    cleaned = re.sub(r"(?i)\s*(euros?|eur|dollars?|usd|pounds?|gbp|lei|ron|€|\$|£)\s*", "", str(text)).strip()
    if not _AMOUNT.match(cleaned):
        raise CliError(EXIT_INPUT, f"'{text}' is not an amount. Say it like 12 or 12.50.")
    whole, _, frac = cleaned.replace(",", ".").partition(".")
    cents = int(whole) * 100 + int((frac + "00")[:2])
    if cents <= 0:
        raise CliError(EXIT_INPUT, "The amount must be more than zero.")
    if cents > MAX_AMOUNT_CENTS:
        raise CliError(EXIT_INPUT, "That amount is too large for one brick.")
    return cents


def _singular(text):
    words = " ".join(text.lower().split())
    if words.endswith("ies"):
        return words[:-3] + "y"
    return words[:-1] if words.endswith("s") and not words.endswith("ss") else words


def match_tower(categories, query):
    """Exact name or short label (case and plural ignored), then a unique name prefix."""
    wanted = _singular(query)
    ordered = sorted(categories, key=lambda c: c.get("position", 0))
    for key in ("name", "short"):
        for c in ordered:
            if _singular(c[key]) == wanted:
                return c
    prefixed = [c for c in ordered if c["name"].lower().startswith(" ".join(query.lower().split()))]
    if len(prefixed) == 1:
        return prefixed[0]
    names = ", ".join(c["name"] for c in ordered) or "none yet"
    raise CliError(EXIT_INPUT, f"There is no tower called {query}. Your towers are: {names}.")


def fmt(cents, currency):
    """'€12', '€12.50', '€1 312', '312 lei', '-€12' for a refund."""
    sign = "-" if cents < 0 else ""
    whole, frac = divmod(abs(cents), 100)
    number = f"{whole:,}".replace(",", " ") + (f".{frac:02d}" if frac else "")
    if currency == "RON":
        return f"{sign}{number} lei"
    return f"{sign}{SYMBOLS.get(currency, '')}{number}"


def _iso_date(text):
    try:
        return dt.date.fromisoformat(text).isoformat()
    except ValueError:
        raise argparse.ArgumentTypeError(f"'{text}' is not a date like 2026-11-24.")


def _positive_int(text):
    value = int(text)
    if value < 1:
        raise argparse.ArgumentTypeError("must be 1 or more")
    return value


def _today_in(snapshot):
    try:
        return dt.datetime.now(ZoneInfo(snapshot["town"]["settings"]["timeZone"])).date()
    except (KeyError, ZoneInfoNotFoundError, ValueError):
        return dt.date.today()


def _now_iso():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def _currency(snapshot):
    return snapshot["town"]["settings"].get("currency", "EUR")


# ---------------------------------------------------------------- the log of bricks this CLI added

def _load_added():
    try:
        rows = json.loads(ADDED_FILE.read_text())
        return rows if isinstance(rows, list) else []
    except (OSError, ValueError):
        return []


def _save_added(rows):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    _write_private(ADDED_FILE, rows[-ADDED_KEPT:])


# ---------------------------------------------------------------- verbs

def cmd_login(args):
    email = (args.email or input("Email: ")).strip()
    if args.password_stdin:
        password = sys.stdin.readline().rstrip("\n")
    else:
        password = getpass.getpass("Password: ")
    status, payload = http("POST", "/auth/login", {"email": email, "password": password})
    if status == 401:
        raise CliError(EXIT_INPUT, _server_sentence(payload) or "That email and password don't match.")
    auth = check(status, payload)
    with _token_lock():
        _save_tokens(auth["accessToken"], auth["refreshToken"])
    name = auth["user"]["name"]
    return {"signedIn": True, "name": name, "email": auth["user"]["email"]}, f"Signed in as {name}."


def cmd_logout(args):
    with _token_lock():
        tokens = _load_tokens()
        if tokens is not None:
            with contextlib.suppress(CliError):
                http("POST", "/auth/logout", {"refreshToken": tokens["refreshToken"]})
        TOKEN_FILE.unlink(missing_ok=True)
    return {"signedIn": False}, "Signed out."


def cmd_summary(args):
    summary = api("GET", "/town/summary" + (f"?date={args.date}" if args.date else ""))
    return summary, summary["sentence"]


def cmd_add(args):
    cents = parse_amount(args.amount)
    snapshot = api("GET", "/town")
    tower = match_tower(snapshot["categories"], args.tower)
    expense = {
        "id": str(uuid.uuid4()),
        "categoryId": tower["id"],
        "amountCents": cents,
        "date": args.date or _today_in(snapshot).isoformat(),
        "note": (args.note or "").strip(),
        "recurring": args.monthly,
        "shared": args.shared,
        "refund": False,
        # The server sets these two itself; the body still has to carry them.
        "paidBy": snapshot["viewerId"],
        "createdAt": _now_iso(),
    }
    saved = api("PUT", f"/town/expenses/{expense['id']}", expense)
    amount = fmt(cents, _currency(snapshot))
    label = f"{amount} from {tower['name']}"
    _save_added(_load_added() + [{"id": saved["id"], "api": API, "label": label}])
    text = f"Added {amount} to {tower['name']}."
    result = {"added": {"id": saved["id"], "tower": tower["name"], "amount": amount,
                        "amountCents": cents, "date": saved["date"], "note": saved.get("note", "")}}
    with contextlib.suppress(CliError):
        summary = api("GET", "/town/summary")
        result["summary"] = summary["sentence"]
        text = f"{text} {summary['sentence']}"
    return result, text


def cmd_recent(args):
    snapshot = api("GET", "/town")
    towers = {c["id"]: c["name"] for c in snapshot["categories"]}
    people = {m["userId"]: m["name"] for m in snapshot["members"]}
    currency = _currency(snapshot)
    newest = sorted(snapshot["expenses"], key=lambda e: e["createdAt"], reverse=True)[: args.limit]
    rows = [{
        "id": e["id"],
        "date": e["date"],
        "tower": towers.get(e["categoryId"], "a removed tower"),
        "note": e.get("note", ""),
        "amount": fmt(-e["amountCents"] if e.get("refund") else e["amountCents"], currency),
        "refund": e.get("refund", False),
        "monthly": e.get("recurring", False),
        "shared": e.get("shared", False),
        "paidBy": people.get(e["paidBy"], "someone who left"),
    } for e in newest]
    if not rows:
        return {"expenses": [], "total": 0}, "No bricks yet."
    lines = [f"{r['date']}  {r['amount']:>10}  {r['tower']}"
             + (f", {r['note']}" if r["note"] else "") + f" ({r['paidBy']})" for r in rows]
    return {"expenses": rows, "total": len(snapshot["expenses"])}, "\n".join(lines)


def cmd_undo(args):
    added = _load_added()
    mine = [row for row in added if row.get("api") == API]
    if not mine:
        raise CliError(EXIT_INPUT, "There is nothing to undo: this CLI has not added a brick yet.")
    last = mine[-1]
    snapshot = api("GET", "/town")
    still_there = any(e["id"] == last["id"] for e in snapshot["expenses"])
    if still_there:
        api("DELETE", f"/town/expenses/{last['id']}")
    _save_added([row for row in added if row is not last])
    if not still_there:
        return {"undone": None, "alreadyGone": last["label"]}, f"The last brick I added ({last['label']}) was already removed."
    return {"undone": last["label"], "id": last["id"]}, f"Removed {last['label']}."


VERBS = {"login": cmd_login, "logout": cmd_logout, "summary": cmd_summary,
         "add": cmd_add, "recent": cmd_recent, "undo": cmd_undo}


def build_parser():
    parser = argparse.ArgumentParser(prog="bricktown", description="Your Brick Town town: what is left, what you spent, add a brick.")
    sub = parser.add_subparsers(dest="verb", required=True)

    def verb(name, help_text):
        p = sub.add_parser(name, help=help_text)
        p.add_argument("--json", action="store_true", help="answer as JSON")
        return p

    login = verb("login", "sign in; the refresh token is stored at ~/.config/bricktown/token (mode 600)")
    login.add_argument("--email")
    login.add_argument("--password-stdin", action="store_true", help="read the password from stdin instead of a prompt")
    verb("logout", "sign out and forget the stored token")
    summary = verb("summary", "what is left today and this month")
    summary.add_argument("--date", type=_iso_date, help="YYYY-MM-DD; default is today in the town's zone")
    add = verb("add", "add a brick: an amount spent on a tower")
    add.add_argument("amount", help="12, 12.50 or 12,50")
    add.add_argument("tower", help="tower name or short label, e.g. groceries or food")
    add.add_argument("--note", default="")
    add.add_argument("--date", type=_iso_date)
    add.add_argument("--monthly", action="store_true", help="a bill that comes back every month")
    add.add_argument("--shared", action="store_true", help="split with the other person in the town")
    recent = verb("recent", "the newest bricks")
    recent.add_argument("--limit", type=_positive_int, default=5)
    verb("undo", "remove the newest brick this CLI added")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        payload, text = VERBS[args.verb](args)
    except CliError as e:
        if args.json:
            print(json.dumps({"error": e.message}, ensure_ascii=False))
        print(e.message, file=sys.stderr)
        return e.code
    except KeyboardInterrupt:
        return EXIT_INPUT
    print(json.dumps(payload, ensure_ascii=False) if args.json else text)
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
