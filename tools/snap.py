"""Capture emulator screen (downscaled) and a compact UI tree. Usage: python -I snap.py <name>"""
import subprocess, sys, io, re, os
import xml.etree.ElementTree as ET
from PIL import Image

name = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "survey")
png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
img = Image.open(io.BytesIO(png)).convert("RGB")
img.resize((img.width // 2, img.height // 2)).save(os.path.join(out, name + ".jpg"), quality=80)

subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
xml = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout
lines = []
def walk(n, d):
    a = n.attrib
    t, cd, rid = a.get("text", ""), a.get("content-desc", ""), a.get("resource-id", "")
    flags = "".join(f for f, k in (("C", "clickable"), ("S", "scrollable"), ("E", "editable")) if a.get(k) == "true")
    if a.get("class", "").endswith("EditText"): flags += "E"
    if t or cd or flags or rid:
        cls = a.get("class", "").split(".")[-1]
        lines.append(f"{'  '*d}{cls} [{flags}] {a.get('bounds','')} rid={rid.split('/')[-1]!r} t={t[:60]!r} d={cd[:60]!r}")
    for c in n: walk(c, d + 1)
try:
    walk(ET.fromstring(xml), 0)
except ET.ParseError:
    lines.append("UI dump failed")
txt = "\n".join(lines)
open(os.path.join(out, name + ".txt"), "w", encoding="utf-8").write(txt)
print(txt)
