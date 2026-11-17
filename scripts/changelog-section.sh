#!/usr/bin/env bash
# Prints the body of one CHANGELOG.md section, e.g. "0.7.0" or "Unreleased"; empty if missing.
# Usage: scripts/changelog-section.sh <version|Unreleased>
set -euo pipefail
cd "$(dirname "$0")/.."
awk -v want="$1" '
  /^## \[/ { inside = index($0, "## [" want "]") == 1; next }
  inside
' CHANGELOG.md | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
