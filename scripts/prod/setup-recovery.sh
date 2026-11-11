#!/usr/bin/env bash
set -euo pipefail

context='k3d-shipflow-prod'
prod_ns='shipflow-prod'
recovery_ns='shipflow-recovery'
secret_names=(
  shipflow-backup-keys
  shipflow-production-postgres-source
  shipflow-production-mongo-source
  production-postgres-ca
  production-mongo-ca
  production-mongo-client
  shipflow-recovery-postgres
  shipflow-recovery-mongo
  shipflow-recovery-backend
)

kubectl --context "$context" get namespace "$prod_ns" >/dev/null
kubectl --context "$context" apply -f k8s/recovery/base/namespace.yaml >/dev/null
for secret_name in "${secret_names[@]}"; do
  if kubectl --context "$context" -n "$recovery_ns" get secret "$secret_name" >/dev/null 2>&1; then
    echo "Secret $secret_name ya existe; no cambié credenciales ni llaves." >&2
    exit 1
  fi
done

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/shipflow-recovery.XXXXXX")
chmod 700 "$work_dir"
trap 'rm -rf "$work_dir"' EXIT

get_secret_value() {
  local secret_name="$1"
  local key="$2"
  kubectl --context "$context" -n "$prod_ns" get secret "$secret_name" -o jsonpath="{.data.$key}" | base64 --decode
}

pg_source_password=$(get_secret_value shipflow-backend-secret POSTGRES_PASSWORD)
mongo_source_password=$(get_secret_value shipflow-mongo-secret MONGO_APP_PASSWORD)
pg_recovery_password=$(openssl rand -hex 32)
pg_recovery_app_password=$(openssl rand -hex 32)
mongo_recovery_root_password=$(openssl rand -hex 32)
mongo_recovery_app_password=$(openssl rand -hex 32)
backup_encryption_key=$(openssl rand -hex 32)
backup_hmac_key=$(openssl rand -hex 32)
recovery_jwt_secret=$(openssl rand -hex 32)

cat > "$work_dir/backup-keys.env" <<EOF_KEYS
BACKUP_ENCRYPTION_KEY=$backup_encryption_key
BACKUP_HMAC_KEY=$backup_hmac_key
EOF_KEYS
cat > "$work_dir/postgres-source.env" <<EOF_PGSOURCE
PGHOST=postgres.shipflow-prod.svc.cluster.local
PGPORT=5432
PGDATABASE=shipflow
PGUSER=shipflow_app
POSTGRES_PASSWORD=$pg_source_password
EOF_PGSOURCE
cat > "$work_dir/mongo-source.env" <<EOF_MONGOSOURCE
MONGO_HOST=mongo.shipflow-prod.svc.cluster.local
MONGO_USER=shipflow_app
MONGO_PASSWORD=$mongo_source_password
EOF_MONGOSOURCE
cat > "$work_dir/recovery-postgres.env" <<EOF_PGRECOVERY
POSTGRES_USER=restore_admin
POSTGRES_PASSWORD=$pg_recovery_password
POSTGRES_APP_PASSWORD=$pg_recovery_app_password
EOF_PGRECOVERY
cat > "$work_dir/recovery-mongo.env" <<EOF_MONGORECOVERY
MONGO_INITDB_ROOT_USERNAME=restore_admin
MONGO_INITDB_ROOT_PASSWORD=$mongo_recovery_root_password
MONGO_APP_PASSWORD=$mongo_recovery_app_password
EOF_MONGORECOVERY
cat > "$work_dir/recovery-backend.env" <<EOF_APP
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=shipflow
POSTGRES_USER=shipflow_app
POSTGRES_PASSWORD=$pg_recovery_app_password
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/shipflow?sslmode=disable
MONGODB_URI=mongodb://shipflow_app:$mongo_recovery_app_password@mongo:27017/shipflow?authSource=shipflow
JWT_SECRET=$recovery_jwt_secret
EOF_APP
chmod 600 "$work_dir"/*.env

kubectl --context "$context" -n "$prod_ns" get secret postgres-tls -o jsonpath='{.data.ca\.crt}' | base64 --decode > "$work_dir/postgres-ca.crt"
kubectl --context "$context" -n "$prod_ns" get secret mongo-tls -o jsonpath='{.data.ca\.crt}' | base64 --decode > "$work_dir/mongo-ca.crt"
kubectl --context "$context" -n "$prod_ns" get secret mongo-client-tls -o jsonpath='{.data.client\.pem}' | base64 --decode > "$work_dir/mongo-client.pem"
chmod 600 "$work_dir"/*

kubectl create secret generic shipflow-backup-keys -n "$recovery_ns" --from-env-file="$work_dir/backup-keys.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-production-postgres-source -n "$recovery_ns" --from-env-file="$work_dir/postgres-source.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-production-mongo-source -n "$recovery_ns" --from-env-file="$work_dir/mongo-source.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic production-postgres-ca -n "$recovery_ns" --from-file=ca.crt="$work_dir/postgres-ca.crt" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic production-mongo-ca -n "$recovery_ns" --from-file=ca.crt="$work_dir/mongo-ca.crt" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic production-mongo-client -n "$recovery_ns" --from-file=client.pem="$work_dir/mongo-client.pem" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-recovery-postgres -n "$recovery_ns" --from-env-file="$work_dir/recovery-postgres.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-recovery-mongo -n "$recovery_ns" --from-env-file="$work_dir/recovery-mongo.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-recovery-backend -n "$recovery_ns" --from-env-file="$work_dir/recovery-backend.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f -

kubectl --context "$context" apply -k k8s/recovery/base

echo 'Recovery namespace and Secrets prepared. Temporary files removed; Secrets were not written to the repository.'
