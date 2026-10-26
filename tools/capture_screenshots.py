#!/usr/bin/env python3
"""
Capture the README screenshots from the running app on an emulator/device.

    ANDROID_HOME=/path/to/sdk tools/capture_screenshots.py

The script taps the real Compose UI by accessibility label, launches six demo
pads, visits FX / PATTERN / MIX, records a short stem, opens the APC64 sheet and
then re-captures the wide layout. Requires `adb` (or ANDROID_HOME/platform-tools).
"""

import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "screenshots")
PKG = "com.danimanx.replicantsynth"
ACTIVITY = f"{PKG}/.MainActivity"


def find_adb():
    env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env:
        candidate = os.path.join(env, "platform-tools", "adb")
        if os.path.isfile(candidate):
            return candidate
    return shutil.which("adb") or "adb"


ADB = find_adb()


def run(*args, capture=False, timeout=60):
    result = subprocess.run(
        [ADB, *args],
        capture_output=capture,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0 and not capture:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr!r}")
    return result.stdout if capture else b""


def shell(*args, capture=False, timeout=60):
    return run("shell", *args, capture=capture, timeout=timeout)


def tap(x, y, settle=0.8):
    shell("input", "tap", str(int(x)), str(int(y)))
    time.sleep(settle)


def long_press(x, y, settle=1.2):
    shell("input", "swipe", str(int(x)), str(int(y)), str(int(x)), str(int(y)), "900")
    time.sleep(settle)


def nodes():
    for _ in range(5):
        try:
            shell("uiautomator", "dump", "/sdcard/window.xml")
            raw = shell("cat", "/sdcard/window.xml", capture=True)
            root = ET.fromstring(raw.decode("utf-8", errors="replace"))
            out = []
            for node in root.iter("node"):
                bounds = node.get("bounds") or ""
                match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
                if not match:
                    continue
                x1, y1, x2, y2 = (int(v) for v in match.groups())
                out.append(
                    {
                        "text": node.get("text") or "",
                        "desc": node.get("content-desc") or "",
                        "clickable": node.get("clickable") == "true",
                        "center": ((x1 + x2) / 2, (y1 + y2) / 2),
                        "bounds": (x1, y1, x2, y2),
                    }
                )
            return out
        except Exception:
            time.sleep(1.0)
    return []


def find(prefix=None, text=None, exact=False):
    for node in nodes():
        if prefix and node["desc"].startswith(prefix):
            return node
        if text is not None:
            if (node["text"] == text) or (not exact and text in node["text"]):
                return node
    return None


def tap_label(text, settle=1.0):
    node = find(text=text)
    if node is None:
        print(f"  ! label not found: {text}")
        return False
    tap(*node["center"], settle=settle)
    return True


def tap_desc(prefix, settle=1.0, long=False):
    node = find(prefix=prefix)
    if node is None:
        print(f"  ! descendant not found: {prefix}")
        return False
    if long:
        long_press(*node["center"], settle=settle)
    else:
        tap(*node["center"], settle=settle)
    return True


def shot(name):
    os.makedirs(SHOTS, exist_ok=True)
    path = os.path.join(SHOTS, name)
    with open(path, "wb") as handle:
        result = subprocess.run(
            [ADB, "exec-out", "screencap", "-p"],
            stdout=handle,
            check=False,
            timeout=30,
        )
    if result.returncode != 0:
        raise RuntimeError(f"screencap failed for {name}")
    print(f"  saved {name} ({os.path.getsize(path)} bytes)")


def launch():
    shell("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    shell("am", "force-stop", PKG)
    shell("am", "start", "-n", ACTIVITY)
    for _ in range(60):
        time.sleep(1.0)
        busy = (
            find(text="BUILDING WAVETABLES")
            or find(text="LOADING SAMPLES")
            or find(text="SYNCING 64 PADS")
            or find(text="LOADING DEMO")
        )
        if find(text="Allow"):
            tap_label("Allow")
        if not busy and find(prefix="PAD 01") and find(text="SOUND"):
            print("  UI ready")
            return
    print("  ! UI did not report ready; continuing")


def main():
    print(f"adb: {ADB}")
    shell("wm", "size", "reset")
    shell("wm", "density", "reset")
    shell("settings", "put", "system", "accelerometer_rotation", "0")
    shell("settings", "put", "system", "user_rotation", "0")
    launch()

    # Six independent clips across three scenes.
    for pad in ("PAD 01", "PAD 02", "PAD 03", "PAD 09", "PAD 10", "PAD 17"):
        tap_desc(pad, settle=0.55)
    time.sleep(2.2)
    shot("phone_grid.png")

    # Select the acid pad without stopping it, then inspect its pages.
    tap_desc("PAD 01", settle=0.6, long=True)
    tap_label("FX", settle=1.0)
    shot("phone_fx.png")

    tap_label("PATTERN", settle=1.0)
    shot("phone_pattern.png")

    tap_label("MIX", settle=0.8)
    tap_label("RECORD STEM", settle=0.8)
    time.sleep(2.4)
    shot("phone_mix_recording.png")
    tap_desc("STOP RECORDING", settle=1.0)

    tap_desc("MIDI DEVICE", settle=1.4)
    shot("phone_midi.png")
    if not tap_desc("Close", settle=0.6):
        shell("input", "keyevent", "4")
        time.sleep(0.6)

    # Wide layout: tablet portrait and landscape, same APK.
    tap_label("SOUND", settle=0.4)
    shell("wm", "size", "1600x2560")
    shell("wm", "density", "240")
    time.sleep(2.0)
    shot("tablet_grid.png")

    shell("wm", "size", "2560x1600")
    time.sleep(1.6)
    shot("tablet_landscape.png")

    shell("wm", "size", "reset")
    shell("wm", "density", "reset")
    print("done")


if __name__ == "__main__":
    main()
