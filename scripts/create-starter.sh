#!/usr/bin/env bash
# Turns a checkout of this blueprint into a single-stack starter, in place.
#
#   scripts/create-starter.sh kotlin-gradle
#   scripts/create-starter.sh java-maven
#
# It deletes the other variant with its workflows and strips every block marked for it from the
# configuration and the docs. Nothing is committed; review the result with `git status`.
# See docs/starter.md.
set -euo pipefail

usage() { echo "usage: $0 <kotlin-gradle|java-maven>" >&2; exit 1; }

case "${1:-}" in
  kotlin-gradle) kept=kotlin-gradle; dropped=java-maven;    dropped_terms='maven|mvnw|checkstyle|mockito' ;;
  java-maven)    kept=java-maven;    dropped=kotlin-gradle; dropped_terms='kotlin|gradle|konsist|mockk' ;;
  *) usage ;;
esac

cd "$(git rev-parse --show-toplevel)"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The working tree has uncommitted changes. Commit or discard them first." >&2
  exit 1
fi

marker='^[[:space:]]*(#|<!--) /?variant:'

strip_marked_blocks() {
  local file=$1 stripped
  stripped=$(mktemp)
  awk -v dropped="variant:($dropped|blueprint)" -v marker="$marker" '
    $0 ~ marker && $0 ~ "/variant:" { skipping = 0; next }
    $0 ~ marker                     { skipping = ($0 ~ dropped); next }
    !skipping
  ' "$file" | cat -s > "$stripped"
  cat "$stripped" > "$file"
  rm "$stripped"
}

git rm -r -q "$dropped" ".github/workflows/"*"-$dropped.yml" \
  docs/adr/0013-two-stack-variants-side-by-side-on-main.md docs/starter.md scripts/create-starter.sh

git grep -l -E "$marker" | while read -r file; do
  strip_marked_blocks "$file"
done

adr_index=$(grep -v '0013-two-stack-variants' docs/README.md)
echo "$adr_index" > docs/README.md
git add -A

echo "Created the $kept starter. Review it with: git status"
if leftovers=$(git grep -n -i -E "$dropped_terms" -- ':!docs/adr' ':!package-lock.json' ":!$kept"); then
  echo
  echo "These lines still mention the removed variant and need a manual look:"
  echo "$leftovers"
fi
