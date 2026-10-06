#!/usr/bin/env bash
# Run all tests, then the spec traceability check.
set -euo pipefail; source "$(dirname "$0")/env.sh"
mvn -B test | grep -E 'Tests run:|FAIL|ERROR|BUILD|NFR-4'
scripts/spec-check.sh
