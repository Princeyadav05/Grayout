#!/usr/bin/env python3
"""Black-box smoke checks for an actual release APK on a disposable emulator.

Resets only Grayout's app data, temporarily changes the emulator clock/timezone,
and exercises real UI, Room persistence, alarms, services and Quick Settings.
No app classes are linked into a test APK or kept alive for instrumentation.
Requires an English emulator with SystemUI (not an ATD image).
"""

import argparse
import datetime as dt
import hashlib
import json
import re
import shlex
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


PACKAGE = "com.princeyadav.grayout"
ENABLED = "accessibility_display_daltonizer_enabled"
MODE = "accessibility_display_daltonizer"


class Device:
    def __init__(self, adb, serial):
        self.command = [adb, "-s", serial]
        if not serial.startswith("emulator-") or self.shell("getprop", "ro.kernel.qemu") != "1":
            raise ValueError("Only a disposable emulator is allowed; Grayout data is reset")
        if self.shell("cmd", "statusbar", "check-support") != "true":
            raise ValueError("Use an emulator with SystemUI; ATD cannot test Quick Settings")

    def adb(self, *args, check=True):
        return subprocess.run(self.command + list(args), text=True, capture_output=True,
                              check=check, timeout=40).stdout.strip()

    def shell(self, *args, check=True):
        return self.adb("shell", shlex.join(args), check=check)

    def launch(self):
        result = self.shell("am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
        assert "Status: ok" in result, result
        return int(re.search(r"TotalTime: (\d+)", result).group(1))

    def nodes(self):
        self.shell("uiautomator", "dump", "/sdcard/grayout-smoke-window.xml")
        nodes = list(ET.fromstring(self.shell("cat", "/sdcard/grayout-smoke-window.xml")).iter("node"))
        self.parents = {child: parent for parent in nodes for child in parent}
        return nodes

    def find(self, value, attribute="text", scroll=False):
        for attempt in range(5 if scroll else 1):
            matches = [n for n in self.nodes() if n.get(attribute) == value]
            if matches:
                return matches[-1]
            if scroll:
                self.shell("input", "swipe", "500", "1500", "500", "500", "250")
        raise AssertionError(f"Missing UI {attribute}={value!r}")

    def tap_node(self, node):
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        assert right > left and bottom > top, node.attrib
        self.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def tap(self, value, attribute="text", scroll=False):
        node = self.find(value, attribute, scroll)
        # Compose exposes text as a child of the interactive semantic node.
        while node.get("clickable") != "true" and node in self.parents:
            node = self.parents[node]
        assert node.get("clickable") == "true", f"No clickable control for {value!r}"
        self.tap_node(node)

    def checked(self, description):
        nodes = self.nodes()
        parents = {child: parent for parent in nodes for child in parent}
        node = next(n for n in nodes if n.get("content-desc") == description)
        while node.get("checkable") != "true":
            node = parents[node]
        return node.get("checked") == "true"

    def text(self, value):
        field = self.find("android.widget.EditText", "class")
        self.tap_node(field)
        self.shell("input", "keyevent", "KEYCODE_MOVE_END")
        self.shell("input", "keyevent", *(["KEYCODE_DEL"] * 80))
        self.shell("input", "text", value.replace(" ", "%s"))
        self.shell("input", "keyevent", "KEYCODE_BACK")

    def gray(self):
        return (self.shell("settings", "get", "secure", ENABLED) == "1" and
                self.shell("settings", "get", "secure", MODE) == "0")

    def service_running(self):
        result = self.shell("dumpsys", "activity", "services", PACKAGE)
        return ".service.GrayoutService" in result and "app=ProcessRecord{" in result

    def live_alarm(self, action):
        return self.alarm_deadline(action) is not None

    def alarm_deadline(self, action):
        # Match a live record and tag, never historical alarm statistics.
        pattern = (r"^\s*(?:RTC|ELAPSED)_WAKEUP #\d+: Alarm\{[^\n]*origWhen (\d+) [^\n]* " + re.escape(PACKAGE) +
                   r"\}\n\s+tag=\*walarm\*:" + re.escape(PACKAGE + "." + action) + r"$")
        match = re.search(pattern, self.shell("dumpsys", "alarm"), re.MULTILINE)
        return int(match.group(1)) if match else None

    def set_time(self, hour, minute=0, second=0):
        instant = dt.datetime(2026, 9, 14, hour, minute, second, tzinfo=dt.timezone.utc)
        self.shell("cmd", "alarm", "set-time", str(int(instant.timestamp() * 1000)))

    def change_hour(self, current_text, hour):
        self.tap(current_text)
        # The native radial picker exposes virtual hour nodes without clickable=true.
        self.tap_node(self.find(str(hour % 12 or 12), "content-desc"))
        self.tap("AM" if hour < 12 else "PM")
        self.tap("OK")
        self.find(f"{hour % 12 or 12}:00 {'AM' if hour < 12 else 'PM'}")

    def add_schedule(self, name, start, end):
        self.tap("+ Add")
        self.text(name)
        if start != 9:
            self.change_hour("9:00 AM", start)
        self.change_hour("5:00 PM", end)
        self.tap("Every day", scroll=True)
        self.tap("Save", scroll=True)
        wait_for(lambda: any(n.get("text") == name for n in self.nodes()), "schedule saved")


def wait_for(read, message, seconds=25):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = read()
        if value:
            return value
        time.sleep(0.25)
    raise AssertionError(message)


def run(args):
    device = Device(args.adb, args.serial)
    apk = Path(args.apk).resolve(strict=True)
    original_zone = device.shell("getprop", "persist.sys.timezone")
    original_auto_time = device.shell("settings", "get", "global", "auto_time")
    original_auto_zone = device.shell("settings", "get", "global", "auto_time_zone")
    original_screen_timeout = device.shell("settings", "get", "system", "screen_off_timeout")
    result = {"apk": str(apk), "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "apk_bytes": apk.stat().st_size,
              "sdk": device.shell("getprop", "ro.build.version.sdk"), "checks": []}

    def passed(name):
        result["checks"].append(name)
        print(json.dumps({"passed": name}), flush=True)

    try:
        device.adb("uninstall", PACKAGE, check=False)
        assert "Success" in device.adb("install", str(apk))
        assert "DEBUGGABLE" not in device.shell("dumpsys", "package", PACKAGE), "Test the release APK"
        device.shell("pm", "grant", PACKAGE, "android.permission.WRITE_SECURE_SETTINGS")
        if int(result["sdk"]) >= 33:
            device.shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
        device.shell("appops", "set", PACKAGE, "GET_USAGE_STATS", "allow")
        device.shell("settings", "put", "global", "auto_time", "0")
        device.shell("settings", "put", "global", "auto_time_zone", "0")
        device.shell("settings", "put", "system", "screen_off_timeout", "1800000")
        device.shell("cmd", "alarm", "set-timezone", "UTC")
        device.shell("input", "keyevent", "KEYCODE_WAKEUP")
        device.shell("wm", "dismiss-keyguard")
        device.shell("settings", "put", "secure", ENABLED, "0")
        device.shell("settings", "put", "secure", MODE, "-1")
        device.adb("logcat", "-c")

        samples = []
        for _ in range(5):
            device.shell("am", "force-stop", PACKAGE)
            samples.append(device.launch())
        result["force_stopped_launch_ms"] = samples
        result["median_launch_ms"] = statistics.median(samples)
        passed("five successful release launches")
        if args.startup_only:
            return result

        device.set_time(8, 55)
        device.tap("Grayscale", "content-desc")
        wait_for(device.gray, "UI toggle must enable grayscale")
        device.tap("Grayscale", "content-desc")
        wait_for(lambda: not device.gray(), "UI toggle must restore color")
        device.tap("Settings")
        device.find("ADB permission", scroll=True)
        device.tap("Home")
        passed("navigation and real display writes")

        device.tap("Schedules")
        device.add_schedule("Morning", 9, 10)
        device.add_schedule("Later", 11, 12)
        device.tap("Morning")
        device.text("Morning edited")
        device.tap("Save", scroll=True)
        wait_for(lambda: any(n.get("text") == "Morning edited" for n in device.nodes()), "edit persisted")
        device.tap("Schedule Morning edited", "content-desc")
        assert not device.checked("Schedule Morning edited"), "disable must change the stored row"
        assert device.alarm_deadline("SCHEDULE_FIRE") == int(
            dt.datetime(2026, 9, 14, 11, tzinfo=dt.timezone.utc).timestamp() * 1000)
        device.tap("Schedule Morning edited", "content-desc")
        assert device.checked("Schedule Morning edited"), "re-enable must change the stored row"
        assert device.alarm_deadline("SCHEDULE_FIRE") == int(
            dt.datetime(2026, 9, 14, 9, tzinfo=dt.timezone.utc).timestamp() * 1000)
        device.shell("am", "force-stop", PACKAGE)
        device.launch()
        device.tap("Schedules")
        device.find("Morning edited")
        device.find("Later")
        device.tap("Home")
        passed("Room create edit toggle and process restart persistence")

        device.set_time(8, 59, 50)
        device.find("9:00 AM")
        assert not device.gray(), "schedule must remain off before its start"
        assert device.live_alarm("SCHEDULE_FIRE"), "timed start needs a real registered alarm"
        wait_for(device.gray, "real schedule start must fire", seconds=25)
        wait_for(lambda: any(n.get("text") == "11:00 AM" for n in device.nodes()), "Home next start must advance")
        device.set_time(9, 59, 55)
        assert device.gray(), "schedule must remain on before its end"
        wait_for(lambda: not device.gray(), "real schedule end must fire", seconds=20)
        passed("real schedule start end and live Home next-start label")

        # UTC 10:00 is inside Later after moving the device zone one hour east.
        device.shell("cmd", "alarm", "set-timezone", "Etc/GMT-1")
        wait_for(device.gray, "timezone change must enter the new local schedule")
        device.shell("cmd", "alarm", "set-timezone", "UTC")
        wait_for(lambda: not device.gray(), "timezone change must close departed schedule")
        passed("manifest timezone receiver enters and closes windows")

        device.tap("EXCLUSIONS", scroll=True)
        device.text("Clock")
        device.tap("Exclude Clock", "content-desc")
        device.shell("input", "keyevent", "KEYCODE_BACK")
        device.tap("Grayscale", "content-desc")
        wait_for(device.gray, "grayscale should start on")
        device.shell("am", "start", "-W", "-a", "android.intent.action.MAIN", "-c",
                     "android.intent.category.LAUNCHER", "-p", "com.android.deskclock")
        wait_for(lambda: not device.gray(), "excluded Clock must stay colored")
        device.shell("input", "keyevent", "KEYCODE_HOME")
        wait_for(device.gray, "leaving Clock must restore grayscale")
        device.launch()
        passed("installed-app picker and foreground exclusion service")

        for name in ("GrayscaleToggleTile", "EnforcementCycleTile"):
            device.shell("cmd", "statusbar", "add-tile", PACKAGE + "/.service." + name)
        device.shell("cmd", "statusbar", "expand-settings")
        time.sleep(2)
        device.shell("cmd", "statusbar", "click-tile", PACKAGE + "/.service.GrayscaleToggleTile")
        wait_for(lambda: not device.gray(), "grayscale Quick Settings tile must toggle")
        device.shell("cmd", "statusbar", "click-tile", PACKAGE + "/.service.EnforcementCycleTile")
        device.shell("cmd", "statusbar", "collapse")
        wait_for(device.service_running, "tile must start foreground enforcement")
        wait_for(lambda: device.live_alarm("ENFORCEMENT_TICK"), "tile must arm enforcement")
        passed("both Quick Settings tiles and interval service")

        if int(result["sdk"]) in (31, 32):
            previous_schedule = device.alarm_deadline("SCHEDULE_FIRE")
            previous_enforcement = device.alarm_deadline("ENFORCEMENT_TICK")
            assert previous_schedule is not None and previous_enforcement is not None
            device.shell("appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "deny")
            assert "deny" in device.shell("appops", "get", PACKAGE, "SCHEDULE_EXACT_ALARM")
            wait_for(lambda: not device.live_alarm("SCHEDULE_FIRE") and
                     not device.live_alarm("ENFORCEMENT_TICK"), "OS revocation must cancel both alarms")
            device.shell("appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "allow")
            wait_for(lambda: device.live_alarm("SCHEDULE_FIRE"), "regrant must rebuild schedule alarm")
            wait_for(lambda: device.live_alarm("ENFORCEMENT_TICK"), "regrant must rebuild enforcement")
            assert device.alarm_deadline("SCHEDULE_FIRE") == previous_schedule
            assert device.alarm_deadline("ENFORCEMENT_TICK") == previous_enforcement
            passed("Android 12 exact-alarm revoke and regrant")

        assert not device.gray(), "enforcement must be observed changing color to gray"
        wait_for(device.gray, "one-minute real enforcement alarm must apply grayscale", seconds=75)
        passed("real enforcement alarm delivery")
        device.launch()
        device.tap("Off")
        device.tap("Schedules")
        device.tap("Morning edited")
        device.tap("Delete schedule", scroll=True)
        device.tap("Delete")
        wait_for(lambda: not any(n.get("text") == "Morning edited" for n in device.nodes()), "delete must persist")
        device.find("Later")
        passed("Room deletion through optimized UI")

        crashes = device.adb("logcat", "-d", "-b", "crash")
        assert PACKAGE not in crashes, crashes
        passed("no Grayout crash log entries")
        return result
    finally:
        device.shell("am", "force-stop", PACKAGE, check=False)
        for name in ("GrayscaleToggleTile", "EnforcementCycleTile"):
            device.shell("cmd", "statusbar", "remove-tile", PACKAGE + "/.service." + name, check=False)
        device.shell("settings", "put", "secure", ENABLED, "0", check=False)
        device.shell("settings", "put", "secure", MODE, "-1", check=False)
        device.shell("cmd", "alarm", "set-timezone", original_zone or "UTC", check=False)
        device.shell("cmd", "alarm", "set-time", str(int(time.time() * 1000)), check=False)
        for key, value in (("auto_time", original_auto_time), ("auto_time_zone", original_auto_zone)):
            if value == "null":
                device.shell("settings", "delete", "global", key, check=False)
            else:
                device.shell("settings", "put", "global", key, value, check=False)
        if original_screen_timeout == "null":
            device.shell("settings", "delete", "system", "screen_off_timeout", check=False)
        else:
            device.shell("settings", "put", "system", "screen_off_timeout", original_screen_timeout, check=False)
        device.shell("input", "keyevent", "KEYCODE_HOME", check=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--startup-only", action="store_true")
    arguments = parser.parse_args()
    report = run(arguments)
    Path(arguments.report).write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)
