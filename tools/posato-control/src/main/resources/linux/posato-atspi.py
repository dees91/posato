"""Reads and drives Posato's accessibility tree in the Linux clone through AT-SPI.

posato-control pipes this script to `python3 - <command>` in the guest:
  dump              print the Posato application's tree as JSON
  press PATH        perform the element's first action (a button's press)
  focus PATH        give the element keyboard focus (a text field before typing)
PATH is the child indexes from the application, joined with "/".
"""
import json
import sys

import pyatspi


def application():
    for app in pyatspi.Registry.getDesktop(0):
        if app is not None and app.name == "Posato":
            return app
    sys.exit("Posato is not on the accessibility bus.")


def extents(node):
    try:
        box = node.queryComponent().getExtents(pyatspi.DESKTOP_COORDS)
        return [box.x, box.y, box.width, box.height]
    except NotImplementedError:
        return [0, 0, 0, 0]


def text_of(node):
    try:
        text = node.queryText()
        return text.getText(0, text.characterCount)
    except NotImplementedError:
        return None


def describe(node, path, depth):
    states = node.getState()
    children = []
    if depth < 60:
        for index, child in enumerate(node):
            if child is not None:
                children.append(describe(child, f"{path}/{index}" if path else str(index), depth + 1))
    return {
        "role": node.getRoleName(),
        "name": node.name or None,
        "description": node.description or None,
        "text": text_of(node) if node.getRole() in (pyatspi.ROLE_TEXT, pyatspi.ROLE_ENTRY, pyatspi.ROLE_PASSWORD_TEXT) else None,
        "enabled": states.contains(pyatspi.STATE_ENABLED) or states.contains(pyatspi.STATE_SENSITIVE),
        "focused": states.contains(pyatspi.STATE_FOCUSED),
        "frame": extents(node),
        "path": path,
        "children": children,
    }


def resolve(path):
    node = application()
    for index in [int(part) for part in path.split("/") if part]:
        node = node.getChildAtIndex(index)
    return node


def main():
    command = sys.argv[1]
    if command == "dump":
        print(json.dumps(describe(application(), "", 0)))
    elif command == "press":
        action = resolve(sys.argv[2]).queryAction()
        if action.nActions == 0:
            sys.exit("The element has no action.")
        action.doAction(0)
    elif command == "focus":
        resolve(sys.argv[2]).queryComponent().grabFocus()
    else:
        sys.exit(f"Unknown command {command}.")


main()
