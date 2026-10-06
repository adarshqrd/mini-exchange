#!/usr/bin/env bash
# Spec traceability check: every AC/NFR id in an APPROVED or DONE spec must be
# named by at least one test @DisplayName. Fails (exit 1) on spec drift.
set -euo pipefail
cd "$(dirname "$0")/.."

specs=$(grep -lE '^Status: (APPROVED|DONE)' specs/*/spec.md || true)
if [[ -z "$specs" ]]; then echo "No approved specs found."; exit 0; fi

required=$(grep -hoE '\*\*(AC|NFR)-[0-9]+' $specs | tr -d '*' | sort -u)
covered=$(grep -rhE '@DisplayName\(' src/test 2>/dev/null | grep -oE '(AC|NFR)-[0-9]+' | sort -u || true)

missing=$(comm -23 <(echo "$required") <(echo "$covered"))
total=$(echo "$required" | wc -l | tr -d ' ')

if [[ -n "$missing" ]]; then
  echo "❌ Spec drift: $(echo "$missing" | wc -l | tr -d ' ') of $total requirements have no test:"
  echo "$missing" | sed 's/^/   - /'
  exit 1
fi
echo "✅ All $total requirements in approved specs are covered by tests."
