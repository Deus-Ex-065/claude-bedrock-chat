"""
Adds or removes the bridge hooks in ~/.claude/settings.json.

    python -I install_hooks.py            # show what would change
    python -I install_hooks.py --apply    # back up settings.json, then add the hooks
    python -I install_hooks.py --remove   # remove only the bridge hooks

Each hook is an async `command` hook: curl POSTs the event to the bridge on this PC, its output is
discarded, and it always exits 0. So it prints nothing (nothing reaches Claude's context), never delays
Claude (async), and logs no error when the bridge is not running.
An `http` hook would be simpler, but Claude Code logs a non-blocking error for every event while the
bridge is down.
Two more hooks let the phone approve requests and send messages (see PERMISSION_HOOK and WAITER_HOOK).
Other hooks in the file are left as they are. Only the "hooks" section is printed, never the rest.
"""
import json
import shutil
import sys
import time
from pathlib import Path

SETTINGS = Path.home() / ".claude" / "settings.json"
URL = "http://127.0.0.1:8765/hooks"
BRIDGE_HOOK = {
    "type": "command",
    "command": f"curl -s -m 2 -o /dev/null -H 'Content-Type: application/json' --data-binary @- {URL} >/dev/null 2>&1; exit 0",
    "async": True,
}
EVENTS = ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "Notification", "Stop", "SessionEnd"]

# Control from the phone.
# PermissionRequest: curl prints the bridge reply. The bridge answers at once with an empty body unless the
# phone has approvals on for the session, so the PC dialog shows as usual. No bridge also means empty output.
PERMISSION_HOOK = {
    "type": "command",
    "command": f"curl -s -m 595 -H 'Content-Type: application/json' --data-binary @- {URL}/permission 2>/dev/null; exit 0",
    "timeout": 600,
}
# Stop waiter: runs in the background (asyncRewake) and waits for a message from the phone. On a message it
# prints it to stderr and exits 2, which wakes the session with it. Otherwise it exits 0 and adds nothing.
# The bridge replies 200 (a message), 202 (ask again), or 204 (stop). When the bridge cannot be reached
# (not started yet, or restarted), the hook tries again every 10 s, so an idle session keeps listening.
WAITER_HOOK = {
    "type": "command",
    # It stops by itself after about 11 hours, before the 12-hour hook timeout, so Claude Code never
    # reports a timed-out hook into the session.
    "command": (
        "in=$(cat); end=$(( $(date +%s) + 39000 )); while [ \"$(date +%s)\" -lt \"$end\" ]; do "
        "r=$(printf '%s' \"$in\" | curl -s -m 3600 -w ' %{http_code}' -H 'Content-Type: application/json' "
        "--data-binary @- " + URL + "/wait 2>/dev/null); "
        "code=${r##* }; body=${r% *}; "
        "case \"$code\" in "
        "200) if [ -n \"$body\" ]; then printf '%s' \"$body\" >&2; exit 2; fi; exit 0;; "
        "204) exit 0;; "
        "202) ;; "
        "*) sleep 10;; "
        "esac; done"
    ),
    "asyncRewake": True,
    "timeout": 43200,
}
EXTRA = {"PermissionRequest": [PERMISSION_HOOK], "Stop": [WAITER_HOOK]}


def is_bridge(group):
    """True for bridge hooks from this version (command) and the first one (http)."""
    return any(h.get("url") == URL or URL in (h.get("command") or "") for h in group.get("hooks", []))


def main():
    settings = json.loads(SETTINGS.read_text(encoding="utf-8"))
    hooks = dict(settings.get("hooks", {}))
    remove = "--remove" in sys.argv

    for event in dict.fromkeys(EVENTS + list(EXTRA)):
        groups = [g for g in hooks.get(event, []) if not is_bridge(g)]
        if not remove:
            if event in EVENTS:
                groups.append({"hooks": [dict(BRIDGE_HOOK)]})
            for extra in EXTRA.get(event, []):
                groups.append({"hooks": [dict(extra)]})
        if groups:
            hooks[event] = groups
        else:
            hooks.pop(event, None)

    print(json.dumps({"hooks": hooks}, indent=2))
    if "--apply" not in sys.argv and not remove:
        print("\nNothing changed. Run again with --apply to write this.")
        return

    backup = SETTINGS.with_name(f"settings.json.bak-{time.strftime('%Y%m%d-%H%M%S')}")
    shutil.copy(SETTINGS, backup)
    if hooks:
        settings["hooks"] = hooks
    else:
        settings.pop("hooks", None)
    SETTINGS.write_text(json.dumps(settings, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    json.loads(SETTINGS.read_text(encoding="utf-8"))  # Fails loudly if the result is not valid JSON.
    print(f"\n{'Removed' if remove else 'Added'} the bridge hooks. Backup: {backup}")


if __name__ == "__main__":
    main()
