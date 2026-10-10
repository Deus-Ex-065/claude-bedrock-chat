# Claude Code Phone Bridge

The setup guide, with screenshots, is in **Bridge-Setup-Guide.pdf** in this folder.
Its source is `docs/guide.html`. To rebuild the PDF, print that page to PDF from a browser.

Quick start:

```bash
python -I bridge/install_hooks.py --apply
python -I bridge/bridge.py --lan
```

Then pair the phone in the app: menu > PC sessions, and enter the address and code that the bridge prints.
