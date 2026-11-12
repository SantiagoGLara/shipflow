# Fallos controlados y comprobación de red en ShipFlow

Guía para el laboratorio local `k3d-shipflow`. Las pruebas de red son de solo lectura. La caída de un Pod de aplicación es reversible: su Deployment debe crearlo de nuevo. Detener el clúster completo interrumpe toda la aplicación y se deja como ejercicio opcional.

## Contextos y estado inicial

El contexto predeterminado de esta máquina puede ser desarrollo. Fija `--context` en los comandos para no confundir sus namespaces y datos.

```bash
kubectl config current-context
kubectl config get-contexts
kubectl --context k3d-shipflow-prod get nodes -o wide
kubectl --context k3d-shipflow-prod -n shipflow-prod get deployment,pods,svc,hpa,pvc
kubectl --context k3d-shipflow-prod -n shipflow-prod get endpointslices
curl -k -sS -o /dev/null -w 'HTTP %{http_code}\n' \
  https://shipflow-prod.localhost:8444/actuator/health
```

Continúa si los nodos están `Ready`, los Deployments están disponibles, las bases están `Running` y la salud HTTPS devuelve 200. En el cluster productivo de laboratorio el backend y frontend tienen dos réplicas.

# ver replicas/deployments prod
```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod get deployments
##cambiar minimo de replicas 
  kubectl --context k3d-shipflow-prod -n shipflow-prod \
  patch hpa backend --type merge -p '{"spec":{"minReplicas":2}}'
```
## Retirar un Pod de backend

1. En una terminal, identifica los dos Pods y el nodo que ocupa cada uno:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod get pods -l app=backend -o wide
```

2. Elige **un nombre concreto** de la lista y elimínalo. No uses `-l app=backend` en `delete pod`: ese selector borraría los dos.

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod delete pod <nombre-de-un-solo-pod>
```

3. Observa cómo el ReplicaSet crea el reemplazo y cuándo vuelve a 2/2:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod get deployment backend -w

kubectl --context k3d-shipflow-prod -n shipflow-prod get pods -l app=backend -w
```

En otra terminal, consulta el endpoint cada segundo durante la prueba:

```bash
while true; do
  date -u '+%H:%M:%S'
  curl -k -sS -o /dev/null -w 'HTTP %{http_code}\n' \
    https://shipflow-prod.localhost:8444/actuator/health || true
  sleep 1
done
```

Detén el ciclo con `Ctrl+C`. Registra el Pod eliminado, nodo, duración hasta recuperar dos réplicas, respuestas HTTP y cualquier error. En la prueba realizada el reemplazo estuvo Ready en unos 39 segundos; el chequeo HTTPS respondió 200 y el Service tuvo temporalmente un endpoint de backend. Eso demuestra la autorreparación de un Pod y que la réplica restante respondió al chequeo; no demuestra que una caída de nodo o de clúster tenga failover.

La misma prueba para frontend usa `get pods -l app=frontend` y elimina un solo nombre. No borres el Deployment: al borrar su controlador Kubernetes no lo recreará.

## Comprobar conexiones permitidas y bloqueadas

El manifiesto [network-checks.yaml](../k8s/prod/diagnostics/network-checks.yaml) crea cuatro Jobs de lectura, sin escribir en las bases:

- Permitidos: los Pods llevan la etiqueta de origen `app=backend`, usan la cuenta de aplicación y validan TLS; PostgreSQL verifica CA y hostname, MongoDB presenta certificado cliente.
- Bloqueados: los Pods usan `app=network-check-denied`, etiqueta que no está autorizada por las NetworkPolicies. Montan los mismos certificados/credenciales para que el resultado mida la red y no una contraseña incorrecta.

Antes de probar, confirma que las bases siguen sanas; así distingues un bloqueo de red de un servicio caído:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  get pods -l 'app in (postgres,mongo)' -o wide
kubectl --context k3d-shipflow-prod -n shipflow-prod get svc postgres mongo
```

Para repetir la prueba, elimina solo los Jobs de diagnóstico de una ejecución anterior y aplica el manifiesto:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod delete job \
  shipflow-netcheck-allowed-pg shipflow-netcheck-allowed-mongo \
  shipflow-netcheck-denied-pg shipflow-netcheck-denied-mongo \
  --ignore-not-found
kubectl --context k3d-shipflow-prod apply \
  -f k8s/prod/diagnostics/network-checks.yaml
```

Espera cada Job:

```bash
for job in \
  shipflow-netcheck-allowed-pg shipflow-netcheck-allowed-mongo \
  shipflow-netcheck-denied-pg shipflow-netcheck-denied-mongo; do
  kubectl --context k3d-shipflow-prod -n shipflow-prod \
    wait --for=condition=complete "job/$job" --timeout=90s || break
done
```

Consulta los resultados:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  logs job/shipflow-netcheck-allowed-pg -c postgres-check
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  logs job/shipflow-netcheck-allowed-mongo -c mongo-check
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  logs job/shipflow-netcheck-denied-pg -c postgres-check
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  logs job/shipflow-netcheck-denied-mongo -c mongo-check
```

Resultado esperado:

- PostgreSQL permitido: la consulta imprime `shipflow_app` y el total de proyectos.
- MongoDB permitido: `mongosh` imprime `1` para el ping.
- PostgreSQL bloqueado: `no response` y `DENIED_EXPECTED`.
- MongoDB bloqueado: error de conexión (por ejemplo `ECONNREFUSED`) y `DENIED_EXPECTED`.

Los cuatro Jobs deben terminar `Complete`; para los bloqueados significa que el probe detectó correctamente que la conexión no pasó. Si aparece `UNEXPECTED_ALLOWED`, no consideres aprobada la prueba: guarda la salida, revisa etiquetas y políticas y no abras reglas nuevas sin localizar primero la causa. Los Jobs expiran automáticamente una hora después de terminar. Para borrarlos antes, borra únicamente los cuatro Jobs anteriores; no borres Services, PVC ni Pods de las bases.

En el ensayo actual los dos flujos permitidos terminaron correctamente y los dos no autorizados fueron bloqueados con los servicios sanos. PostgreSQL respondió a una consulta autenticada con `verify-full`; Mongo respondió al ping usando TLS mutuo.

## Qué significa que caiga un clúster

Los contextos `k3d-shipflow-dev` y `k3d-shipflow-prod` son clústeres separados. Si producción se detiene, desarrollo sigue activo, pero no recibe automáticamente el tráfico ni los datos de producción. Ambos corren sobre el mismo equipo físico; una falla del host afecta a los dos. Los PVC `local-path` siguen ligados a sus nodos.

La prueba siguiente detiene **todos los nodos de producción**. Hazla solo si aceptas que HTTPS y la API productiva queden fuera de servicio hasta arrancar el clúster otra vez:

```bash
k3d cluster stop shipflow-prod

# Esto debe seguir respondiendo: es el otro clúster.
kubectl --context k3d-shipflow-dev get nodes

# Durante la parada, el API de producción no estará disponible.
kubectl --context k3d-shipflow-prod get nodes

k3d cluster start shipflow-prod
kubectl --context k3d-shipflow-prod wait \
  --for=condition=Ready node --all --timeout=180s
kubectl --context k3d-shipflow-prod -n shipflow-prod wait \
  --for=condition=available deployment --all --timeout=300s
kubectl --context k3d-shipflow-prod -n shipflow-prod get pods,pvc
curl -k -sS -o /dev/null -w 'HTTP %{http_code}\n' \
  https://shipflow-prod.localhost:8444/actuator/health
```

`stop`/`start` paran y arrancan los contenedores existentes. No uses `k3d cluster delete`, `kubectl delete pvc`, ni `docker rm` para esta prueba. No simules pérdida de un worker con una base local en él: con `local-path`, mover el Pod no mueve el volumen.

## Evidencia que conviene guardar

Para cada ensayo anota fecha y hora, contexto, recurso afectado, comando, estado inicial, tiempo de recuperación, respuesta del endpoint, logs, estado final de Pods/Services/PVC y resultado esperado/observado. No incluyas contraseñas, tokens ni llaves privadas. Los Jobs de red no revelan credenciales en su salida.


```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod patch hpa backend --type merge -p '{"spec":{"minReplicas":4}}'
```
## Ensayo preliminar de carga

### Medición ejecutada en desarrollo

El 11 de noviembre se hizo un ensayo de solo lectura sobre `GET /api/projects`, con cuenta sintética y cuatro solicitudes concurrentes durante 60 segundos. La petición atravesó el backend y consultó PostgreSQL. Resultados: 5,401 solicitudes, 90.02 solicitudes/s, p50 26.76 ms, p95 98.18 ms, p99 316.15 ms, 5,401 respuestas HTTP 200 y 0% de errores. Esta es una comprobación preliminar; no es el experimento de aceptación del HPA porque desarrollo no tiene HPA y no se midieron cinco minutos de línea base ni diez minutos de carga.

### Ensayo formal de HPA en producción

Antes de enviar carga, confirma backend/frontend listos, PostgreSQL y MongoDB `1/1 Ready`, y HPA en mínimo 2, máximo 6 y CPU objetivo 60%:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod get pods,hpa
kubectl --context k3d-shipflow-prod -n shipflow-prod describe hpa backend
kubectl --context k3d-shipflow-prod top pods
kubectl --context k3d-shipflow-prod -n shipflow-prod get endpointslices
```

Mide cinco minutos de línea base y registra réplicas, CPU, memoria, RPS, p95 y errores. Después ejecuta diez minutos de carga gradual sobre la lectura autenticada `GET /api/projects` (no sobre archivos estáticos), con cuenta sintética y concurrencia anotada. Durante toda la prueba observa, en terminales separadas:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod get hpa backend -w
kubectl --context k3d-shipflow-prod -n shipflow-prod get pods -l app=backend -w
kubectl --context k3d-shipflow-prod top pods -w
```

Al terminar, detén el generador de carga y observa la reducción hacia dos réplicas durante la ventana de estabilización de cinco minutos. Registra el generador y versión, concurrencia, duración, solicitudes, errores, latencias p50/p95/p99, réplicas deseadas/listas, uso de bases y eventos. No escales las bases de datos para compensar saturación.

**Bloqueo observado al preparar la prueba:** el MongoDB de producción quedó `0/1 Ready` con reinicios de contenedor y probes de solo un segundo; el HPA del backend reporta temporalmente mínimo 4, máximo 6 y CPU 1%. No ejecutes el ensayo de estrés en producción hasta recuperar MongoDB y devolver el mínimo HPA a 2. El ensayo preliminar se mantuvo en desarrollo.

### Repetir el ensayo preliminar de desarrollo

El script `scripts/run-readonly-load.py` envía únicamente GET autenticados a `/api/projects`; requiere Python 3. Para abrir la API en un terminal:

```bash
kubectl --context k3d-shipflow-dev -n shipflow-dev \
  port-forward service/backend 18081:8080
```

En otro terminal, crea una cuenta sintética de prueba y guarda su token sin imprimirlo:

```bash
python3 - <<'PY'
import json, secrets, time
with open('/tmp/shipflow-load-user.json', 'w') as f:
    json.dump({'name':'Load Test', 'email':f'load-{int(time.time())}@example.test',
               'password':secrets.token_urlsafe(24)}, f)
PY
chmod 600 /tmp/shipflow-load-user.json
curl -sS -o /tmp/shipflow-load-register.json \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/shipflow-load-user.json \
  http://127.0.0.1:18081/api/auth/register
jq -er '.token' /tmp/shipflow-load-register.json > /tmp/shipflow-load-token
chmod 600 /tmp/shipflow-load-token
```

Mide los recursos antes y durante la carga en una tercera terminal:

```bash
kubectl --context k3d-shipflow-dev -n shipflow-dev top pods
LOAD_SECONDS=60 LOAD_CONCURRENCY=4 \
  TOKEN_FILE=/tmp/shipflow-load-token \
  python3 scripts/run-readonly-load.py
kubectl --context k3d-shipflow-dev -n shipflow-dev top pods
```

El resultado es una línea JSON con RPS, códigos HTTP, errores y p50/p95/p99. Para repetir con la misma cuenta, conserva los archivos temporales con permisos 600 mientras los necesites; al terminar, elimina `/tmp/shipflow-load-user.json`, `/tmp/shipflow-load-register.json` y `/tmp/shipflow-load-token`. El usuario sintético creado permanece en la base dev. El port-forward se cierra con `Ctrl+C`.
