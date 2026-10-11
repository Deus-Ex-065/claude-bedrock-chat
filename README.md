# Bedrock Chat and Claude Code Phone Bridge

Two parts that work together:

| Part | What it is |
|---|---|
| **Bedrock Chat** (`BedrockChat/`) | An Android app for chatting with Claude through your own **Amazon Bedrock API key**. It also has a **PC sessions** screen that connects to the bridge. |
| **Phone bridge** (`bridge/`) | A small Python server on your computer. It lets the phone follow, message, and approve **Claude Code** sessions that run on that computer. |

> This is a personal project. It is not made by or affiliated with Anthropic or Amazon. It uses only
> documented interfaces: the Amazon Bedrock API, Claude Code hooks, `claude agents`, and the Claude Agent SDK.

## What you can do

**Chat (Bedrock Chat app)**
- Chat with Claude models on Amazon Bedrock. Choose the model and the effort level.
- Send images and files, use voice input, and read Markdown replies with code blocks.
- Edit and resend messages, retry replies, star, rename, and delete chats, and use incognito chats.
- Optional "Notify when Claude responds": a reply continues when you leave the app, and the phone notifies you when it is ready.
- The API key is checked with one short request, then stored encrypted on the phone only.

**Claude Code from the phone (app + bridge)**
- See all Claude Code sessions on the computer and follow each one live.
- Send a message to a session. An open session gets it when Claude finishes its turn. A closed session is
  continued with `claude -p --resume` (the app warns you first, because that costs more tokens).
- Allow or deny tool requests (for example a shell command) from the phone.
- Get notifications for approvals and finished replies, also when the app is closed.

## Get started

1. **Install the app.** Download the APK from the
   [latest release](../../releases/latest) and open it on the phone (Android 8.0 or later).
2. **Set up the computer.** You need Claude Code, Python 3.10 or later, and the Claude Agent SDK:
   ```bash
   python -m pip install claude-agent-sdk
   python -I bridge/install_hooks.py            # shows the change to ~/.claude/settings.json
   python -I bridge/install_hooks.py --apply    # backs up settings.json, then adds the hooks
   python -I bridge/bridge.py --lan             # prints the phone address and a pairing code
   ```
3. **Pair the phone.** In the app, open the menu, tap **PC sessions**, and enter the address and the code.

The full setup guide, with screenshots, troubleshooting, and security notes, is
**[bridge/Bridge-Setup-Guide.pdf](bridge/Bridge-Setup-Guide.pdf)**. Its source is `bridge/docs/guide.html`.

## Token use

The bridge never calls Claude by itself.

| Action | Tokens |
|---|---|
| Follow sessions live, notifications, waiting for a phone message | None |
| Allow or deny a request | None extra (the same as answering on the computer) |
| Message to an **open** session | About the same as typing it on the computer, plus a note of about 25 tokens |
| Message to a **closed** session | Can be expensive: Claude reads the whole conversation again |

Details are in section 7 of the setup guide.

## Security

- The phone gets a secret token when it pairs. Every request needs it. Pairing codes work one time and expire after 10 minutes.
- Hook events and pairing codes are accepted from the computer itself only.
- The connection is plain HTTP on your local network. **Do not forward the port or expose it to the internet.**
- The bridge keeps its token in `bridge/.bridge-token`. This file, `.paired-devices.json`, and
  `.approve-on-phone.json` are local state and are excluded from git.
- Notifications have no Allow button, so a locked phone cannot approve a command.

## Repository layout

```
BedrockChat/                Android app (Kotlin, Jetpack Compose)
  app/src/main/java/dev/local/bedrockchat/
    data/                   Bedrock client, settings, chat store, bridge client
    ui/                     Screens: chat, chats, settings, PC sessions
    PcViewModel.kt          PC sessions state and live updates
    PcWatchService.kt       Background connection and notifications
bridge/
  bridge.py                 The bridge server (HTTP + server-sent events)
  install_hooks.py          Adds or removes the bridge hooks in ~/.claude/settings.json
  viewer.html               A browser view of the sessions, at http://localhost:8765
  Bridge-Setup-Guide.pdf    Setup guide; source in docs/guide.html
tools/
  demo_bridge.py            A bridge with sample data, for screenshots
  snap.py, make_test_chat.py  Emulator test helpers
```

## Build the app

The project has no Gradle wrapper. Use Gradle 9.x, a JDK, and the Android SDK (compileSdk 37).

```bash
cd BedrockChat
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
gradle :app:assembleDebug
# The APK is in app/build/outputs/apk/debug/app-debug.apk
```

Tested with Gradle 9.8.1, JDK 25, Android Gradle Plugin 9.4.1, and Kotlin 2.4.

## Remove the bridge

```bash
python -I bridge/install_hooks.py --remove
```

This removes only the bridge hooks. Your other hooks and settings stay as they are.

## License

[MIT](LICENSE). The fonts in the app are under the SIL Open Font License 1.1; see [licenses/](licenses/).

## Planned

- Access away from home Wi-Fi, through a private network (for example Tailscale) or a protected tunnel.
  Until then, the bridge works on the local network only.
