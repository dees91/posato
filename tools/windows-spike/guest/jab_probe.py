"""Spike probe: read Posato's Compose tree through the Java Access Bridge, press a button, assert the next step.

usage: python jab_probe.py dump | press <name> [expect <text>] | type <field name or -> <text>
Prints one JSON object on stdout.
"""
import ctypes
import glob
import json
import logging
import os
import queue
import sys
import threading
import time
from ctypes import byref, wintypes

logging.basicConfig(stream=sys.stderr, level=logging.WARNING, force=True)
if "RC_JAVA_ACCESS_BRIDGE_DLL" not in os.environ:
    # The JDK that setup-guest.ps1 unpacked under C:\Tools carries the bridge's client library.
    jdks = sorted(glob.glob(r"C:\Tools\jdk-21*"))
    if jdks:
        os.environ["RC_JAVA_ACCESS_BRIDGE_DLL"] = os.path.join(jdks[-1], "bin", "windowsaccessbridge-64.dll")

from JABWrapper.context_tree import ContextTree  # noqa: E402
from JABWrapper.jab_wrapper import JavaAccessBridgeWrapper  # noqa: E402

WINDOW_TITLE = "Posato"


def start_bridge():
    """The bridge answers through window messages, so it lives on a thread that pumps them."""
    ready = queue.Queue()

    def pump():
        ready.put(JavaAccessBridgeWrapper(ignore_callbacks=True))
        message = wintypes.MSG()
        while ctypes.windll.user32.GetMessageW(byref(message), 0, 0, 0) > 0:
            ctypes.windll.user32.TranslateMessage(byref(message))
            ctypes.windll.user32.DispatchMessageW(byref(message))

    threading.Thread(target=pump, daemon=True).start()
    jab = ready.get(timeout=30)
    time.sleep(1)  # the bridge discovers running JVMs asynchronously
    return jab


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD), ("dwExtraInfo", ctypes.c_size_t)]


class INPUT(ctypes.Structure):
    class _U(ctypes.Union):
        _fields_ = [("ki", KEYBDINPUT), ("pad", ctypes.c_byte * 32)]
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", _U)]


def send_text(jab, text):
    """Compose's editable text refuses setTextContents, so type into the focused window as a keyboard would."""
    user32 = ctypes.windll.user32
    hwnd = user32.FindWindowW(None, WINDOW_TITLE)
    user32.keybd_event(0x12, 0, 0, 0)  # Alt lets this process move the foreground
    user32.SetForegroundWindow(hwnd)
    user32.keybd_event(0x12, 0, 2, 0)
    time.sleep(0.3)
    for ch in text:
        for flags in (0x0004, 0x0004 | 0x0002):  # KEYEVENTF_UNICODE, then key up
            event = INPUT(type=1)
            event.ki = KEYBDINPUT(0, ord(ch), flags, 0, 0)
            user32.SendInput(1, byref(event), ctypes.sizeof(INPUT))
        time.sleep(0.02)


def nodes(jab):
    jab.switch_window_by_title(WINDOW_TITLE)
    result = []
    for node in ContextTree(jab):
        info = node.context_info
        result.append({
            "role": info.role,
            "name": info.name,
            "states": info.states,
            "bounds": [info.x, info.y, info.width, info.height],
            "actions": node.get_actions() if info.accessibleAction else [],
            "_node": node,
        })
    return result


def public(entries):
    return [{k: v for k, v in e.items() if k != "_node"} for e in entries if e["name"] or e["actions"]]


def main():
    jab = start_bridge()
    command = sys.argv[1] if len(sys.argv) > 1 else "dump"
    before = nodes(jab)
    if command == "dump":
        print(json.dumps({"windows": [w.title for w in jab.get_windows()], "nodes": public(before)}, indent=1))
        return
    if command == "type":
        label, text = sys.argv[2], sys.argv[3]
        field = next((e for e in before if e["role"] == "text" and (e["name"] or "") == label), None) or next((e for e in before if e["role"] == "text"), None)
        if field is None:
            print(json.dumps({"ok": False, "error": "no text field", "nodes": public(before)}, indent=1))
            sys.exit(2)
        field["_node"].click()
        field["_node"].request_focus()
        time.sleep(0.3)
        send_text(jab, text)
        time.sleep(0.5)
        print(json.dumps({"ok": True, "typed": text, "after": public(nodes(jab))}, indent=1))
        return
    name = sys.argv[2]
    target = next((e for e in before if e["name"] == name and e["actions"]), None)
    if target is None:
        print(json.dumps({"ok": False, "error": f"no actionable node named {name!r}", "nodes": public(before)}, indent=1))
        sys.exit(2)
    target["_node"].click()
    expect = sys.argv[4] if len(sys.argv) > 4 and sys.argv[3] == "expect" else None
    deadline = time.time() + 10
    while True:
        time.sleep(0.5)
        after = nodes(jab)
        names = [e["name"] for e in after]
        if expect is None or any(expect in (n or "") for n in names) or time.time() > deadline:
            break
    ok = expect is None or any(expect in (n or "") for n in names)
    print(json.dumps({"ok": ok, "pressed": name, "actions": target["actions"], "expect": expect, "after": public(after)}, indent=1))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
