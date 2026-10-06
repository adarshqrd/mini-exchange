#!/usr/bin/env bash
# Run the scripted demo against a running server (start scripts/server.sh first).
set -euo pipefail; source "$(dirname "$0")/env.sh"; build
run com.miniexchange.DemoClient "$@"
