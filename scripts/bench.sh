#!/usr/bin/env bash
# Measure in-process engine latency (NFR-4).
set -euo pipefail; source "$(dirname "$0")/env.sh"; build
run com.miniexchange.Latency
