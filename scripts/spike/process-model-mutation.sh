#!/usr/bin/env bash
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK_DIR="${WORK_DIR:-${TMPDIR:-/tmp}/process-model-mutation}"
RESULTS_DIR="${RESULTS_DIR:-$REPO_ROOT/.context/mutation-spike/e3}"
APP="service/app"
MUTANTS="$APP/build/process-model-mutants"
RESOURCES="$APP/build/resources/main"

rsync -a --delete --exclude build --exclude .gradle --exclude .kotlin "$REPO_ROOT/kotlin-gradle/" "$WORK_DIR/"
cd "$WORK_DIR" || exit 1
./gradlew -q :service:app:processResources :service:app:generateProcessModelMutants || exit 1
mkdir -p "$RESULTS_DIR"
cp "$MUTANTS/manifest.csv" "$RESULTS_DIR/"

restore_original_models() {
  cp -R "$APP/src/main/resources/bpmn" "$APP/src/main/resources/dmn" "$RESOURCES/"
}

run_process_tests() {
  local result_dir="$RESULTS_DIR/$1"
  local started
  started=$(date +%s)
  mkdir -p "$result_dir"
  gtimeout 600 ./gradlew -q :service:app:cleanTest :service:app:test --tests 'io.miragon.blueprint.process.*' \
    -x processResources -x generateBpmnModels > "$result_dir/gradle.log" 2>&1 < /dev/null
  echo $? > "$result_dir/exit-code"
  echo $(( $(date +%s) - started )) > "$result_dir/seconds"
  cp "$APP"/build/test-results/test/*.xml "$result_dir/" 2>/dev/null
}

lint_mutant() {
  (cd "$REPO_ROOT" && npx --no-install bpmnlint "$WORK_DIR/$MUTANTS/$1/$2") > "$RESULTS_DIR/$1/bpmnlint.log" 2>&1 < /dev/null
  echo $? > "$RESULTS_DIR/$1/bpmnlint-exit-code"
}

if [[ -z "${SKIP_CONTROL:-}" ]]; then
  restore_original_models
  cp -R "$MUTANTS/IDENTITY/." "$RESOURCES/"
  run_process_tests CONTROL
fi

while IFS=';' read -r -u 3 id operator resource site; do
  if [[ -n "${ONLY:-}" && " $ONLY " != *" $id "* ]]; then continue; fi
  if [[ -n "${SHARD:-}" ]] && (( 10#${id#M} % ${SHARDS:-1} != SHARD )); then continue; fi
  echo "$(date +%T) $id $operator $resource $site"
  restore_original_models
  cp "$MUTANTS/$id/$resource" "$RESOURCES/$resource"
  run_process_tests "$id"
  if [[ "$resource" == *.bpmn ]]; then lint_mutant "$id" "$resource"; fi
done 3< <(tail -n +2 "$MUTANTS/manifest.csv")

restore_original_models
python3 "$REPO_ROOT/scripts/spike/classify-process-model-mutants.py" "$RESULTS_DIR"
