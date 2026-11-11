#!/usr/bin/env bash
set -euo pipefail

context='k3d-shipflow-prod'
namespace='shipflow-prod'
secret_names=(
  shipflow-postgres-secret
  shipflow-mongo-secret
  postgres-tls
  mongo-tls
  mongo-client-tls
  shipflow-backend-secret
  shipflow-backend-mongo-tls
  shipflow-prod-tls
)

kubectl --context "$context" get namespace "$namespace" >/dev/null
for secret_name in "${secret_names[@]}"; do
  if kubectl --context "$context" -n "$namespace" get secret "$secret_name" >/dev/null 2>&1; then
    echo "Secret $secret_name ya existe; no se modificó ninguno. Usa este script solo en un namespace de producción nuevo." >&2
    exit 1
  fi
done

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/shipflow-prod-secrets.XXXXXX")
chmod 700 "$work_dir"
trap 'rm -rf "$work_dir"' EXIT

pg_migrator_password=$(openssl rand -hex 32)
pg_app_password=$(openssl rand -hex 32)
mongo_root_password=$(openssl rand -hex 32)
mongo_app_password=$(openssl rand -hex 32)
jwt_secret=$(openssl rand -hex 32)

cat > "$work_dir/postgres.env" <<EOF_PG
POSTGRES_USER=shipflow_migrator
POSTGRES_PASSWORD=$pg_migrator_password
POSTGRES_APP_PASSWORD=$pg_app_password
EOF_PG
cat > "$work_dir/mongo.env" <<EOF_MONGO
MONGO_INITDB_ROOT_USERNAME=shipflow_root
MONGO_INITDB_ROOT_PASSWORD=$mongo_root_password
MONGO_APP_PASSWORD=$mongo_app_password
EOF_MONGO
chmod 600 "$work_dir"/*.env

openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout "$work_dir/postgres.key" -out "$work_dir/postgres.crt" \
  -subj '/CN=postgres.shipflow-prod.svc' \
  -addext 'subjectAltName=DNS:postgres,DNS:postgres.shipflow-prod.svc,DNS:postgres.shipflow-prod.svc.cluster.local' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,digitalSignature,keyCertSign,keyEncipherment' \
  -addext 'extendedKeyUsage=serverAuth' >/dev/null 2>&1

openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout "$work_dir/mongo-ca.key" -out "$work_dir/mongo-ca.crt" \
  -subj '/CN=shipflow-prod-mongo-ca' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' >/dev/null 2>&1
printf '%s\n' \
  'basicConstraints=critical,CA:FALSE' \
  'keyUsage=critical,digitalSignature,keyEncipherment' \
  'extendedKeyUsage=serverAuth' \
  'subjectAltName=DNS:mongo,DNS:mongo.shipflow-prod.svc,DNS:mongo.shipflow-prod.svc.cluster.local' \
  > "$work_dir/mongo-server.ext"
openssl req -new -newkey rsa:2048 -nodes \
  -keyout "$work_dir/mongo-server.key" -out "$work_dir/mongo-server.csr" \
  -subj '/CN=mongo.shipflow-prod.svc' >/dev/null 2>&1
openssl x509 -req -in "$work_dir/mongo-server.csr" \
  -CA "$work_dir/mongo-ca.crt" -CAkey "$work_dir/mongo-ca.key" -CAcreateserial \
  -out "$work_dir/mongo-server.crt" -days 3650 -sha256 \
  -extfile "$work_dir/mongo-server.ext" >/dev/null 2>&1
printf '%s\n' \
  'basicConstraints=critical,CA:FALSE' \
  'keyUsage=critical,digitalSignature,keyEncipherment' \
  'extendedKeyUsage=clientAuth' \
  > "$work_dir/mongo-client.ext"
openssl req -new -newkey rsa:2048 -nodes \
  -keyout "$work_dir/mongo-client.key" -out "$work_dir/mongo-client.csr" \
  -subj '/CN=shipflow-prod-mongo-client' >/dev/null 2>&1
openssl x509 -req -in "$work_dir/mongo-client.csr" \
  -CA "$work_dir/mongo-ca.crt" -CAkey "$work_dir/mongo-ca.key" -CAcreateserial \
  -out "$work_dir/mongo-client.crt" -days 3650 -sha256 \
  -extfile "$work_dir/mongo-client.ext" >/dev/null 2>&1
cat "$work_dir/mongo-server.key" "$work_dir/mongo-server.crt" > "$work_dir/mongo-server.pem"
cat "$work_dir/mongo-client.key" "$work_dir/mongo-client.crt" > "$work_dir/mongo-client.pem"
chmod 600 "$work_dir"/*

kubectl create secret generic shipflow-postgres-secret -n "$namespace" \
  --from-env-file="$work_dir/postgres.env" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-mongo-secret -n "$namespace" \
  --from-env-file="$work_dir/mongo.env" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -
kubectl create secret generic postgres-tls -n "$namespace" \
  --from-file=server.crt="$work_dir/postgres.crt" \
  --from-file=server.key="$work_dir/postgres.key" \
  --from-file=ca.crt="$work_dir/postgres.crt" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -
kubectl create secret generic mongo-tls -n "$namespace" \
  --from-file=server.pem="$work_dir/mongo-server.pem" \
  --from-file=ca.crt="$work_dir/mongo-ca.crt" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -
kubectl create secret generic mongo-client-tls -n "$namespace" \
  --from-file=client.pem="$work_dir/mongo-client.pem" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -

keytool -importcert -noprompt -alias shipflow-prod-mongo-ca \
  -file "$work_dir/mongo-ca.crt" -keystore "$work_dir/truststore.p12" \
  -storetype PKCS12 -storepass changeit >/dev/null 2>&1
openssl pkcs12 -export -in "$work_dir/mongo-client.pem" \
  -inkey "$work_dir/mongo-client.pem" -out "$work_dir/client.p12" \
  -name shipflow-mongo-client -passout pass:changeit >/dev/null 2>&1

mongo_app_from_secret=$(kubectl --context "$context" -n "$namespace" get secret shipflow-mongo-secret -o jsonpath='{.data.MONGO_APP_PASSWORD}' | base64 --decode)
cat > "$work_dir/backend.env" <<EOF_BACKEND
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=shipflow
POSTGRES_USER=shipflow_app
POSTGRES_PASSWORD=$pg_app_password
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/shipflow?sslmode=verify-full&sslrootcert=/etc/shipflow/postgres/ca.crt
MONGODB_URI=mongodb://shipflow_app:$mongo_app_from_secret@mongo:27017/shipflow?authSource=shipflow&tls=true
JWT_SECRET=$jwt_secret
JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/etc/shipflow/mongo/truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.keyStore=/etc/shipflow/mongo/client.p12 -Djavax.net.ssl.keyStoreType=PKCS12 -Djavax.net.ssl.keyStorePassword=changeit
EOF_BACKEND
chmod 600 "$work_dir/backend.env"
kubectl create secret generic shipflow-backend-secret -n "$namespace" \
  --from-env-file="$work_dir/backend.env" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -
kubectl create secret generic shipflow-backend-mongo-tls -n "$namespace" \
  --from-file=truststore.p12="$work_dir/truststore.p12" \
  --from-file=client.p12="$work_dir/client.p12" --dry-run=client -o yaml |
  kubectl --context "$context" apply -f -

openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout "$work_dir/ingress.key" -out "$work_dir/ingress.crt" \
  -subj '/CN=shipflow-prod.localhost' \
  -addext 'subjectAltName=DNS:shipflow-prod.localhost' \
  -addext 'basicConstraints=critical,CA:FALSE' \
  -addext 'keyUsage=critical,digitalSignature,keyEncipherment' \
  -addext 'extendedKeyUsage=serverAuth' >/dev/null 2>&1
chmod 600 "$work_dir/ingress.key" "$work_dir/ingress.crt"
kubectl create secret tls shipflow-prod-tls -n "$namespace" \
  --cert="$work_dir/ingress.crt" --key="$work_dir/ingress.key" \
  --dry-run=client -o yaml | kubectl --context "$context" apply -f -

echo 'Production Secrets created. Temporary keys and environment files removed.'
