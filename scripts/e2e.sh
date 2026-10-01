#!/usr/bin/env bash
# End-to-end acceptance run against a freshly started application — JVM jar or native executable.
#
#   scripts/e2e.sh jvm       java -jar service/app/build/libs/app-*.jar      (./gradlew :service:app:bootJar)
#   scripts/e2e.sh jvm-aot   same jar with -Dspring.aot.enabled=true         (./gradlew -Pnative :service:app:bootJar)
#   scripts/e2e.sh native    service/app/build/native/nativeCompile/app      (./gradlew -Pnative :service:app:nativeCompile)
#
# Needs a reachable Postgres (docker compose -f stack/docker-compose.yml up -d), plus curl, jq and npx.
# Ports and datasource follow the application's own env vars, so a busy 8080/5432 can be avoided:
#   SERVER_PORT=18080 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bikeleasing scripts/e2e.sh native
set -euo pipefail

variant="${1:?usage: scripts/e2e.sh <jvm|jvm-aot|native>}"
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
export SERVER_PORT="${SERVER_PORT:-8080}"
base_url="http://localhost:${SERVER_PORT}"
engine_rest="${base_url}/engine-rest"
log_file="${E2E_LOG_FILE:-${repo_root}/service/app/build/e2e-${variant}.log}"
readiness_timeout_seconds=120
app_pid=""
failures=0

case "${variant}" in
  jvm)     start_command=(java -jar "$(ls "${repo_root}"/service/app/build/libs/app-*.jar | grep -v plain)") ;;
  jvm-aot) start_command=(java -Dspring.aot.enabled=true -jar "$(ls "${repo_root}"/service/app/build/libs/app-*.jar | grep -v plain)") ;;
  native)  start_command=("${repo_root}/service/app/build/native/nativeCompile/app") ;;
  *)       echo "unknown variant '${variant}'" >&2; exit 2 ;;
esac

now_ms() { perl -MTime::HiRes=time -e 'printf "%d\n", time * 1000'; }

rss_mb() { ps -o rss= -p "${app_pid}" | awk '{ printf "%.0f", $1 / 1024 }'; }

start_app() {
  local started_at
  started_at="$(now_ms)"
  "${start_command[@]}" >> "${log_file}" 2>&1 &
  app_pid=$!
  until curl -sf "${base_url}/actuator/health/readiness" > /dev/null; do
    kill -0 "${app_pid}" 2> /dev/null || { echo "application exited during start-up, see ${log_file}" >&2; exit 1; }
    (( $(now_ms) - started_at < readiness_timeout_seconds * 1000 )) || { echo "application not ready in time" >&2; exit 1; }
    sleep 0.05
  done
  ready_after_ms=$(( $(now_ms) - started_at ))
}

stop_app() {
  [[ -n "${app_pid}" ]] && kill "${app_pid}" 2> /dev/null && wait "${app_pid}" 2> /dev/null || true
  app_pid=""
}
trap stop_app EXIT

check() {
  local description="$1"; shift
  if "$@" > /dev/null 2>&1; then echo "  ✓ ${description}"; else echo "  ✗ ${description}"; failures=$((failures + 1)); fi
}

status_is() { [[ "$(curl -s -o /dev/null -w '%{http_code}' "${@:2}")" == "$1" ]]; }

json_matches() { local filter="$1"; shift; curl -sf "$@" | jq -e "${filter}"; }

poll() {
  local deadline=$(( $(now_ms) + 30000 ))
  until "$@" > /dev/null 2>&1; do
    (( $(now_ms) < deadline )) || return 1
    sleep 0.25
  done
}

latest_deployment_resources() {
  local deployment_id
  deployment_id="$(curl -sf "${engine_rest}/deployment?sortBy=deploymentTime&sortOrder=desc&maxResults=1" | jq -r '.[0].id')"
  echo "${engine_rest}/deployment/${deployment_id}/resources"
}

operational_surface() {
  echo "Operational surface"
  check "actuator health is UP"                 json_matches '.status == "UP"' "${base_url}/actuator/health"
  check "liveness probe is UP"                  json_matches '.status == "UP"' "${base_url}/actuator/health/liveness"
  check "readiness probe is UP"                 json_matches '.status == "UP"' "${base_url}/actuator/health/readiness"
  check "actuator info carries build info"      json_matches '.build.artifact != null' "${base_url}/actuator/info"
  check "prometheus scrape endpoint responds"   status_is 200 "${base_url}/actuator/prometheus"
  check "OpenAPI spec lists the domain API"     json_matches '.paths["/api/bike-leasing"] != null' "${base_url}/v3/api-docs"
  check "Swagger UI is served"                  status_is 200 "${base_url}/swagger-ui/index.html"
  check "engine REST reports the default engine" json_matches '.[0].name == "default"' "${engine_rest}/engine"
  check "both process definitions are deployed" json_matches '[.[].key] | sort == ["bikeLeasingProcess", "cancelBikeOrder"]' "${engine_rest}/process-definition?latestVersion=true"
  check "the DMN decision is deployed"          json_matches '.[0].key == "checkCreditRating"' "${engine_rest}/decision-definition?latestVersion=true"
  check "both Camunda Forms are deployed"       json_matches '[.[].name] | contains(["clarify-alternative.form", "clarify-return.form"])' "$(latest_deployment_resources)"
  check "bike catalogue is seeded"              json_matches 'length >= 3' "${base_url}/api/bikes"
}

webapps() {
  echo "CIB seven webapps"
  local cookies xsrf
  cookies="$(mktemp)"
  check "Welcome app is served"                 status_is 200 "${base_url}/camunda/app/welcome/default/"
  check "Tasklist app is served"                status_is 200 "${base_url}/camunda/app/tasklist/default/"
  check "Cockpit app is served"                 status_is 200 -c "${cookies}" "${base_url}/camunda/app/cockpit/default/"
  xsrf="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "${cookies}")"
  check "webapp static assets are served"       status_is 200 "${base_url}/camunda/lib/deps.js"
  check "admin can log in to Cockpit"           json_matches '.userId == "admin"' "${base_url}/camunda/api/admin/auth/user/default/login/cockpit" -b "${cookies}" -c "${cookies}" -H "X-XSRF-TOKEN: ${xsrf}" -d 'username=admin&password=admin'
  check "Cockpit plugin API lists definitions"  json_matches '[.[].key] | index("bikeLeasingProcess") != null' "${base_url}/camunda/api/cockpit/plugin/base/default/process-definition/statistics?firstResult=0&maxResults=50" -b "${cookies}"
  check "Tasklist engine API answers"           json_matches '.count >= 0' "${base_url}/camunda/api/engine/engine/default/task/count" -b "${cookies}"
  rm -f "${cookies}"
}

bruno_scenarios() {
  echo "Bruno scenarios (happy path, escalation, abort, not solvent, bike unavailable, incident, list and inbox)"
  if (cd "${repo_root}/bruno" && npx --yes @usebruno/cli@4.0.0 run . --env local -r \
        --env-var "baseUrl=${base_url}" --env-var "engineRest=${engine_rest}") > "${log_file%.log}-bruno.log" 2>&1; then
    echo "  ✓$(grep -E '^│ (Requests|Tests|Assertions)' "${log_file%.log}-bruno.log" | tr -s ' │' ' ' | paste -sd ',' -)"
  else
    echo "  ✗ Bruno run failed, see ${log_file%.log}-bruno.log"; failures=$((failures + 1))
  fi
}

application_field() { curl -sf "${base_url}/api/bike-leasing/$1" | jq -e "$2"; }

user_task_form() {
  echo "User task with a deployed Camunda Form"
  local application_id task_id
  application_id="$(curl -sf -H 'Content-Type: application/json' "${base_url}/api/bike-leasing" \
    -d '{"customerName":"Fiona Form","email":"fiona@example.com","age":40,"monthlyNetIncome":4200.0,"bikeId":"BIKE-OOS","bikeModel":"Mountain Trail 600"}' | jq -r '.applicationId')"
  check "contract is sent"                          poll application_field "${application_id}" '.contractId != null'
  check "contract can be signed"                    status_is 202 -X POST "${base_url}/api/bike-leasing/${application_id}/sign-contract"
  check "out-of-stock bike parks on the user task"  poll json_matches 'length == 1' "${engine_rest}/task?processInstanceBusinessKey=${application_id}&taskDefinitionKey=userTask_clarifyAlternative"
  task_id="$(curl -sf "${engine_rest}/task?processInstanceBusinessKey=${application_id}&taskDefinitionKey=userTask_clarifyAlternative" | jq -r '.[0].id')"
  check "task resolves its deployed Camunda Form"   json_matches '.id == "clarifyAlternativeForm"' "${engine_rest}/task/${task_id}/deployed-form"
  check "alternative can be clarified"             status_is 202 -H 'Content-Type: application/json' -d '{"alternativeFound":true,"bikeId":"BIKE-900","bikeModel":"Gravel Explorer 900"}' "${base_url}/api/bike-leasing/${application_id}/clarify-alternative"
  check "alternative bike gets ordered"             poll application_field "${application_id}" '.status == "ORDERED" and .bikeId == "BIKE-900"'
}

restart_survival() {
  echo "Persistence across a restart"
  local application_id instance_id timer_job_id
  application_id="$(curl -sf -H 'Content-Type: application/json' "${base_url}/api/bike-leasing" \
    -d '{"customerName":"Rita Restart","email":"rita@example.com","age":40,"monthlyNetIncome":4200.0,"bikeId":"BIKE-800","bikeModel":"Carbon Road 800"}' | jq -r '.applicationId')"
  check "application submitted before the restart" poll application_field "${application_id}" '.contractId != null'
  stop_app
  start_app
  check "instance still waits for the signature after the restart" json_matches 'length == 1' "${engine_rest}/process-instance?businessKey=${application_id}"
  check "contract can be signed after the restart"  status_is 202 -X POST "${base_url}/api/bike-leasing/${application_id}/sign-contract"
  check "bike gets ordered"                         poll application_field "${application_id}" '.status == "ORDERED" and .orderId != null'
  check "handover can be reported"                  status_is 202 -X POST "${base_url}/api/bike-leasing/${application_id}/report-handover"
  instance_id="$(curl -sf "${engine_rest}/process-instance?businessKey=${application_id}" | jq -r '.[0].id')"
  check "withdrawal timer is scheduled"             poll json_matches 'length == 1' "${engine_rest}/job?processInstanceId=${instance_id}&activityId=event_withdrawalPeriodElapsed&timers=true"
  timer_job_id="$(curl -sf "${engine_rest}/job?processInstanceId=${instance_id}&activityId=event_withdrawalPeriodElapsed&timers=true" | jq -r '.[0].id')"
  check "withdrawal timer can be fired"             status_is 204 -X POST "${engine_rest}/job/${timer_job_id}/execute"
  check "leasing is active"                         poll application_field "${application_id}" '.status == "ACTIVE"'
}

mkdir -p "$(dirname "${log_file}")"
: > "${log_file}"
echo "== ${variant}: ${start_command[*]}"
start_app
first_ready_ms="${ready_after_ms}"
sleep 5
idle_rss="$(rss_mb)"

operational_surface
webapps
scenarios_started_at="$(now_ms)"
bruno_scenarios
scenarios_ms=$(( $(now_ms) - scenarios_started_at ))
loaded_rss="$(rss_mb)"
user_task_form
restart_survival

echo
echo "== ${variant} summary"
echo "ready after:            ${first_ready_ms} ms (restart: ${ready_after_ms} ms)"
echo "RSS idle / after load:  ${idle_rss} MB / ${loaded_rss} MB"
echo "Bruno scenarios:        ${scenarios_ms} ms"
echo "failed checks:          ${failures}"
[[ "${failures}" -eq 0 ]]
