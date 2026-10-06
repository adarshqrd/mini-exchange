#!/usr/bin/env bash
# Replay an audit log (default: the latest) and verify identical output (AC-41).
set -euo pipefail; source "$(dirname "$0")/env.sh"; build
run com.miniexchange.Main replay "${1:-$(ls -t audit/*.log | head -1)}"
