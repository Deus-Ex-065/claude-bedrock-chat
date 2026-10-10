"""A fake bridge with made-up sessions, for clean screenshots in the guide. Not for real use.

    python -I tools/demo_bridge.py      # listens on 0.0.0.0:8766, pairing code 123456
"""
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

NOW = int(time.time() * 1000)
SID = "11111111-1111-4111-8111-111111111111"
SESSIONS = [
    {"session_id": SID, "title": "Add dark mode to the website", "cwd": "C:\\Projects\\website", "last_modified": NOW,
     "live": True, "status": "busy", "activity": None, "remote": True, "pending": 1},
    {"session_id": "22222222-2222-4222-8222-222222222222", "title": "Fix failing unit tests", "cwd": "C:\\Projects\\api",
     "last_modified": NOW - 25 * 60000, "live": True, "status": "idle"},
    {"session_id": "33333333-3333-4333-8333-333333333333", "title": "Write release notes for v2.4", "cwd": "C:\\Projects\\api",
     "last_modified": NOW - 3 * 3600000},
    {"session_id": "44444444-4444-4444-8444-444444444444", "title": "Explain the build pipeline", "cwd": "C:\\Projects\\tools",
     "last_modified": NOW - 26 * 3600000},
]


def msg(kind, *blocks):
    return {"type": kind, "uuid": None, "message": {"role": kind, "content": list(blocks)}}


MESSAGES = [
    msg("user", {"type": "text", "text": "Add a dark mode toggle to the settings page"}),
    msg("assistant", {"type": "text", "text": "I'll look at how the theme is set up first."},
        {"type": "tool_use", "name": "Grep", "input": {"pattern": "theme", "path": "src"}}),
    msg("user", {"type": "tool_result", "content": "src/theme.css\nsrc/settings/Settings.tsx", "is_error": False}),
    msg("assistant", {"type": "text", "text": "The colours live in **`src/theme.css`**. I'll add a `dark` class and a toggle:\n\n"
                      "1. Define dark colour variables\n2. Add the toggle to `Settings.tsx`\n3. Save the choice in local storage"},
        {"type": "tool_use", "name": "Edit", "input": {"file_path": "src/theme.css"}}),
    msg("user", {"type": "tool_result", "content": "The file was updated.", "is_error": False}),
    msg("assistant", {"type": "text", "text": "Now I'll run the tests to check nothing broke."}),
]
CONTROL = {"remote": True, "queued": [], "running_here": False,
           "pending": [{"id": "abcdef0123456789", "tool": "Bash", "summary": "npm test", "input": {"command": "npm test"},
                        "can_always": True}]}


class Handler(BaseHTTPRequestHandler):
    def _send(self, body, status=200):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/sessions":
            return self._send(SESSIONS)
        if re.fullmatch(r"/api/sessions/[0-9a-f-]+/messages", path):
            return self._send(MESSAGES if SID in path else MESSAGES[:1])
        if re.fullmatch(r"/api/sessions/[0-9a-f-]+/control", path):
            return self._send(CONTROL if SID in path else {})
        if path == "/api/stream":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            try:
                while True:
                    self.wfile.write(b": keep-alive\n\n")
                    self.wfile.flush()
                    time.sleep(15)
            except OSError:
                return
        self._send({"error": "not found"}, 404)

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length") or 0))
        if self.path == "/api/pair":
            return self._send({"token": "demo", "name": "My-PC"})
        self._send({"result": "queued"})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8766), Handler).serve_forever()
