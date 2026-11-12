
## 1. Aplicación y servicios

| Capa | Tecnología que usa ShipFlow | Responsabilidad |
|---|---|---|
| Interfaz | React 19, TypeScript, Vite; producción estática servida por Nginx | Login, panel, proyectos, tareas y colaboración/revisiones |
| API | Java 21, Spring Boot 4.1.1, Spring MVC REST, Spring Security, JWT, JPA/Hibernate y Flyway | Autenticación, reglas de negocio, persistencia relacional y flujo de revisiones |
| Base relacional | PostgreSQL 17 | Usuarios, proyectos, miembros, tareas, revisiones y Outbox; fuente de verdad transaccional |
| Base documental | MongoDB 7.0 | Eventos de workflow publicados desde Outbox; índice único `eventId` para idempotencia |
| Entrada | Traefik Ingress en K3s | Termina HTTPS y dirige tráfico al Service frontend |
| Orquestación | K3s 1.35.5+k3s1, administrado localmente con k3d | Dos clústeres Kubernetes locales ejecutados en contenedores Docker |

El frontend real utiliza CSS y el backend Java/JPA/Flyway.

## 2. Clústeres y capacidad del host

Los nodos k3d son contenedores Docker. No son máquinas virtuales independientes y comparten el procesador físico del host.

| Entorno | Nodos Kubernetes | Versión | Límites Docker observados |
|---|---|---|---|
| Desarrollo `shipflow-dev` | `k3d-shipflow-dev-server-0` (control plane y workload) | K3s `v1.35.5+k3s1` | Sin límite de memoria ni cuota CPU configurados en Docker; ve aproximadamente 15.4 GiB y 16 CPU del host |
| Producción `shipflow-prod` | `k3d-shipflow-prod-server-0` (control plane), `agent-0` y `agent-1` (workers) | K3s `v1.35.5+k3s1` | 3 GiB de RAM por nodo; CPU compartida, sin cuota CPU Docker |

El host observado es Linux x86_64 con Docker Desktop 29.8.1, 16 CPU lógicas y alrededor de 15.4 GiB disponibles para Docker. `kubectl` reporta 16 CPU por nodo, pero no son CPU dedicadas a cada nodo. No sumarlas como si fueran 48 CPU físicas.

Consumo de nodos en una lectura de `kubectl top nodes`:

| Entorno/nodo | CPU | Memoria |
|---|---:|---:|
| Desarrollo `server-0` | 106m | 1,926 MiB |
| Producción `agent-0` | 75m | 921 MiB |
| Producción `agent-1` | 152m | 1,057 MiB |
| Producción `server-0` | 299m | 1,240 MiB |

### Aclaración sobre las cifras del audio

La transcripción menciona como ejemplo 4 GiB para desarrollo, 1.5 GiB para el control plane y 2.5 GiB por worker. Esas **no son las asignaciones actuales de ShipFlow**. Lo comprobado es: desarrollo sin límite Docker de memoria y producción con 3 GiB en cada nodo (control plane y ambos workers). En una exposición conviene mostrar `docker inspect` y `kubectl get nodes` en vez de repetir las cifras del audio.

## 3. Workloads y almacenamiento por entorno

### Desarrollo — contexto `k3d-shipflow-dev`, namespace `shipflow-dev`

| Recurso | Cantidad actual | Imagen/puerto | Recursos por Pod | Persistencia |
|---|---:|---|---|---|
| Deployment `backend` | 1 réplica | `shipflow-backend:1.0`, HTTP 8080 | request 250m CPU / 512 MiB; limit 1 CPU / 1 GiB | Sin PVC |
| Deployment `frontend` | 1 réplica | `shipflow-frontend:1.4`, HTTP 8080 en Pod; Service 80 | request 100m / 64 MiB; limit 500m / 256 MiB | Sin PVC |
| StatefulSet `postgres` | 1 Pod | `postgres:17`, TCP 5432 | request 250m / 512 MiB; limit 1 CPU / 1 GiB | PVC `postgres-data-postgres-0`, 5 GiB, `local-path` |
| StatefulSet `mongo` | 1 Pod | `mongo:7.0`, TCP 27017 | request 250m / 512 MiB; limit 1 CPU / 1 GiB | PVC `mongo-data-mongo-0`, 5 GiB, `local-path` |

Además hay Services `backend`, `frontend`, `postgres`, `postgres-headless`, `mongo` y `mongo-headless`; un Ingress `frontend` con host `localhost`; ServiceAccounts `default` y `shipflow-app`; ResourceQuota, LimitRange y NetworkPolicies.

El Ingress de desarrollo está declarado, pero el contenedor `k3d-shipflow-dev-serverlb` actualmente no publica los puertos web 80/443 al host. Su API Kubernetes quedó publicada en `0.0.0.0:37545` en esta lectura. El intento desde el host hacia la IP del Ingress también dio timeout. Por ello no se debe asumir que la URL de desarrollo es accesible desde el navegador local sin revisar/publicar el puerto de entrada.

### Producción — contexto `k3d-shipflow-prod`, namespace `shipflow-prod`

| Recurso | Cantidad actual | Imagen/puerto | Recursos por Pod | Persistencia |
|---|---:|---|---|---|
| Deployment `backend` | 2 réplicas actuales | `shipflow-backend:prod-1.1`, HTTP 8080 | request 500m CPU / 768 MiB; limit 1 CPU / 1,536 MiB | Sin PVC |
| HPA `backend` | 2 actuales; mínimo 2, máximo 6 | CPU objetivo 60% | Métrica servida por Metrics Server | Escala el Deployment backend |
| Deployment `frontend` | 2 réplicas | `shipflow-frontend:1.4`, HTTP 8080 en Pod; Service 80 | request 100m / 64 MiB; limit 500m / 256 MiB | Sin PVC |
| StatefulSet `postgres` | 1 Pod | `postgres:17`, TCP 5432, TLS | request 250m / 512 MiB; limit 1 CPU / 1 GiB | PVC `postgres-data-postgres-0`, 5 GiB, `local-path` |
| StatefulSet `mongo` | 1 Pod | `mongo:7.0`, TCP 27017, TLS mutuo | request 250m / 512 MiB; limit 1 CPU / 1 GiB | PVC `mongo-data-mongo-0`, 5 GiB, `local-path` |
| Job `backend-migrate` | Ejecutado y completado | Migraciones Flyway | Solo corre como Job; no por réplica | No aplica |

Servicios de producción: `frontend`, `backend`, `postgres`, `postgres-headless`, `mongo` y `mongo-headless`. La entrada TLS usa Traefik, host `shipflow-prod.localhost`; los puertos del balanceador k3d están publicados como HTTP `127.0.0.1:8080`, HTTPS `127.0.0.1:8444` y API `127.0.0.1:6446`. El certificado web es autofirmado para el laboratorio.

El almacenamiento persistente usa `local-path`, modo `ReadWriteOnce`; los volúmenes son locales al nodo. PostgreSQL y Mongo tienen una sola réplica de base de datos: no son StatefulSets replicados ni proporcionan failover automático de motor. No hay PodDisruptionBudget configurado en producción.

### Recuperación — namespace `shipflow-recovery` en el clúster de producción

| Recurso | Cantidad | Imagen/puerto | Recursos por Pod | Persistencia |
|---|---:|---|---|---|
| Deployment `recovery-backend` | 1 réplica | `shipflow-backend:prod-1.1`, HTTP 8080 | request 250m / 512 MiB; limit 750m / 1 GiB | Sin PVC |
| StatefulSet `recovery-postgres` | 1 Pod | `postgres:17`, TCP 5432 | request 100m / 256 MiB; limit 750m / 768 MiB | PVC nuevo de 1 GiB |
| StatefulSet `recovery-mongo` | 1 Pod | `mongo:7.0`, TCP 27017 | request 100m / 256 MiB; limit 750m / 768 MiB | PVC nuevo de 1 GiB |
| PVC `shipflow-backups` | 1 | Destino independiente de copias cifradas | 5 GiB | `local-path` |
| CronJob `shipflow-db-backup` | Programado diario 02:00 `America/Mexico_City` | PostgreSQL 17 y MongoDB 7.0 | Dos contenedores, cada uno request 100m/128 MiB y limit 500m/512 MiB | Retención configurada de 14 días |
| Jobs de restauración | Jobs de prueba completados | Restauran copias en las bases recovery | Solo namespace recovery | PVC de recuperación, nunca PVC de producción |

Las copias usan cifrado AES-256-CBC con PBKDF2 (200,000 iteraciones), HMAC-SHA256 y checksum SHA-256. El PVC de copias es distinto de los PVC origen, pero `local-path` comparte el mismo equipo físico y no protege ante pérdida del host.

## 4. Límites de namespace y controles de Kubernetes

| Namespace | ResourceQuota principal | LimitRange máximo por contenedor | Perfil |
|---|---|---|---|
| `shipflow-dev` | requests 8 CPU/8 GiB; limits 12 CPU/16 GiB | 8 CPU/8 GiB | ServiceAccount `shipflow-app`; NetworkPolicy default-deny y permisos explícitos |
| `shipflow-prod` | requests 8 CPU/7 GiB; limits 12 CPU/12 GiB | 2 CPU/3 GiB | Pod Security Admission Restricted; default-deny; cuenta de app y cuenta operador de solo lectura |
| `shipflow-recovery` | requests 3 CPU/4 GiB; limits 6 CPU/6 GiB; PVC hasta 10 GiB | 2 CPU/3 GiB | Pod Security Admission Restricted; default-deny; cuentas de backup, restore y app separadas |

Controles que se aplican a las cargas propias: usuario no root, `allowPrivilegeEscalation: false`, capacidades eliminadas, seccomp `RuntimeDefault` y token de ServiceAccount desactivado donde no se necesita. Las cuentas y certificados se guardan en Secrets; sus valores no forman parte de este inventario.

NetworkPolicies permiten DNS; frontend hacia backend; backend hacia PostgreSQL/MongoDB; Ingress Traefik hacia frontend; migración hacia motores; y, desde recovery, respaldos hacia las bases origen y backend/restores hacia las bases recovery.

## 5. Recursos de plataforma compartidos

En ambos clústeres se observan:

- K3s con containerd; k3d ejecuta los nodos como contenedores Docker.
- Traefik Ingress Controller y ServiceLB de K3s.
- CoreDNS para DNS del clúster.
- `local-path-provisioner` y StorageClass predeterminada `local-path`.
- Metrics Server para métricas actuales usadas por el HPA; no hay Prometheus/Grafana desplegados en este inventario.
- Services `kubernetes`, `kube-dns`, `metrics-server` y `traefik` en `kube-system`.

## 6. Configuración, identidades y datos

- ConfigMaps de aplicación/base: `postgres-init` y `mongo-init` en producción; `recovery-postgres-init` en recuperación.
- Secrets de producción: `shipflow-backend-secret`, `shipflow-postgres-secret`, `shipflow-mongo-secret`, `postgres-tls`, `mongo-tls`, `mongo-client-tls`, `shipflow-backend-mongo-tls` y `shipflow-prod-tls`.
- Secrets de desarrollo: `shipflow-backend-secret`, `shipflow-db-secret` y `shipflow-dev-tls`.
- Secrets de recovery incluyen material de cifrado y credenciales/CA origen y destino; no se listan sus valores.
- Los PVC, namespaces, certificados y credenciales de desarrollo, producción y recovery son independientes.
- Las entidades relacionales son usuarios, proyectos, miembros, tareas y revisiones. MongoDB guarda documentos `workflow_events` emitidos mediante Outbox.

## 7. Guion de demostración sugerido por la transcripción

1. Presentar ShipFlow: React/TypeScript → API REST Spring Boot → PostgreSQL y MongoDB, y explicar qué guarda cada motor.
2. Mostrar contextos, nodos, namespaces y diferencias entre una réplica de desarrollo y dos réplicas de frontend/backend en producción.
3. Mostrar Deployments, StatefulSets, Pods, Services, Ingress, PVC, Secrets/ConfigMaps por nombre, HPA, recursos y sondas.
4. Mostrar que los datos están aislados entre los PVC de dev y prod; no compartir credenciales ni volúmenes.
5. Si piden escalar a cuatro: el HPA está configurado de 2 a 6. El estado normal actual es 2. Una orden manual `kubectl scale` puede ser reconciliada por HPA según CPU; para demostrar escalado automático debe observarse `kubectl get hpa -w` con carga y métricas, no solo mostrar la creación manual de Pods.
6. Para el estado actual del host y límites, usar `docker inspect`/`kubectl get nodes`; no usar las cifras distintas que se mencionan como ejemplo en el audio.

## 8. Comandos para refrescar el inventario

```bash
k3d cluster list
kubectl config get-contexts
kubectl --context k3d-shipflow-dev get nodes -o wide
kubectl --context k3d-shipflow-prod get nodes -o wide
kubectl --context k3d-shipflow-dev get all,pvc,ingress,networkpolicy,resourcequota,limitrange -n shipflow-dev
kubectl --context k3d-shipflow-prod get all,pvc,ingress,networkpolicy,resourcequota,limitrange -n shipflow-prod
kubectl --context k3d-shipflow-prod -n shipflow-prod get hpa
kubectl --context k3d-shipflow-prod top nodes
```

No copiar la salida completa de `kubectl get secret -o yaml` al informe; puede exponer credenciales o certificados privados.



```bash
``` 