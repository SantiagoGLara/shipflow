#!/usr/bin/env bash
set -euo pipefail

context='k3d-shipflow-prod'
namespace='shipflow-prod'

prepare_claim() {
  local claim="$1"
  local pod="$2"
  kubectl --context "$context" -n "$namespace" wait \
    --for=jsonpath='{.status.phase}'=Bound "pvc/$claim" --timeout=180s >/dev/null

  local volume node path
  volume=$(kubectl --context "$context" -n "$namespace" get pvc "$claim" -o jsonpath='{.spec.volumeName}')
  node=$(kubectl --context "$context" -n "$namespace" get pod "$pod" -o jsonpath='{.spec.nodeName}')
  path=$(kubectl --context "$context" get pv "$volume" -o jsonpath='{.spec.local.path}')

  if [[ -z "$volume" || -z "$node" || -z "$path" || "$path" != *"_${namespace}_"* ]]; then
    echo "No pude validar el volumen local de $claim; no cambié permisos." >&2
    return 1
  fi

  docker exec "$node" chown 999:999 "$path"
  echo "Preparado $claim en $node para UID/GID 999."
}

prepare_claim postgres-data-postgres-0 postgres-0
prepare_claim mongo-data-mongo-0 mongo-0
