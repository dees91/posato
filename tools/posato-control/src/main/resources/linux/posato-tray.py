"""Reads and drives Posato's status icon in the Linux clone over D-Bus, as the desktop's panel does.

posato-control pipes this script to `python3 - <command>` in the guest, with the session bus in the environment:
  show              print the watcher, Posato's item, and its menu as JSON
  activate LABEL    send the menu's "clicked" event to the enabled item whose label is LABEL
  open              call the item's Activate, as a click on the icon does
"""
import json
import sys
import time

from gi.repository import Gio, GLib

WATCHER = "org.kde.StatusNotifierWatcher"
ITEM = "org.kde.StatusNotifierItem"
MENU = "com.canonical.dbusmenu"


def bus():
    return Gio.bus_get_sync(Gio.BusType.SESSION, None)


def call(connection, name, path, interface, method, parameters=None, reply=None):
    return connection.call_sync(name, path, interface, method, parameters, reply, Gio.DBusCallFlags.NONE, 5000, None)


def get(connection, name, path, interface, prop):
    value = call(connection, name, path, "org.freedesktop.DBus.Properties", "Get", GLib.Variant("(ss)", (interface, prop)))
    return value.unpack()[0]


def items(connection):
    try:
        return get(connection, WATCHER, "/StatusNotifierWatcher", WATCHER, "RegisteredStatusNotifierItems")
    except GLib.Error as error:
        sys.exit(json.dumps({"watcher": False, "error": error.message}))


def posato_item(connection):
    for entry in items(connection):
        # An entry is "<bus name>/<object path>" or a bare bus name for the default path.
        name, _, path = entry.partition("/")
        path = "/" + path if path else "/StatusNotifierItem"
        try:
            if get(connection, name, path, ITEM, "Id") == "posato":
                return name, path
        except GLib.Error:
            continue
    return None


def flatten(node, out):
    identifier, props, children = node
    if identifier != 0:
        out.append({
            "id": identifier,
            "label": props.get("label", ""),
            "enabled": props.get("enabled", True),
            "visible": props.get("visible", True),
            "type": props.get("type", "standard"),
        })
    for child in children:
        flatten(child, out)
    return out


def describe(connection, name, path):
    menu_path = get(connection, name, path, ITEM, "Menu")
    layout = call(connection, name, menu_path, MENU, "GetLayout", GLib.Variant("(iias)", (0, -1, [])))
    revision, root = layout.unpack()
    return {
        "watcher": True,
        "item": {"name": name, "path": path, "title": get(connection, name, path, ITEM, "Title"),
                 "status": get(connection, name, path, ITEM, "Status"),
                 "toolTip": get(connection, name, path, ITEM, "ToolTip")[2]},
        "menu": {"path": menu_path, "revision": revision, "items": flatten(root, [])},
    }


def main():
    connection = bus()
    found = posato_item(connection)
    if found is None:
        print(json.dumps({"watcher": True, "item": None}))
        sys.exit(2)
    name, path = found
    command = sys.argv[1] if len(sys.argv) > 1 else "show"
    if command == "show":
        print(json.dumps(describe(connection, name, path)))
    elif command == "open":
        call(connection, name, path, ITEM, "Activate", GLib.Variant("(ii)", (0, 0)))
        print(json.dumps({"opened": True}))
    elif command == "activate":
        state = describe(connection, name, path)
        menu_path = state["menu"]["path"]
        call(connection, name, menu_path, MENU, "AboutToShow", GLib.Variant("(i)", (0,)))
        state = describe(connection, name, path)
        match = [i for i in state["menu"]["items"] if i["label"] == sys.argv[2] and i["enabled"] and i["visible"]]
        if not match:
            sys.exit(json.dumps({"error": "No enabled menu item " + sys.argv[2], "menu": state["menu"]["items"]}))
        call(connection, name, menu_path, MENU, "Event",
             GLib.Variant("(isvu)", (match[0]["id"], "clicked", GLib.Variant("i", 0), int(time.time()))))
        print(json.dumps({"activated": match[0]["label"]}))
    else:
        sys.exit("Unknown command " + command)


if __name__ == "__main__":
    main()
