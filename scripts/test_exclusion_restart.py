#!/usr/bin/env python3
"""Process-death regression on a disposable Android emulator, using only adb.

This resets Grayout's test-app data. Physical devices are rejected. Install the
debug APK first, then run with --serial emulator-5554. The process under test is
killed with SIGKILL, never force-stopped; force-stop is used only for fixture cleanup.
"""

import argparse
import json
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


PACKAGE = "com.princeyadav.grayout"
EXCLUDED = "com.android.deskclock"
PREFS = "shared_prefs/grayout_prefs.xml"
ENABLED = "accessibility_display_daltonizer_enabled"
MODE = "accessibility_display_daltonizer"
CASES = ("excluded-on", "excluded-off", "outside", "countdown", "screen-off")


class Device:
    def __init__(self, adb, serial):
        self.command = [adb, "-s", serial]
        if not serial.startswith("emulator-") or self.shell("getprop", "ro.kernel.qemu") != "1":
            raise ValueError("Only a disposable emulator is allowed")

    def shell(self, *args, stdin=None, check=True):
        result = subprocess.run(
            self.command + ["shell", shlex.join(args)], input=stdin,
            text=True, capture_output=True, timeout=15, check=check,
        )
        return result.stdout.strip()

    def pid(self):
        return self.shell("pidof", PACKAGE, check=False)

    def gray(self):
        return (self.shell("settings", "get", "secure", ENABLED) == "1" and
                self.shell("settings", "get", "secure", MODE) == "0")

    def state(self):
        xml = self.shell("run-as", PACKAGE, "cat", PREFS, check=False)
        values = {}
        if xml:
            for node in ET.fromstring(xml):
                if node.tag in ("boolean", "int"):
                    values[node.attrib["name"]] = node.attrib["value"]
        return {
            "pid": self.pid(), "gray": self.gray(),
            "active": values.get("excluded_app_active") == "true",
            "restore_gray": values.get("was_grayscale_on_before_exclusion") == "true",
            "interval": int(values.get("enforcement_interval_minutes", "0")),
        }

    def excluded_foreground(self):
        lines = self.shell("dumpsys", "activity", "activities").splitlines()
        return any((EXCLUDED + "/") in line and
                   ("topResumedActivity=" in line or "mResumedActivity:" in line)
                   for line in lines)

    def service_running(self):
        text = self.shell("dumpsys", "activity", "services", PACKAGE)
        return ".service.GrayoutService" in text and "app=ProcessRecord{" in text

    def interactive(self):
        return bool(re.search(r"^\s*mWakefulness=Awake$", self.shell("dumpsys", "power"), re.MULTILINE))

    def enforcement_deadline(self):
        # Match a live scheduled record and its tag, never action names in stats
        # or history. origWhen is the absolute elapsed-realtime requested deadline.
        pattern = (
            r"^\s*ELAPSED_WAKEUP #\d+: Alarm\{[^\n]*origWhen (\d+) "
            r"whenElapsed \d+ " + re.escape(PACKAGE) + r"\}\n"
            r"\s+tag=\*walarm\*:" + re.escape(PACKAGE + ".ENFORCEMENT_TICK") + r"$"
        )
        match = re.search(pattern, self.shell("dumpsys", "alarm"), re.MULTILINE)
        return int(match.group(1)) if match else None

    def prepare(self, was_on, interval):
        self.shell("am", "force-stop", PACKAGE)
        assert self.shell("pm", "clear", PACKAGE) == "Success"
        for permission in ("WRITE_SECURE_SETTINGS", "POST_NOTIFICATIONS"):
            self.shell("pm", "grant", PACKAGE, "android.permission." + permission)
        self.shell("appops", "set", PACKAGE, "GET_USAGE_STATS", "allow")
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        wait_for(self.interactive, "Fixture requires an interactive screen")
        self.shell("input", "keyevent", "KEYCODE_HOME")
        root = ET.Element("map")
        ET.SubElement(root, "int", name="enforcement_interval_minutes", value=str(interval))
        apps = ET.SubElement(root, "set", name="excluded_packages")
        ET.SubElement(apps, "string").text = EXCLUDED
        self.shell("run-as", PACKAGE, "mkdir", "-p", "shared_prefs")
        self.shell("run-as", PACKAGE, "tee", PREFS, stdin=ET.tostring(root, encoding="unicode"))
        self.shell("settings", "put", "secure", MODE, "0" if was_on else "-1")
        self.shell("settings", "put", "secure", ENABLED, "1" if was_on else "0")
        self.shell("am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        wait_for(self.service_running, "Foreground service must be running before leaving Grayout")
        self.shell("am", "start", "-a", "android.intent.action.MAIN",
                   "-c", "android.intent.category.LAUNCHER", "-p", EXCLUDED)
        wait_for(lambda: self.excluded_foreground(), "Excluded Clock app must remain foreground")
        return wait_for(
            lambda: self.state(), "Real detector must persist the exclusion before death",
            lambda state: state["active"] and state["restore_gray"] == was_on and not state["gray"],
        )

    def cleanup(self):
        self.shell("am", "force-stop", PACKAGE, check=False)
        self.shell("settings", "put", "secure", ENABLED, "0", check=False)
        self.shell("settings", "put", "secure", MODE, "-1", check=False)
        self.shell("input", "keyevent", "KEYCODE_WAKEUP", check=False)
        self.shell("input", "keyevent", "KEYCODE_HOME", check=False)


def wait_for(read, message, predicate=bool, seconds=20):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        last = read()
        if predicate(last):
            return last
        time.sleep(0.15)
    raise AssertionError(f"{message}; last={last}")


def run_case(device, case):
    was_on = case not in ("excluded-off", "countdown")
    interval = 5 if case == "countdown" else 0
    result = {"case": case, "restart_mode": "sticky"}
    try:
        result["before"] = device.prepare(was_on, interval)
        old_pid = result["before"]["pid"]
        assert old_pid.isdigit()
        if case in ("outside", "screen-off"):
            # Prevent the old detector from processing the transition. Then kill
            # it for real, testing startup against state it never got to reconcile.
            device.shell("run-as", PACKAGE, "kill", "-STOP", old_pid)
            key = "KEYCODE_HOME" if case == "outside" else "KEYCODE_SLEEP"
            device.shell("input", "keyevent", key)
        # Expire the normal UsageStats window. This is deliberate test pacing.
        time.sleep(12)
        result["screen_interactive_before_death"] = device.interactive()
        assert result["screen_interactive_before_death"] == (case != "screen-off")
        if case not in ("outside", "screen-off"):
            assert device.excluded_foreground(), "Foreground changed before death"
        if case == "countdown":
            result["original_countdown_deadline"] = device.enforcement_deadline()
            assert result["original_countdown_deadline"] is not None, "Fixture requires an existing countdown"
        device.shell("run-as", PACKAGE, "kill", "-9", old_pid)
        samples = []

        def restarted():
            pid = device.pid()
            samples.append(device.gray())
            return pid if pid and pid != old_pid and device.service_running() else None

        result["new_pid"] = wait_for(restarted, "Service must restart in a new process", seconds=45)
        # A live PID is not proof that onStartCommand or the first detector poll ran.
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            samples.append(device.gray())
            time.sleep(0.1)
        result["after_restart"] = device.state()
        assert device.interactive() == (case != "screen-off"), "Screen state changed during restart"
        result["sampled_gray_during_restart"] = any(samples)

        if case in ("outside", "screen-off"):
            result["after_recovery"] = wait_for(
                device.state, "Startup must reconcile the departed/suspended exclusion",
                lambda state: state["gray"] and not state["active"],
            )
        else:
            assert device.excluded_foreground(), "Restart must not change foreground"
            state = result["after_restart"]
            assert state["active"] and state["restore_gray"] == was_on, "Startup lost the restoration target"
            assert not any(samples), "Observed grayscale while the excluded app remained foreground"
            if case == "countdown":
                assert device.enforcement_deadline() == result["original_countdown_deadline"], "Restart changed the surviving countdown"
            device.shell("input", "keyevent", "KEYCODE_HOME")
            result["after_exit"] = wait_for(
                device.state, "Exit must restore the original state and honor a pending countdown",
                lambda state: not state["active"] and state["gray"] == was_on,
            )
            # The state flag clears before the main-thread enforcement callback.
            # Observe the settled result, not just a transient color snapshot.
            deadline = time.monotonic() + 3
            while time.monotonic() < deadline:
                assert device.gray() == was_on, "Delayed callback changed the expected display state"
                if case == "countdown":
                    assert device.enforcement_deadline() == result["original_countdown_deadline"], "Exit changed the surviving countdown"
                time.sleep(0.1)
        assert device.state()["interval"] == interval, "Restart changed the enforcement interval"
        result["passed"] = True
    except Exception as error:
        result.update(passed=False, error=str(error))
    finally:
        device.cleanup()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True, help="Disposable emulator; its Grayout data is reset")
    parser.add_argument("--case", choices=(*CASES, "all"), default="all")
    args = parser.parse_args()
    device = Device(args.adb, args.serial)
    results = []
    for case in CASES if args.case == "all" else (args.case,):
        result = run_case(device, case)
        results.append(result)
        print(json.dumps(result), flush=True)
    return 0 if all(result["passed"] for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
