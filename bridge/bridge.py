"""
Claude Code bridge: watch and control Claude Code sessions from the phone.

Lists Claude Code sessions and serves their messages in the Agent SDK message shape
(type, uuid, session_id, parent_tool_use_id, message{role, content[]}), with content blocks
in the Claude API format: text, thinking, tool_use, tool_result.

Sources are documented interfaces only:
- `claude agents --json` for live sessions and their status,
- the Agent SDK `list_sessions()` and `get_session_messages()` for titles and history.
Transcript files are never parsed or changed here.

Control from the phone also uses documented hooks only:
- PermissionRequest: while the phone has approvals on for a session, the bridge holds the request
  until the phone allows or denies it. Otherwise it answers at once with no decision, and the
  normal dialog shows on the PC.
- Stop with asyncRewake: after each turn a hook waits here for a phone message. When one comes,
  the hook exits 2 and Claude Code wakes the session with it. A prompt typed on the PC releases it.
- Sessions that are not open run the phone message with `claude -p --resume`.

Run:  python -I bridge.py [--port 8765] [--lan]
Then open http://localhost:8765 on this PC.
"""
import argparse
import json
import os
import re
import secrets
import shutil
import site
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

# The Agent SDK can be installed normally (pip install claude-agent-sdk) or into a folder.
# For a folder, set CLAUDE_AGENT_SDK_DIR to it.
SDK_DIR = os.environ.get("CLAUDE_AGENT_SDK_DIR", "D:/Softs/Android-Tools/python-libs/claude-agent-sdk")
if os.path.isdir(SDK_DIR):
    sys.path.insert(0, SDK_DIR)
    site.addsitedir(SDK_DIR)  # Also loads the .pth files that pywin32 needs.
from claude_agent_sdk import get_session_info, get_session_messages, list_sessions  # noqa: E402

MAX_TOOL_RESULT_CHARS = 4000
HERE = Path(__file__).parent

# ---- Access ----
# Every /api request needs the token. The phone gets it once by pairing with a short code.
# /hooks accepts events from this PC only. The viewer is served to this PC only, with the token inside.

TOKEN_FILE = HERE / ".bridge-token"
PAIR_CODE_SECONDS = 600
PAIR_MAX_FAILURES = 5
PAIR_LOCK_SECONDS = 300


def load_token():
    if TOKEN_FILE.exists():
        return TOKEN_FILE.read_text(encoding="utf-8").strip()
    token = secrets.token_urlsafe(32)
    TOKEN_FILE.write_text(token, encoding="utf-8")
    return token


TOKEN = load_token()
_pair = {"code": None, "until": 0.0, "failures": 0, "locked_until": 0.0}
_pair_lock = threading.Lock()


def pair_code():
    """The current 6-digit pairing code, renewed every 10 minutes."""
    with _pair_lock:
        if time.time() > _pair["until"]:
            _pair.update(code=f"{secrets.randbelow(10**6):06d}", until=time.time() + PAIR_CODE_SECONDS)
            print(f"Pairing code: {_pair['code']} (valid 10 minutes)", flush=True)
        return _pair["code"], int(_pair["until"] - time.time())


# ---- Paired phones ----
# One token serves all phones. This list only records which phones paired and when each was last seen,
# so the terminal can show them. Phones send their model name in the X-Device-Name header.

DEVICES_FILE = HERE / ".paired-devices.json"
_devices_lock = threading.Lock()
_devices = {"saved": 0.0, "list": {}}
_streams = {}  # phone name -> open live connections


FORGET_AFTER = 30 * 86400  # Phones not seen for 30 days leave the list (their pairing still works).


def load_devices():
    try:
        phones = json.loads(DEVICES_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        phones = {}
    _devices["list"] = {n: d for n, d in phones.items() if time.time() - d.get("last_seen", 0) < FORGET_AFTER}


def save_devices():
    try:
        DEVICES_FILE.write_text(json.dumps(_devices["list"], indent=1), encoding="utf-8")
        _devices["saved"] = time.time()
    except OSError as e:
        print("Could not save the paired phones list:", e, file=sys.stderr)


def note_device(name, address, paired=False):
    now = int(time.time())
    with _devices_lock:
        unnamed = _devices["list"].pop(f"Phone at {address}", None) if not name.startswith("Phone at ") else None
        d = _devices["list"].setdefault(name, unnamed or {"paired_at": now})
        if paired:
            d["paired_at"] = now
        d.update(last_seen=now, address=address)
        if paired or time.time() - _devices["saved"] > 60:
            save_devices()


def ago(seconds):
    seconds = int(seconds)
    if seconds < 90:
        return "just now"
    for unit, size in (("day", 86400), ("hour", 3600), ("minute", 60)):
        if seconds >= size:
            n = seconds // size
            return f"{n} {unit}{'s' if n > 1 else ''} ago"
    return "just now"


def print_devices():
    phones = _devices["list"]
    if not phones:
        print("Paired phones: none yet", flush=True)
        return
    print(f"Paired phones: {len(phones)}", flush=True)
    for name, d in sorted(phones.items(), key=lambda kv: -kv[1].get("last_seen", 0)):
        print(f"  {name}: last seen {ago(time.time() - d.get('last_seen', 0))} from {d.get('address', '?')}", flush=True)


def try_pair(code):
    """Returns the token for the right code. Locks pairing for 5 minutes after 5 wrong codes."""
    with _pair_lock:
        now = time.time()
        if now < _pair["locked_until"]:
            return None, "Too many wrong codes. Try again in a few minutes."
        if _pair["code"] and now <= _pair["until"] and secrets.compare_digest(str(code), _pair["code"]):
            _pair.update(failures=0, until=0.0)  # A code works once.
            return TOKEN, None
        _pair["failures"] += 1
        if _pair["failures"] >= PAIR_MAX_FAILURES:
            _pair.update(failures=0, locked_until=now + PAIR_LOCK_SECONDS, until=0.0)
            return None, "Too many wrong codes. Pairing is locked for 5 minutes."
        return None, "Wrong or expired code."
COMMAND = re.compile(r"<command-name>(.*?)</command-name>", re.S)
COMMAND_ARGS = re.compile(r"<command-args>(.*?)</command-args>", re.S)
# User-side records that are not prompts the person typed.
HIDDEN_USER_TAGS = ("<task-notification>", "<local-command-stdout>", "<local-command-caveat>")

_live_cache = {"at": 0.0, "data": {}}
_live_lock = threading.Lock()

# ---- Hook events ----
# Claude Code POSTs hook events to /hooks. The bridge always answers 204 with an empty body:
# the documented "success, no output" reply, so nothing is ever added to Claude's context.

HOOK_EVENTS = ("SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "Notification", "Stop", "SessionEnd")
MAX_EVENTS = 2000
_events = []  # (seq, event) in arrival order
_seq = 0
_events_cond = threading.Condition()
# Final reply text per session from Stop. The transcript may not hold it yet when Stop fires.
_last_reply = {}
# What each session is doing right now, for the status line.
_activity = {}


def brief_input(tool_input):
    if not isinstance(tool_input, dict):
        return ""
    for key in ("command", "file_path", "pattern", "description", "url", "query", "prompt"):
        if tool_input.get(key):
            return str(tool_input[key])[:200]
    return ""


def record_hook(data):
    """Turns a hook payload into a small feed event."""
    name = data.get("hook_event_name")
    sid = data.get("session_id")
    if name not in HOOK_EVENTS or not sid:
        return
    event = {"event": name, "session_id": sid}
    if name == "UserPromptSubmit":
        event["prompt"] = data.get("prompt", "")
        _activity[sid] = "Thinking"
        remember_pc_prompt(sid, event["prompt"])
    elif name == "PreToolUse":
        event["tool"] = data.get("tool_name")
        event["input"] = brief_input(data.get("tool_input"))
        _activity[sid] = f"Running {event['tool']}" + (f": {event['input']}" if event["input"] else "")
    elif name == "PostToolUse":
        event["tool"] = data.get("tool_name")
        _activity[sid] = "Thinking"
    elif name == "Notification":
        event["message"] = data.get("message", "")
        event["notification_type"] = data.get("notification_type")
        if event["notification_type"] == "permission_prompt":
            _activity[sid] = "Waiting for approval on the PC"
    elif name == "Stop":
        event["text"] = data.get("last_assistant_message") or ""
        if event["text"]:
            _last_reply[sid] = event["text"]
        # The phone notifies for these turns only. Other turns were started on the PC, where the person is.
        event["phone_turn"] = sid in _phone_turns
        event["remote"] = sid in _remote
        _phone_turns.discard(sid)
        _activity.pop(sid, None)
    elif name == "SessionStart":
        event["source"] = data.get("source")
    elif name == "SessionEnd":
        event["reason"] = data.get("reason")
        _activity.pop(sid, None)
    if sid in _stopping and name in ("PreToolUse", "PostToolUse", "Notification"):
        _activity[sid] = "Stopping"  # Until the turn ends, show that the stop is under way.
    # The PC dialog shows at the same time as the phone card. If the PC answers first, clear the card.
    if name == "PostToolUse":
        cancel_pending(sid, data.get("tool_name"), data.get("tool_input"))
    elif name in ("Stop", "UserPromptSubmit", "SessionEnd"):
        cancel_pending(sid)
    if name in ("Stop", "UserPromptSubmit", "SessionEnd"):
        with _control:
            was = sid in _stopping
            _stopping.discard(sid)
        if was:
            push_event({"event": "ControlChanged", "session_id": sid})
    if name in ("UserPromptSubmit", "SessionEnd"):
        release_waiter(sid)  # Someone typed on the PC, or the session closed.
        _last_reply.pop(sid, None)  # A new turn: the saved final reply is now in the transcript or out of date.
    if name == "SessionEnd":
        set_remote(sid, False)
    push_event(event)


def remember_pc_prompt(sid, prompt):
    """Notes where a PC prompt goes in the history. Reads the transcript in a thread, so the hook answers at once."""
    text = clean_user_text(prompt or "")
    if not text or not text.strip():
        return

    def note():
        last = next((m["uuid"] for m in reversed(messages_raw(sid)) if m.get("uuid")), None)
        with _control:
            prompts = _pc_prompts.setdefault(sid, [])
            prompts.append((last, text))
            del prompts[:-MAX_PC_PROMPTS]
            save_prompts()

    threading.Thread(target=note, daemon=True).start()


def push_event(event):
    """Adds an event to the feed and wakes the stream listeners."""
    global _seq
    event.setdefault("at", int(time.time() * 1000))
    event.setdefault("activity", _activity.get(event["session_id"]))
    with _events_cond:
        _seq += 1
        _events.append((_seq, event))
        del _events[:-MAX_EVENTS]
        _events_cond.notify_all()
    with _live_lock:
        _live_cache["at"] = 0.0  # Status (busy or idle) may have changed.


# ---- Control from the phone ----

PERMISSION_WAIT = 590  # The hook timeout is 600 s. After this the request goes back to the PC dialog.
PROMPT_WAIT = 3300  # One wait. The waiter hook asks again (its curl allows 3600 s, the hook 43200 s).
# Claude Code shows this text to Claude only, not in the PC session. So the reply quotes the message first.
PHONE_NOTE = ("User's next prompt, sent from their phone (the PC does not show it). "
              "Start your reply with: > 📱 **From your phone:** <the message>\n\n")

_control = threading.Condition()
_remote = set()  # Sessions whose approvals go to the phone
_phone_turns = set()  # Sessions whose current turn a phone message started
_pending = {}  # request id -> permission request waiting for the phone
_queued = {}  # session id -> [{"id", "text"}] phone messages not delivered yet
_stopping = set()  # Sessions whose current turn the phone asked to stop. Cleared when the turn ends.
_waiter_gen = {}  # session id -> number of the newest Stop waiter; older waiters give up
_phone_prompts = {}  # session id -> [(uuid of the message before it, text)] delivered by the waiter
# Prompts typed on the PC, from UserPromptSubmit. A prompt typed while Claude works goes in mid-turn, and the
# transcript keeps it in a record that get_session_messages() skips. These fill the gap. Kept while the bridge runs.
_pc_prompts = {}  # session id -> [(uuid of the message before it, text)]
MAX_PC_PROMPTS = 100
_headless = {}  # session id -> Popen of a `claude -p --resume` run for the phone


def control_changed(sid):
    push_event({"event": "ControlChanged", "session_id": sid})


def control_state(sid):
    with _control:
        run = _headless.get(sid)
        return {
            "remote": sid in _remote,
            "pending": [{k: v for k, v in p.items() if k not in ("decision", "suggestions")}
                        for p in _pending.values() if p["session_id"] == sid],
            "queued": [q["text"] for q in _queued.get(sid, [])],
            "queued_ids": [q["id"] for q in _queued.get(sid, [])],
            "stopping": sid in _stopping,
            "running_here": bool(run and run.poll() is None),
        }


REMOTE_FILE = HERE / ".approve-on-phone.json"  # Keeps the phone switch through bridge restarts


PROMPTS_FILE = HERE / ".session-prompts.json"  # Phone and mid-turn PC prompts, through bridge restarts


def load_prompts():
    try:
        data = json.loads(PROMPTS_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return
    for key, target in (("phone", _phone_prompts), ("pc", _pc_prompts)):
        for sid, items in (data.get(key) or {}).items():
            target[sid] = [(after, text) for after, text in items]


def save_prompts():
    """Call with _control held."""
    data = {"phone": _phone_prompts, "pc": _pc_prompts}
    try:
        PROMPTS_FILE.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    except OSError as e:
        print("Could not save the session prompts:", e, file=sys.stderr)


def load_remote():
    try:
        _remote.update(json.loads(REMOTE_FILE.read_text(encoding="utf-8")))
    except (OSError, ValueError):
        pass


def set_remote(sid, on):
    with _control:
        changed = (sid in _remote) != on
        (_remote.add if on else _remote.discard)(sid)
        _control.notify_all()  # Held requests go back to the PC when approvals are turned off.
        if changed:
            try:
                REMOTE_FILE.write_text(json.dumps(sorted(_remote)), encoding="utf-8")
            except OSError as e:
                print("Could not save the approval switches:", e, file=sys.stderr)
    if changed:
        control_changed(sid)


def permission_request(data):
    """Holds a permission request for the phone. Returns the hook output, or None for no decision."""
    sid = data.get("session_id")
    with _control:
        if sid not in _remote:
            return None
        rid = secrets.token_hex(8)
        _pending[rid] = {
            "id": rid, "session_id": sid, "tool": data.get("tool_name"), "input": data.get("tool_input"),
            "summary": brief_input(data.get("tool_input")), "can_always": bool(data.get("permission_suggestions")),
            "suggestions": data.get("permission_suggestions") or [], "at": int(time.time() * 1000), "decision": None,
        }
    before = _activity.get(sid)
    _activity[sid] = f"Waiting for approval: {data.get('tool_name')}"
    control_changed(sid)
    push_event({"event": "ApprovalNeeded", "session_id": sid, "tool": data.get("tool_name"),
                "input": _pending[rid]["summary"]})
    deadline = time.time() + PERMISSION_WAIT
    with _control:
        while _pending[rid]["decision"] is None and sid in _remote and time.time() < deadline:
            _control.wait(deadline - time.time())
        decision = _pending.pop(rid)["decision"]
    if before:
        _activity[sid] = before
    else:
        _activity.pop(sid, None)
    control_changed(sid)
    if not decision:
        return None
    return {"hookSpecificOutput": {"hookEventName": "PermissionRequest", "decision": decision}}


def cancel_pending(sid, tool=None, tool_input=None):
    """Ends held requests that were answered on the PC. With a tool, only the request for that call."""
    with _control:
        hit = [p for p in _pending.values() if p["session_id"] == sid and p["decision"] is None
               and (tool is None or (p["tool"] == tool and p["input"] == tool_input))]
        for p in hit:
            p["decision"] = {}  # Empty: the hook returns no decision, and the PC answer stands.
        if hit:
            _control.notify_all()


def decide(rid, behavior, always=False, message=""):
    with _control:
        p = _pending.get(rid)
        if not p or p["decision"] is not None:
            return False
        if behavior == "allow":
            d = {"behavior": "allow"}
            if always and p["suggestions"]:
                d["updatedPermissions"] = p["suggestions"]
        else:
            d = {"behavior": "deny", "message": message or "The user denied this from their phone."}
        p["decision"] = d
        _control.notify_all()
        return True


def release_waiter(sid):
    with _control:
        _waiter_gen[sid] = _waiter_gen.get(sid, 0) + 1
        _control.notify_all()


# Replies to the waiter hook: 200 with the message, 202 to ask again, 204 to stop waiting.
WAKE, AGAIN, DONE = 200, 202, 204


def wait_for_prompt(data):
    """Called by the Stop waiter hook. Returns (status, text to wake Claude with)."""
    sid = data.get("session_id")
    if sid in _headless or sid not in live_sessions():
        return DONE, None  # Only open sessions wait. A `claude -p` run must end normally.
    with _control:
        gen = _waiter_gen.get(sid, 0) + 1
        _waiter_gen[sid] = gen
        deadline = time.time() + PROMPT_WAIT
        while not _queued.get(sid) and _waiter_gen.get(sid) == gen and time.time() < deadline:
            _control.wait(min(60, deadline - time.time()))
        if _waiter_gen.get(sid) != gen:
            return DONE, None  # A newer waiter took over, or someone typed on the PC.
        if not _queued.get(sid):
            return AGAIN, None
        text = _queued[sid].pop(0)["text"]
        _stopping.discard(sid)  # A new turn starts.
    last = next((m["uuid"] for m in reversed(messages_raw(sid)) if m.get("uuid")), None)
    with _control:
        prompts = _phone_prompts.setdefault(sid, [])
        prompts.append((last, text))
        del prompts[:-MAX_PC_PROMPTS]
        save_prompts()
    _last_reply.pop(sid, None)  # A new turn starts, as for UserPromptSubmit.
    _activity[sid] = "Thinking"
    _phone_turns.add(sid)
    control_changed(sid)
    print(f"Delivered a phone message to session {sid[:8]}", flush=True)
    return WAKE, PHONE_NOTE + text


# Claude Code shows this as "PreToolUse:<tool> hook error: ..." on the PC, so it starts with the part for the person.
STOP_NOTE = ("Stopped from the phone. (For Claude: the user stopped this turn. Do not call more tools. "
             "End your turn now with one short line: Stopped from the phone.)")
OLD_STOP_NOTE = "The user stopped this turn from their phone."  # Wording before 0.8.2, still in old transcripts


def unqueue(sid, qid):
    """Removes a phone message that was not delivered yet. False if it was delivered already."""
    with _control:
        queue = _queued.get(sid, [])
        kept = [q for q in queue if q["id"] != qid]
        found = len(kept) != len(queue)
        _queued[sid] = kept
    control_changed(sid)
    return found


def stop_session(sid):
    """Stops the current turn. Messages that wait to send are removed too, so nothing starts again.

    A `claude -p` run that the bridge started ends at once. An open session on the PC cannot be
    interrupted from outside, so the next tool call is denied with STOP_NOTE, and Claude ends its turn.
    A request that waits for approval on the phone is denied the same way.
    """
    with _control:
        _queued.pop(sid, None)
        run = _headless.get(sid)
        headless = bool(run and run.poll() is None)
        if not headless:
            _stopping.add(sid)
        held = [p["id"] for p in _pending.values() if p["session_id"] == sid and p["decision"] is None]
    for rid in held:
        decide(rid, "deny", message=STOP_NOTE)
    if headless:
        kill_tree(run)
        result = "stopped"
    else:
        _activity[sid] = "Stopping"
        result = "stopping"
    control_changed(sid)
    print(f"Phone stopped session {sid[:8]}", flush=True)
    return result


def kill_tree(proc):
    """Ends a process and its children. On Windows, `claude` may run through a shell wrapper."""
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    else:
        proc.terminate()


def stop_check(data):
    """Called by the PreToolUse stop hook. Returns the hook output, or None to let the tool run."""
    sid = data.get("session_id")
    with _control:
        if sid not in _stopping:
            return None
    return {"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny",
                                   "permissionDecisionReason": STOP_NOTE}}


def send_prompt(sid, text):
    """Queues a phone message for an open session, or runs it with `claude -p --resume` otherwise."""
    with _control:
        run = _headless.get(sid)
        busy_here = bool(run and run.poll() is None)
    if busy_here or sid in live_sessions():
        with _control:
            _queued.setdefault(sid, []).append({"id": secrets.token_hex(6), "text": text})
            _control.notify_all()
        control_changed(sid)
        return "queued"
    info = get_session_info(sid)
    exe = shutil.which("claude")
    if not info or not exe:
        raise RuntimeError("Cannot start Claude Code for this session.")
    set_remote(sid, True)  # A `claude -p` run cannot show dialogs, so approvals come to the phone.
    proc = subprocess.Popen([exe, "-p", "--resume", sid, text], cwd=info.cwd or None,
                            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    with _control:
        _headless[sid] = proc
    _activity[sid] = "Starting Claude Code"
    control_changed(sid)
    threading.Thread(target=_after_headless, args=(sid, proc), daemon=True).start()
    return "started"


def _after_headless(sid, proc):
    proc.wait()
    with _control:
        if _headless.get(sid) is proc:
            del _headless[sid]
        follow = _queued.get(sid) or []
        nxt = follow.pop(0)["text"] if follow else None
    _activity.pop(sid, None)
    control_changed(sid)
    if nxt:
        send_prompt(sid, nxt)


# Event IDs are "<run>-<number>". The numbers start again at each bridge start, so an ID from an
# earlier run must not be used: the phone would get nothing until the new numbers passed the old one.
BOOT = secrets.token_hex(4)


def resume_point(last_id):
    """The event number to continue after, from the phone's Last-Event-ID."""
    run, _, number = (last_id or "").rpartition("-")
    if run == BOOT and number.isdigit():
        return int(number)
    return _seq  # New phone or earlier run: send only new events. The app reloads after a reconnect.


def events_after(seq, timeout):
    """Events newer than seq, waiting up to timeout seconds for at least one."""
    with _events_cond:
        if not any(s > seq for s, _ in _events):
            _events_cond.wait(timeout)
        return [(s, e) for s, e in _events if s > seq]


def live_sessions():
    """Active sessions from `claude agents --json`, keyed by session ID. Cached for 3 s."""
    with _live_lock:
        if time.time() - _live_cache["at"] < 3:
            return _live_cache["data"]
        try:
            out = subprocess.run("claude agents --json", shell=True, capture_output=True, text=True,
                                 encoding="utf-8", timeout=20).stdout
            data = {s["sessionId"]: s for s in json.loads(out)}
        except Exception as e:  # The list still works without live status.
            print("claude agents --json failed:", e, file=sys.stderr)
            data = {}
        _live_cache.update(at=time.time(), data=data)
        return data


def title_of(info):
    return info.custom_title or info.summary or (info.first_prompt or "")[:80] or info.session_id


def session_entry(info, live):
    l = live.get(info.session_id)
    return {
        "session_id": info.session_id,
        "title": title_of(info),
        "cwd": info.cwd,
        "git_branch": info.git_branch,
        "created_at": info.created_at,
        "last_modified": info.last_modified,
        "live": bool(l),
        "status": l.get("status") if l else None,  # "busy" or "idle" while the session is open
        "kind": l.get("kind") if l else None,  # "interactive" or "background"
        "activity": _activity.get(info.session_id),  # From hooks, e.g. "Running Bash: ls"
        "remote": info.session_id in _remote,
        "pending": sum(1 for p in list(_pending.values()) if p["session_id"] == info.session_id),
    }


def sessions(limit):
    live = live_sessions()
    entries = [session_entry(i, live) for i in list_sessions(limit=limit)]
    known = {e["session_id"] for e in entries}
    # Live sessions that fall outside the recent list still belong at the top.
    for sid in live:
        if sid not in known and (info := get_session_info(sid)):
            entries.append(session_entry(info, live))
    entries.sort(key=lambda e: (not e["live"], -(e["last_modified"] or 0)))
    return entries


def clean_tool_result(block):
    content = block.get("content")
    if isinstance(content, list):
        parts = []
        for c in content:
            if c.get("type") == "text":
                parts.append(c.get("text", ""))
            elif c.get("type") == "image":
                parts.append("[image]")
        content = "\n".join(parts)
    text = content if isinstance(content, str) else json.dumps(content)
    if STOP_NOTE in text or OLD_STOP_NOTE in text:  # A tool call that Stop refused. Not an error for the person who stopped it.
        return {"type": "tool_result", "tool_use_id": block.get("tool_use_id"), "is_error": False,
                "content": "Stopped from your phone", "truncated": False}
    truncated = len(text) > MAX_TOOL_RESULT_CHARS
    return {
        "type": "tool_result",
        "tool_use_id": block.get("tool_use_id"),
        "is_error": bool(block.get("is_error")),
        "content": text[:MAX_TOOL_RESULT_CHARS],
        "truncated": truncated,
    }


def clean_user_text(text):
    """Returns the text to show for a user text record, or None to hide it."""
    stripped = text.lstrip()
    if stripped.startswith(HIDDEN_USER_TAGS):
        return None
    if stripped.startswith("<command-name>") or stripped.startswith("<command-message>"):
        m = COMMAND.search(text)
        if m:
            a = COMMAND_ARGS.search(text)
            return f"{m.group(1).strip()} {a.group(1).strip() if a else ''}".strip()
    return text


def clean_message(m):
    """One SDK SessionMessage in SDK message shape, minus records a person would not see."""
    body = m.message if isinstance(m.message, dict) else {}
    raw = body.get("content")
    blocks = [{"type": "text", "text": raw}] if isinstance(raw, str) else list(raw or [])
    content = []
    for b in blocks:
        kind = b.get("type")
        if kind == "text":
            text = b.get("text", "")
            if m.type == "user":
                text = clean_user_text(text)
            if text:
                content.append({"type": "text", "text": text})
        elif kind == "thinking":
            if b.get("thinking"):  # Most thinking is stored without its text.
                content.append({"type": "thinking", "thinking": b["thinking"]})
        elif kind == "tool_use":
            content.append({"type": "tool_use", "id": b.get("id"), "name": b.get("name"), "input": b.get("input")})
        elif kind == "tool_result":
            content.append(clean_tool_result(b))
        elif kind in ("image", "document"):
            # The file itself is not sent to the phone. The phone shows a label, for example "Image (PNG)".
            source = b.get("source") or {}
            media = str(source.get("media_type") or "").split("/")[-1].upper()
            content.append({"type": "attachment", "kind": "image" if kind == "image" else "file",
                            "name": b.get("title") or "", "format": media})
    if not content:
        return None
    out = {
        "type": m.type,
        "uuid": m.uuid,
        "session_id": m.session_id,
        "parent_tool_use_id": m.parent_tool_use_id,
        "message": {"role": body.get("role", m.type), "content": content},
    }
    if m.type == "assistant":
        out["message"]["model"] = body.get("model")
        out["message"]["stop_reason"] = body.get("stop_reason")
    return out


def messages_raw(session_id, limit=None, offset=0):
    raw = get_session_messages(session_id, limit=limit, offset=offset)
    return [c for c in (clean_message(m) for m in raw) if c]


PHONE_QUOTE = re.compile(r"\A\s*> 📱 \*\*From your phone:\*\*[ \t]*([^\n]*)\n*")


def is_text(m):
    """True for a record that shows as a bubble: user or assistant text, not tool calls or results."""
    content = m["message"]["content"]
    if isinstance(content, str):
        return bool(content.strip())
    return any(isinstance(b, dict) and b.get("type") == "text" and b.get("text", "").strip() for b in content)


def messages(session_id, limit, offset):
    out = messages_raw(session_id, limit, offset)
    # The phone shows its own messages as bubbles, so it does not need the quote line that the PC needs.
    # The quote is kept: it rebuilds a bubble that the bridge does not know (sent before PROMPTS_FILE existed).
    quoted = []  # (assistant record, the phone message it quotes)
    for m in out:
        if m["type"] == "assistant":
            for b in m["message"]["content"]:
                if b["type"] == "text":
                    q = PHONE_QUOTE.match(b["text"])
                    if q:
                        quoted.append((m, q.group(1).strip()))
                        b["text"] = b["text"][q.end():]
    out = [m for m in out if m["type"] != "assistant" or any(b["type"] != "text" or b["text"] for b in m["message"]["content"])]
    # Messages the waiter delivered reach Claude as hook output, not as user records. Show them as the user's.
    for after, text in _phone_prompts.get(session_id, []):
        bubble = {"type": "user", "uuid": None, "session_id": session_id, "parent_tool_use_id": None,
                  "message": {"role": "user", "content": [{"type": "text", "text": text}]}, "from_phone": True}
        at = next((i + 1 for i, m in enumerate(out) if m.get("uuid") == after), None)
        if at is not None:
            out.insert(at, bubble)
        elif after is None and offset == 0:
            out.insert(0, bubble)
    # Replies that quote a phone message with no bubble before them: show the quote as the bubble.
    for m, text in quoted:
        at = next((i for i, x in enumerate(out) if x is m), None)
        if at is None or not text:
            continue
        before = next((x for x in reversed(out[:at]) if is_text(x)), None)
        if before is None or before["type"] != "user":
            out.insert(at, {"type": "user", "uuid": None, "session_id": session_id, "parent_tool_use_id": None,
                            "message": {"role": "user", "content": [{"type": "text", "text": text}]}, "from_phone": True})
    # PC prompts that the transcript list does not show: the ones typed while Claude worked.
    with _control:
        pc_prompts = list(_pc_prompts.get(session_id, []))
    for after, text in pc_prompts:
        at = next((i + 1 for i, m in enumerate(out) if m.get("uuid") == after), None)
        if at is None and not (after is None and offset == 0):
            continue
        at = at or 0
        wanted = text.strip()
        if any(m["type"] == "user" and any(b["type"] == "text" and b["text"].strip() == wanted for b in m["message"]["content"])
               for m in out[max(0, at - 1):]):
            continue  # A normal prompt: the transcript has it (it can be the "after" message itself).
        out.insert(at, {"type": "user", "uuid": None, "session_id": session_id, "parent_tool_use_id": None,
                        "message": {"role": "user", "content": [{"type": "text", "text": text}]}, "mid_turn": True})
    # If Stop reported a final reply that the transcript does not hold yet, add it at the end.
    final = PHONE_QUOTE.sub("", _last_reply.get(session_id) or "")
    if final and offset == 0:
        recent = {b["text"].strip() for m in out[-40:] if m["type"] == "assistant"
                  for b in m["message"]["content"] if b["type"] == "text"}
        if final.strip() not in recent:
            out.append({"type": "assistant", "uuid": None, "session_id": session_id, "parent_tool_use_id": None,
                        "message": {"role": "assistant", "content": [{"type": "text", "text": final}]},
                        "from_stop_hook": True})
    return out


class Handler(BaseHTTPRequestHandler):
    def is_local(self):
        return self.client_address[0] in ("127.0.0.1", "::1")

    def device_name(self):
        name = (self.headers.get("X-Device-Name") or "").strip()[:60]
        return name or f"Phone at {self.client_address[0]}"

    def authorized(self, query):
        header = self.headers.get("Authorization", "")
        given = header[7:] if header.startswith("Bearer ") else query.get("token", "")
        ok = secrets.compare_digest(given, TOKEN)
        if ok and not self.is_local():
            note_device(self.device_name(), self.client_address[0])
        return ok

    def _send(self, status, body, content_type="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        try:
            if url.path == "/":
                if not self.is_local():
                    return self._send(403, {"error": "The viewer is available on this PC only."})
                page = (HERE / "viewer.html").read_text(encoding="utf-8").replace("__TOKEN__", TOKEN)
                return self._send(200, page.encode("utf-8"), "text/html; charset=utf-8")
            if url.path == "/api/pairing-code":
                if not self.is_local():
                    return self._send(403, {"error": "Pairing codes are shown on this PC only."})
                code, seconds = pair_code()
                return self._send(200, {"code": code, "expires_in": seconds, "addresses": lan_addresses(self.server.server_port)})
            if not url.path.startswith("/api/"):
                return self._send(404, {"error": "not found"})
            if not self.authorized(q):
                return self._send(401, {"error": "Not paired. Pair this device again."})
            if url.path == "/api/hello":
                return self._send(200, {"name": socket.gethostname(), "bridge": 1})
            if url.path == "/api/sessions":
                return self._send(200, sessions(int(q.get("limit", 30))))
            if url.path == "/api/stream":
                return self.stream(resume_point(self.headers.get("Last-Event-ID") or q.get("since")))
            m = re.fullmatch(r"/api/sessions/([0-9a-f-]{36})/control", url.path)
            if m:
                return self._send(200, control_state(m.group(1)))
            m = re.fullmatch(r"/api/sessions/([0-9a-f-]{36})/messages", url.path)
            if m:
                limit = int(q["limit"]) if "limit" in q else None
                return self._send(200, messages(m.group(1), limit, int(q.get("offset", 0))))
            self._send(404, {"error": "not found"})
        except Exception as e:
            self._send(500, {"error": f"{type(e).__name__}: {e}"})

    def do_POST(self):
        url = urlparse(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        if url.path == "/api/pair":
            try:
                code = json.loads(body or b"{}").get("code", "")
            except Exception:
                code = ""
            token, error = try_pair(code)
            if token:
                note_device(self.device_name(), self.client_address[0], paired=True)
                print(f"Paired {self.device_name()} from {self.client_address[0]}", flush=True)
                return self._send(200, {"token": token, "name": socket.gethostname()})
            return self._send(403, {"error": error})
        if url.path in ("/hooks/permission", "/hooks/wait", "/hooks/check"):
            if not self.is_local():
                return self._send(403, {"error": "forbidden"})
            try:
                data = json.loads(body or b"{}")
                status = 200
                if url.path in ("/hooks/permission", "/hooks/check"):
                    out = permission_request(data) if url.path == "/hooks/permission" else stop_check(data)
                    reply = json.dumps(out).encode("utf-8") if out else b""
                else:
                    status, text = wait_for_prompt(data)
                    reply = text.encode("utf-8") if text else b""
            except Exception as e:
                print("control hook failed:", e, file=sys.stderr)
                status, reply = (AGAIN if url.path == "/hooks/wait" else 200), b""
            # An empty body means no decision and no message, so Claude Code goes on as normal.
            return self._send(status, reply, "text/plain; charset=utf-8")
        if url.path.startswith("/api/"):
            if not self.authorized({}):
                return self._send(401, {"error": "Not paired. Pair this device again."})
            try:
                data = json.loads(body or b"{}")
                m = re.fullmatch(r"/api/sessions/([0-9a-f-]{36})/(remote|prompt|stop|unqueue)", url.path)
                if m and m.group(2) == "stop":
                    return self._send(200, {"result": stop_session(m.group(1))})
                if m and m.group(2) == "unqueue":
                    found = unqueue(m.group(1), str(data.get("id", "")))
                    return self._send(200 if found else 409, control_state(m.group(1)) if found
                                      else {"error": "This message was already sent to Claude."})
                if m and m.group(2) == "remote":
                    set_remote(m.group(1), bool(data.get("on")))
                    return self._send(200, control_state(m.group(1)))
                if m and m.group(2) == "prompt":
                    text = str(data.get("text", "")).strip()
                    if not text:
                        return self._send(400, {"error": "The message is empty."})
                    return self._send(200, {"result": send_prompt(m.group(1), text)})
                m = re.fullmatch(r"/api/permissions/([0-9a-f]{16})", url.path)
                if m:
                    ok = decide(m.group(1), data.get("behavior"), bool(data.get("always")), str(data.get("message", "")))
                    return self._send(200 if ok else 409, {} if ok else {"error": "This request was already answered on the PC."})
            except Exception as e:
                return self._send(500, {"error": f"{type(e).__name__}: {e}"})
            return self._send(404, {"error": "not found"})
        if url.path == "/hooks":
            if not self.is_local():  # Only Claude Code on this PC may post events.
                self.send_response(403)
                self.end_headers()
                return
            try:
                record_hook(json.loads(body or b"{}"))
            except Exception as e:
                print("hook event ignored:", e, file=sys.stderr)
            # Always the empty success reply, even on errors, so Claude Code adds nothing to context.
            self.send_response(204)
            self.end_headers()
            return
        self._send(404, {"error": "not found"})

    def stream(self, since):
        """Server-sent events: one `data:` line per hook event, plus a comment every 15 s to keep the line open."""
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        seq = since
        name = None if self.is_local() else self.device_name()
        if name:
            with _devices_lock:
                _streams[name] = _streams.get(name, 0) + 1
                first = _streams[name] == 1
            if first:
                print(f"Phone connected: {name} ({self.client_address[0]})", flush=True)
        try:
            while True:
                batch = events_after(seq, 15)
                if not batch:
                    self.wfile.write(b": keep-alive\n\n")
                for s, e in batch:
                    self.wfile.write(f"id: {BOOT}-{s}\ndata: {json.dumps(e, ensure_ascii=False)}\n\n".encode("utf-8"))
                    seq = s
                self.wfile.flush()
        except OSError:  # The phone left the screen, slept, or lost the network.
            pass
        finally:
            if name:
                with _devices_lock:
                    _streams[name] -= 1
                    last = _streams[name] == 0
                if last:
                    print(f"Phone disconnected: {name}", flush=True)

    def log_message(self, fmt, *args):
        pass


def lan_addresses(port):
    """Addresses the phone can use on the local network."""
    found = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and not ip.startswith("169.254."):
                found.add(ip)
    except OSError:
        pass
    return [f"http://{ip}:{port}" for ip in sorted(found)]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8765)
    p.add_argument("--lan", action="store_true", help="also listen on the local network (token required)")
    args = p.parse_args()
    # A remote option (a tunnel or VPN address) only changes the address the phone uses, not the bridge.
    server = ThreadingHTTPServer(("0.0.0.0" if args.lan else "127.0.0.1", args.port), Handler)
    print(f"Bridge running at http://localhost:{args.port}", flush=True)
    load_devices()
    load_remote()
    load_prompts()
    print_devices()
    if args.lan:
        for address in lan_addresses(args.port):
            print(f"Phone address: {address}", flush=True)
        pair_code()
    server.serve_forever()


if __name__ == "__main__":
    main()
