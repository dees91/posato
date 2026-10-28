"""Reads and drives Posato's Compose tree in the Windows clone through the Java Access Bridge, for posato-control.

usage: python posato-jab.py dump | press <path> [<name>] | focus <path> [<name>] | type <text> | key <combo>
       python posato-jab.py -    (the arguments as a JSON array of strings on standard input, in UTF-8)

`dump` prints the window's accessible tree (Posato's window, or a dialog of Posato that takes input over it) as JSON: each element with its role, name, description, text, states,
bounds, actions, and path (child indexes from the window, joined by "."). `press` runs an element's click action,
`focus` clicks it and requests focus, `type` sends Unicode keyboard input to the focused window, and `key` sends one
key combination such as `ctrl+a` or `return`. Exits 2 when the window or the element is missing, 1 on another failure.
"""
import ctypes
import glob
import json
import logging
import os
import queue
import re
import sys
import threading
import time
from ctypes import byref, wintypes

sys.stderr.reconfigure(encoding="utf-8")
logging.basicConfig(stream=sys.stderr, level=logging.WARNING, force=True)
if "RC_JAVA_ACCESS_BRIDGE_DLL" not in os.environ:
    # The JDK that the golden VM's setup unpacked under C:\Tools carries the bridge's client library.
    jdks = sorted(glob.glob(r"C:\Tools\jdk-21*"))
    if jdks:
        os.environ["RC_JAVA_ACCESS_BRIDGE_DLL"] = os.path.join(jdks[-1], "bin", "windowsaccessbridge-64.dll")

from JABWrapper.context_tree import ContextTree  # noqa: E402
from JABWrapper.jab_wrapper import JavaAccessBridgeWrapper  # noqa: E402

WINDOW_TITLE = os.environ.get("POSATO_WINDOW_TITLE", "Posato")
MISSING = 2


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


KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
VIRTUAL_KEYS = {
    "return": 0x0D, "enter": 0x0D, "tab": 0x09, "escape": 0x1B, "esc": 0x1B, "backspace": 0x08, "delete": 0x2E,
    "space": 0x20, "left": 0x25, "up": 0x26, "right": 0x27, "down": 0x28, "home": 0x24, "end": 0x23,
    "pageup": 0x21, "pagedown": 0x22, "ctrl": 0x11, "control": 0x11, "shift": 0x10, "alt": 0x12, "win": 0x5B,
    "f4": 0x73,
}


def send(event_key, flags, virtual=0):
    event = INPUT(type=1)
    event.ki = KEYBDINPUT(virtual, event_key, flags, 0, 0)
    ctypes.windll.user32.SendInput(1, byref(event), ctypes.sizeof(INPUT))


def bring_forward():
    user32 = ctypes.windll.user32
    hwnd = user32.FindWindowW(None, WINDOW_TITLE)
    if not hwnd:
        raise LookupError(f"no window titled {WINDOW_TITLE!r}")
    if user32.GetForegroundWindow() != hwnd:
        # Any input event lets this process move the foreground. Not Alt: releasing it opens the window menu,
        # which swallows the first typed character.
        user32.keybd_event(0, 0, 0, 0)
        user32.SetForegroundWindow(hwnd)
        time.sleep(0.3)


def send_text(text):
    """Compose's editable text refuses setTextContents, so text goes in as a keyboard's Unicode input."""
    bring_forward()
    for ch in text:
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            send(ord(ch), flags)
        time.sleep(0.02)


def send_combo(combo):
    bring_forward()
    names = [part.strip().lower() for part in combo.split("+")]
    codes = []
    for name in names:
        if name in VIRTUAL_KEYS:
            codes.append(VIRTUAL_KEYS[name])
        elif len(name) == 1 and name.isalnum():
            codes.append(ord(name.upper()))
        else:
            raise LookupError(f"unknown key {name!r}")
    for code in codes:
        send(0, 0, code)
    for code in reversed(codes):
        send(0, KEYEVENTF_KEYUP, code)


def text_of(node):
    info = node.context_info
    if not info.accessibleText:
        return None
    try:
        return node.text.items.sentence
    except Exception:  # noqa: BLE001 - a text interface that does not answer reads as no text
        return None


def describe(node, path):
    info = node.context_info
    return {
        "role": info.role,
        "name": info.name or None,
        "description": info.description or None,
        "text": text_of(node),
        "states": [state for state in (info.states or "").split(",") if state],
        "bounds": [info.x, info.y, info.width, info.height],
        "actions": node.get_actions() if info.accessibleAction else [],
        "path": path,
        "children": [describe(child, f"{path}.{index}" if path else str(index)) for index, child in enumerate(node.children)],
    }


def window_title(jab):
    """The window to read: Posato's main window, or the topmost visible window of its process that takes input.

    A modal dialog, such as Swing's "Choose apps", disables the main window while it is open, so the reader and its
    presses follow the dialog, as a person's clicks would.
    """
    windows = jab.get_windows()
    main = next((window for window in windows if window.title == WINDOW_TITLE), None)
    if main is None:
        return WINDOW_TITLE
    user32 = ctypes.windll.user32
    for window in windows:  # EnumWindows lists top-level windows from the top of the z-order
        if window.pid == main.pid and window.title and user32.IsWindowVisible(window.hwnd) and user32.IsWindowEnabled(window.hwnd):
            return window.title
    return WINDOW_TITLE


def tree(jab):
    title = window_title(jab)
    try:
        jab.switch_window_by_title(re.escape(title) + "$")
    except Exception as missing:  # noqa: BLE001 - the wrapper raises a bare Exception for a missing window
        raise LookupError(f"no Java window titled {title!r}: {missing}") from missing
    return ContextTree(jab).root


def at(root, path):
    node = root
    for part in [p for p in path.split(".") if p]:
        index = int(part)
        if index >= len(node.children):
            raise LookupError(f"no element at {path!r}")
        node = node.children[index]
    return node


def main():
    arguments = sys.argv[1:]
    if arguments == ["-"]:
        # Text with quotes or non-ASCII characters passes the guest agent's command line intact only this way.
        arguments = json.loads(sys.stdin.buffer.read().decode("utf-8"))
    command = arguments[0] if arguments else "dump"
    if command == "type":
        send_text(arguments[1])
        return
    if command == "key":
        send_combo(arguments[1])
        return
    jab = start_bridge()
    root = tree(jab)
    if command == "dump":
        print(json.dumps(describe(root, "")))
        return
    path = arguments[1]
    node = at(root, path)
    expected = arguments[2] if len(arguments) > 2 else None
    # The tree can change between the read that found the element and this press; a different name means it did.
    if expected is not None and (node.context_info.name or node.context_info.description or "") != expected:
        raise LookupError(f"the element at {path!r} is now {node.context_info.name!r}, not {expected!r}")
    if command == "press":
        actions = node.get_actions() if node.context_info.accessibleAction else []
        if not actions:
            raise LookupError(f"the element at {path!r} has no action")
        if "click" in [action.lower() for action in actions]:
            node.click()
        else:
            node.do_action(actions[0])
    elif command == "focus":
        if node.context_info.accessibleAction:
            node.click()
        node.request_focus()
    else:
        raise ValueError(f"unknown command {command!r}")


if __name__ == "__main__":
    try:
        main()
    except LookupError as missing:
        print(str(missing), file=sys.stderr)
        sys.exit(MISSING)
    except Exception as failure:  # noqa: BLE001 - every other failure is reported with its type and exits 1
        print(f"{type(failure).__name__}: {failure}", file=sys.stderr)
        sys.exit(1)
