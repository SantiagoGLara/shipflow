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

## Retirar un Pod de backend

1. En una terminal, identifica los dos Pods y el nodo que ocupa cada uno:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  get pods -l app=backend -o wide
```

2. Elige **un nombre concreto** de la lista y elimínalo. No uses `-l app=backend` en `delete pod`: ese selector borraría los dos.

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  delete pod <nombre-de-un-solo-pod>
```

3. Observa cómo el ReplicaSet crea el reemplazo y cuándo vuelve a 2/2:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-prod \
  get deployment backend -w

kubectl --context k3d-shipflow-prod -n shipflow-prod \
  get pods -l app=backend -w
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
