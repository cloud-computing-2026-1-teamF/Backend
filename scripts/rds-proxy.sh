#!/usr/bin/env bash
set -euo pipefail

# Local RDS tunnel for IntelliJ/DataGrip.
# Keeps RDS private and forwards localhost -> ECS task -> RDS through AWS SSM.

PROFILE="${AWS_PROFILE:-teamf-main}"
REGION="${AWS_REGION:-ap-northeast-2}"
CLUSTER="${ECS_CLUSTER:-sanggwonai-dev-cluster}"
SERVICE="${ECS_SERVICE:-app}"
CONTAINER="${ECS_CONTAINER:-app}"
DB_INSTANCE="${DB_INSTANCE:-sanggwonai-dev-postgres}"
PASSWORD_PARAMETER="${DB_PASSWORD_PARAMETER:-/sanggwonai-dev/postgres/password}"
LOCAL_PORT="${LOCAL_PORT:-15432}"

usage() {
  cat <<USAGE
Usage:
  scripts/rds-proxy.sh tunnel      Open localhost:${LOCAL_PORT} -> main RDS tunnel
  scripts/rds-proxy.sh info        Print IntelliJ/DataGrip connection settings
  scripts/rds-proxy.sh password    Print the DB password from SSM Parameter Store

Optional env overrides:
  AWS_PROFILE=${PROFILE} AWS_REGION=${REGION} LOCAL_PORT=${LOCAL_PORT} scripts/rds-proxy.sh tunnel
USAGE
}

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    if [[ "$1" == "session-manager-plugin" ]]; then
      echo "Install on macOS: brew install --cask session-manager-plugin" >&2
    fi
    exit 1
  fi
}

aws_text() {
  aws --profile "$PROFILE" --region "$REGION" "$@" --output text
}

db_host() {
  aws_text rds describe-db-instances \
    --db-instance-identifier "$DB_INSTANCE" \
    --query 'DBInstances[0].Endpoint.Address'
}

db_port() {
  aws_text rds describe-db-instances \
    --db-instance-identifier "$DB_INSTANCE" \
    --query 'DBInstances[0].Endpoint.Port'
}

db_name() {
  aws_text rds describe-db-instances \
    --db-instance-identifier "$DB_INSTANCE" \
    --query 'DBInstances[0].DBName'
}

db_user() {
  aws_text rds describe-db-instances \
    --db-instance-identifier "$DB_INSTANCE" \
    --query 'DBInstances[0].MasterUsername'
}

db_password() {
  aws_text ssm get-parameter \
    --name "$PASSWORD_PARAMETER" \
    --with-decryption \
    --query 'Parameter.Value'
}

running_task_arn() {
  aws_text ecs list-tasks \
    --cluster "$CLUSTER" \
    --service-name "$SERVICE" \
    --desired-status RUNNING \
    --query 'taskArns[0]'
}

container_runtime_id() {
  local task_arn="$1"
  aws_text ecs describe-tasks \
    --cluster "$CLUSTER" \
    --tasks "$task_arn" \
    --query "tasks[0].containers[?name=='${CONTAINER}'].runtimeId | [0]"
}

session_target() {
  local task_arn task_id runtime_id
  task_arn="$(running_task_arn)"
  if [[ -z "$task_arn" || "$task_arn" == "None" ]]; then
    echo "No running ECS task found for ${CLUSTER}/${SERVICE}." >&2
    echo "Deploy/start the backend service first, then retry." >&2
    exit 1
  fi

  task_id="${task_arn##*/}"
  runtime_id="$(container_runtime_id "$task_arn")"
  if [[ -z "$runtime_id" || "$runtime_id" == "None" ]]; then
    echo "Could not resolve ECS runtimeId for container '${CONTAINER}' in task ${task_id}." >&2
    exit 1
  fi

  printf 'ecs:%s_%s_%s' "$CLUSTER" "$task_id" "$runtime_id"
}

print_info() {
  local host port name user
  host="$(db_host)"
  port="$(db_port)"
  name="$(db_name)"
  user="$(db_user)"

  cat <<INFO
Main RDS:
  AWS profile:   ${PROFILE}
  AWS region:    ${REGION}
  DB instance:   ${DB_INSTANCE}
  RDS host:      ${host}
  RDS port:      ${port}
  Database:      ${name}
  Username:      ${user}
  Password SSM:  ${PASSWORD_PARAMETER}

IntelliJ/DataGrip through the local proxy:
  Host:          localhost
  Port:          ${LOCAL_PORT}
  Database:      ${name}
  User:          ${user}
  JDBC URL:      jdbc:postgresql://localhost:${LOCAL_PORT}/${name}

Password:
  scripts/rds-proxy.sh password

Start tunnel:
  scripts/rds-proxy.sh tunnel
INFO
}

open_tunnel() {
  need_cmd session-manager-plugin

  local host port target params
  host="$(db_host)"
  port="$(db_port)"
  target="$(session_target)"
  params="$(printf '{"host":["%s"],"portNumber":["%s"],"localPortNumber":["%s"]}' "$host" "$port" "$LOCAL_PORT")"

  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Local port ${LOCAL_PORT} is already in use. Try LOCAL_PORT=15433 scripts/rds-proxy.sh tunnel" >&2
    exit 1
  fi

  cat <<INFO
Opening RDS proxy...
  localhost:${LOCAL_PORT} -> ${host}:${port}
  ECS target: ${target}

Keep this terminal open while IntelliJ/DataGrip is connected.
Press Ctrl-C to close the tunnel.
INFO

  exec aws --profile "$PROFILE" --region "$REGION" ssm start-session \
    --target "$target" \
    --document-name AWS-StartPortForwardingSessionToRemoteHost \
    --parameters "$params"
}

need_cmd aws

case "${1:-tunnel}" in
  tunnel|start|proxy)
    open_tunnel
    ;;
  info)
    print_info
    ;;
  password)
    db_password
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
