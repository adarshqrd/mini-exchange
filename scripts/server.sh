#!/usr/bin/env bash
# Start the exchange on port 9878 (Ctrl+C to stop).
set -euo pipefail; source "$(dirname "$0")/env.sh"; build
run com.miniexchange.Main "$@"
