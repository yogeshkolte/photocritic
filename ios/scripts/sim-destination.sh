#!/bin/sh
# Prints the xcodebuild -destination value for the newest available iPhone
# simulator (design file 41).
set -e
id=$(xcrun simctl list devices available --json | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
best = None
for runtime, items in devices.items():
    if "iOS" not in runtime:
        continue
    # e.g. ...SimRuntime.iOS-27-2 or ...SimRuntime.iOS-27-2-iPhone19-4
    parts = []
    for p in runtime.rsplit("iOS-", 1)[-1].split("-"):
        if not p.isdigit():
            break
        parts.append(int(p))
    version = tuple(parts)
    for d in items:
        if d["name"].startswith("iPhone") and (best is None or version > best[0]):
            best = (version, d["udid"])
if best is None:
    sys.exit("no iPhone simulator available")
print(best[1])
')
echo "id=$id"
