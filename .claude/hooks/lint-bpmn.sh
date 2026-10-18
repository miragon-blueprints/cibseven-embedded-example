#!/usr/bin/env bash
set -uo pipefail

edited_file=$(node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{process.stdout.write(JSON.parse(s).tool_input?.file_path??"")}catch{}})')

case "$edited_file" in
  */service/app/src/main/resources/bpmn/*.bpmn | service/app/src/main/resources/bpmn/*.bpmn) ;;
  *) exit 0 ;;
esac

bpmnlint="${CLAUDE_PROJECT_DIR:-.}/node_modules/.bin/bpmnlint"
if [ ! -x "$bpmnlint" ]; then
  echo "bpmnlint is not installed, the model was not linted: run npm ci in the repo root" >&2
  exit 1
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 1
if ! findings=$("$bpmnlint" --max-warnings=0 "$edited_file" 2>&1); then
  printf 'bpmnlint findings in %s:\n%s\n' "$edited_file" "$findings" >&2
  exit 2
fi
