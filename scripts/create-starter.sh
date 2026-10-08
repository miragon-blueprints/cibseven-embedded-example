#!/usr/bin/env bash
# Turns a checkout of this blueprint into a single-stack starter, in place.
#
#   scripts/create-starter.sh kotlin-gradle [--flat]
#   scripts/create-starter.sh java-maven [--flat]
#
# It deletes the other variant with its workflows and strips every block marked for it from the
# configuration and the docs. With --flat it also moves the build to the repo root and the process
# assets from shared/ into src/main/resources, so the result looks like a plain Spring Boot project.
# Nothing is committed; review the result with `git status`. See docs/starter.md.
set -euo pipefail

usage() { echo "usage: $0 <kotlin-gradle|java-maven> [--flat]" >&2; exit 1; }

case "${1:-}" in
  kotlin-gradle) kept=kotlin-gradle; dropped=java-maven;    dropped_terms='maven|mvnw|checkstyle|mockito' ;;
  java-maven)    kept=java-maven;    dropped=kotlin-gradle; dropped_terms='kotlin|gradle|konsist|mockk' ;;
  *) usage ;;
esac

case "${2:-}" in
  "")     flat=false; dropped_blocks="variant:($dropped|blueprint)" ;;
  --flat) flat=true;  dropped_blocks="(variant:($dropped|blueprint)|layout:shared)" ;;
  *) usage ;;
esac

cd "$(git rev-parse --show-toplevel)"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The working tree has uncommitted changes. Commit or discard them first." >&2
  exit 1
fi

marker='^[[:space:]]*(#|//|<!--) /?(variant|layout):'
resources=service/app/src/main/resources

rewrite() {
  local file=$1 rewritten
  shift
  rewritten=$(mktemp)
  "$@" "$file" > "$rewritten"
  cat "$rewritten" > "$file"
  rm "$rewritten"
}

strip_marked_blocks() {
  awk -v dropped="$dropped_blocks" -v marker="$marker" '
    $0 ~ marker && $0 ~ "/(variant|layout):" { skipping = 0; next }
    $0 ~ marker                              { skipping = ($0 ~ dropped); next }
    !skipping
  ' "$1" | cat -s
}

tracked_text_files() {
  git ls-files -- '*.md' '*.yml' '*.toml' '*.json' '*.kts' '*.xml' .gitignore ':!docs/adr' ':!package-lock.json'
}

move_process_assets_into_the_service() {
  git mv shared/bpmn shared/dmn shared/forms shared/db "$kept/$resources/"
  git rm -q -f shared/README.md
  if [[ $kept == kotlin-gradle ]]; then
    rewrite "$kept/service/app/build.gradle.kts" sed 's|rootDir.resolveSibling("shared")|projectDir.resolve("src/main/resources")|'
  else
    rewrite "$kept/service/app/pom.xml" sed 's|${project.basedir}/../../../shared|${project.basedir}/src/main/resources|'
  fi
  tracked_text_files | while read -r file; do
    rewrite "$file" sed "s|shared/|$resources/|g"
  done
}

append_the_variant_readme_to_the_root_readme() {
  local merged
  merged=$(mktemp)
  {
    sed '/^## License/,$d' README.md
    sed -e '1,/^## /{/^## /!d;}' -e 's|\.\./||g' "$kept/README.md"
    echo
    sed -n '/^## License/,$p' README.md
  } > "$merged"
  cat "$merged" > README.md
  rm "$merged"
  git rm -q -f "$kept/README.md"
}

move_the_build_to_the_repo_root() {
  tracked_text_files | while read -r file; do
    rewrite "$file" sed \
      -e "/working-directory: $kept\$/d" \
      -e "s|cd $kept && ||g" \
      -e "s|\"/$kept\"|\"/\"|g" \
      -e "s|\`$kept/\`|the repo root|g" \
      -e "s|$kept/||g"
  done
  git ls-files -- "$kept" | cut -d/ -f2 | sort -u | while read -r entry; do
    git mv "$kept/$entry" "$entry"
  done
  find "$kept" -type d -empty -delete
}

git rm -r -q "$dropped" ".github/workflows/"*"-$dropped.yml" .github/workflows/starter.yml \
  docs/adr/0013-two-stack-variants-side-by-side-on-main.md docs/starter.md scripts/create-starter.sh

git grep -l -E "$marker" | while read -r file; do
  rewrite "$file" strip_marked_blocks
done
rewrite docs/README.md grep -v '0013-two-stack-variants'

if $flat; then
  move_process_assets_into_the_service
  append_the_variant_readme_to_the_root_readme
  move_the_build_to_the_repo_root
fi
git add -A

echo "Created the $kept starter. Review it with: git status"
if leftovers=$(git grep -n -i -E "$dropped_terms" -- ':!docs/adr' ':!package-lock.json' ":!$kept" ':!service' ':!gradle*' ':!mvnw*' ':!pom.xml' ':!*.kts'); then
  echo
  echo "These lines still mention the removed variant and need a manual look:"
  echo "$leftovers"
fi
