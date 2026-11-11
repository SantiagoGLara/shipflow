# Producción de laboratorio (k3d)

## Topología y capacidad

- Contexto: `k3d-shipflow-prod` (independiente de `k3d-shipflow-dev`).
- 1 nodo control plane y 2 workers; todos los nodos están en el mismo equipo físico, por lo que una falla del equipo detiene ambos ambientes.
- Límites de memoria: 3 GiB por nodo (9 GiB máximo agregado). El PDF propone como punto inicial 4 GiB para el control plane y 8 GiB por worker; ese perfil supera el límite de 15.37 GiB de Docker en este equipo. Se eligieron límites menores para el laboratorio y deberán revisarse con mediciones de carga.
- Los nodos comparten las 16 CPU lógicas del host; no se aplicaron cuotas de CPU por nodo.
- Puertos locales del balanceador: HTTP `127.0.0.1:8080`, HTTPS `127.0.0.1:8444` y API `127.0.0.1:6446`.
- StorageClass disponible: `local-path`; sus volúmenes son locales al nodo y no sobreviven la pérdida de ese nodo sin recuperación desde respaldo.

## Reconstrucción desde un equipo limpio

Este comando describe la topología. Elimina o recrea recursos solo si se ejecuta después de una decisión explícita; no volver a ejecutarlo sobre el clúster existente.

```bash
k3d cluster create shipflow-prod \
  --servers 1 --agents 2 \
  --servers-memory 3g --agents-memory 3g \
  --api-port 127.0.0.1:6446 \
  --port 127.0.0.1:8080:80@loadbalancer \
  --port 127.0.0.1:8444:443@loadbalancer \
  --k3s-arg "--secrets-encryption@server:0"
```

En el clúster actual se habilitó y verificó el cifrado de Secrets con `k3s secrets-encrypt status`; el estado terminó en `Enabled` y `reencrypt_finished`. La rotación de claves y el reinicio requerido deben repetirse solo mediante el procedimiento documentado de K3s.

## Base declarativa

En un clúster de producción nuevo, ejecutar en este orden desde la raíz del repo. El cifrado de Secrets de K3s debe estar habilitado antes de crear credenciales.

```bash
kubectl --context k3d-shipflow-prod apply -f k8s/prod/namespace.yaml
./scripts/prod/create-secrets.sh
kubectl --context k3d-shipflow-prod apply -k k8s/prod
./scripts/prod/prepare-local-pvc-ownership.sh
```

K3s `local-path` crea los directorios como root. El segundo script asigna solamente los directorios de los dos PVC nuevos a UID/GID 999 en los nodos k3d; no entra en los contenedores de base ni cambia archivos de datos. Los pods mantienen PSA Restricted y no usan initContainers privilegiados.

El script de Secrets usa credenciales aleatorias, certificados locales y archivos temporales con permisos privados. Comprueba primero que ninguno de los Secrets exista; si encuentra uno, se detiene sin rotarlo. No lo ejecutes para “actualizar” credenciales de una base ya inicializada. Nunca se reutilizan secretos ni PVC de desarrollo.

Para reconstruir imágenes y desplegar desde cero, después de aplicar la base:

```bash
docker build -t shipflow-backend:prod-1.1 ./backend
# Si se reconstruye el frontend, conservar o actualizar el tag en sus manifiestos.
k3d image import postgres:17 mongo:7.0 shipflow-backend:prod-1.1 shipflow-frontend:1.4 -c shipflow-prod
kubectl --context k3d-shipflow-prod apply -f k8s/prod/backend/migration-job.yaml
kubectl --context k3d-shipflow-prod -n shipflow-prod wait --for=condition=complete job/backend-migrate --timeout=300s
kubectl --context k3d-shipflow-prod apply -k k8s/prod/backend
kubectl --context k3d-shipflow-prod apply -k k8s/prod/frontend
```

HTTPS local: `https://shipflow-prod.localhost:8444`. El certificado del Ingress es autofirmado para el laboratorio y el navegador mostrará una advertencia.


## Bases de producción y aplicación

- `database/` crea StatefulSets PostgreSQL 17 y MongoDB 7.0 con PVC independientes de 5 GiB.
- Ambas bases usan usuario no root (UID 999), `allowPrivilegeEscalation: false`, `capabilities.drop: ALL`, seccomp `RuntimeDefault` y PVC `local-path`.
- Los directorios de estos dos PVC nuevos se asignaron a UID/GID 999 porque el provisionador local de K3s crea directorios con dueño root. Si en este clúster se crea un PVC nuevo para estas imágenes, prepara su directorio local con el UID/GID que exige la imagen antes de iniciar el pod; no uses un initContainer privilegiado bajo PSA Restricted.
- Las cuentas PostgreSQL `shipflow_migrator` y `shipflow_app` están separadas. Mongo tiene una cuenta root de inicialización y `shipflow_app` con `readWrite` solo en `shipflow`.
- PostgreSQL requiere TLS con validación de nombre por parte del backend. Mongo requiere TLS mutuo; certificados y contraseñas existen solo en Secrets del namespace.
- Para ejecutar migraciones después de reconstruir la imagen: `kubectl --context k3d-shipflow-prod apply -f k8s/prod/backend/migration-job.yaml` y esperar `job/backend-migrate` en estado Complete. El Deployment backend lleva `SPRING_FLYWAY_ENABLED=false`; las migraciones no se ejecutan al escalar réplicas.
- El HPA del backend usa CPU objetivo 60%, mínimo 2 y máximo 6 pods. El frontend tiene 2 pods.
- HTTPS de producción: `https://shipflow-prod.localhost:8444` con certificado autofirmado de laboratorio; el puerto/host son distintos de dev.
- Para aplicar backend y frontend una vez completado el Job: `kubectl --context k3d-shipflow-prod apply -k k8s/prod/backend` y `kubectl --context k3d-shipflow-prod apply -k k8s/prod/frontend`.
