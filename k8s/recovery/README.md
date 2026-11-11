# Copias y recuperación de ShipFlow

## Objetivo de laboratorio

- RPO objetivo: 24 horas (copia diaria).
- RTO objetivo: 30 minutos desde el inicio de la restauración hasta backend listo.
- Copias: PostgreSQL y MongoDB, diariamente a las 02:00 `America/Mexico_City`; retención de 14 días.
- Destino: PVC `shipflow-backups` (5 GiB), separado de los PVC de ambas bases.
- Protección: AES-256-CBC con PBKDF2-HMAC-SHA256 (200,000 iteraciones), HMAC-SHA256 y SHA-256; claves en Secret del namespace `shipflow-recovery`, no en Git. K3s cifra Secrets en etcd.
- Aislamiento: restaurar solo en `shipflow-recovery`, con PVC propios de 1 GiB por motor, acceso de red limitado y usuario no root. Nunca apuntar los comandos de restore a `shipflow-prod`.

`local-path` escribe en el disco del nodo que aloja el PVC. Separar el PVC evita depender del PVC original para restaurar, pero **no protege ante pérdida del equipo o nodo**. Para cubrir esa falla, mover/copiar los artefactos cifrados a almacenamiento externo fuera del host y ensayar su recuperación.

## Preparación inicial

Con el clúster `k3d-shipflow-prod` ya operativo y cifrado de Secrets activo:

```bash
./scripts/prod/setup-recovery.sh
kubectl --context k3d-shipflow-prod -n shipflow-recovery get cronjob,pvc
```

El script crea secretos independientes con permisos privados, aplica cuotas, NetworkPolicies y el CronJob. No muestra ni guarda credenciales en el repositorio. Ejecuta un backup de prueba:

```bash
kubectl --context k3d-shipflow-prod -n shipflow-recovery create job \
  --from=cronjob/shipflow-db-backup shipflow-db-backup-manual-$(date +%s)
kubectl --context k3d-shipflow-prod -n shipflow-recovery get jobs,pods
kubectl --context k3d-shipflow-prod -n shipflow-recovery logs job/<job> -c postgres-backup
kubectl --context k3d-shipflow-prod -n shipflow-recovery logs job/<job> -c mongo-backup
```

Ambos contenedores del mismo pod comparten el identificador de ejecución y cada motor obtiene una copia consistente con su propio mecanismo nativo. Asegúrate de que el Job termine `Complete` y que cada motor reporte verificación correcta antes de considerarlo un par recuperable. La tarea aborta si PostgreSQL tiene eventos Outbox sin publicar; esa comprobación reduce el riesgo de divergencia, pero el par no es una transacción atómica distribuida si hay escrituras concurrentes durante la copia.

## Prueba aislada de restauración

Los manifiestos de `restore/` crean servicios y StatefulSets independientes, una réplica del backend, y un Job de restore. Antes de una nueva prueba:

1. Selecciona un pod de backup `Complete` que tenga ambos archivos de esa ejecución. El identificador es el nombre del pod, no el Job.
2. Cambia `BACKUP_RUN_ID` en `restore/restore-job.yaml` al nombre de ese pod y usa un nombre nuevo para el Job.
3. Aplica las bases y sus PVC nuevos:

```bash
kubectl --context k3d-shipflow-prod apply -f k8s/recovery/restore/databases.yaml
```

El provisionador `local-path` crea directorios propiedad de root. Como las imágenes corren con UID/GID 999 bajo PSA Restricted, prepara únicamente las rutas de los PVC **nuevos** de recuperación, igual que el script `scripts/prod/prepare-local-pvc-ownership.sh` para los PVC de producción; valida namespace, PV y nodo antes de ejecutar `docker exec <nodo> chown 999:999 <ruta>`. Reinicia los pods de recuperación y espera que estén Ready. No cambies la propiedad de PVC de producción durante una prueba.

Después, aplica el Job y espera ambos contenedores:

```bash
kubectl --context k3d-shipflow-prod apply -f k8s/recovery/restore/restore-job.yaml
kubectl --context k3d-shipflow-prod -n shipflow-recovery wait \
  --for=condition=complete job/<restore-job> --timeout=30m
kubectl --context k3d-shipflow-prod -n shipflow-recovery logs job/<restore-job> -c postgres-restore
kubectl --context k3d-shipflow-prod -n shipflow-recovery logs job/<restore-job> -c mongo-restore
kubectl --context k3d-shipflow-prod apply -f k8s/recovery/restore/backend.yaml
kubectl --context k3d-shipflow-prod -n shipflow-recovery rollout status \
  deployment/recovery-backend --timeout=5m
```

El Job verifica SHA-256 y HMAC antes de descifrar; restaura PostgreSQL sin propietarios/permisos exclusivos de producción y restaura MongoDB con índices. Solo escribe a `postgres` y `mongo` del namespace `shipflow-recovery`. Verifica los conteos de tablas, las relaciones, el Outbox y el índice `eventId_1` único. Para probar el flujo completo, usa una cuenta sintética de laboratorio contra `recovery-backend`, nunca datos personales reales.

## Ensayo realizado

- Snapshot coherente: PostgreSQL y MongoDB del pod `shipflow-db-backup-verified-1794362332-x96n2`; generado el 2026-11-11 alrededor de 01:58 UTC. PostgreSQL exportó 28,784 bytes y MongoDB 560 bytes. Ambos archivos fueron descifrados para validar su estructura antes de publicarse en el PVC.
- Conteos guardados: 4 usuarios, 2 proyectos, 2 tareas, 1 miembro, 1 review, 1 evento Outbox publicado y 0 no publicados.
- Restore: ambos motores en PVC nuevos (1 GiB cada uno), y backend recuperado `Ready`. Los mismos conteos y relaciones se observaron después; Mongo devolvió 1 evento y el índice único `eventId_1`.
- Flujo de aplicación: login HTTP 200; listado de proyectos y tareas HTTP 200; la respuesta incluyó el proyecto sintético id 2 y su tarea id 2.
- Duración medida del backup verificado: 10 s de Job. PostgreSQL restore: 10 s de Job (restauración dentro del contenedor: 6 s). Mongo restore: 2 s dentro del contenedor. La recuperación completa con backend Ready quedó por debajo de 5 minutos, dentro del RTO objetivo de 30 minutos.
- RPO observado en este ensayo: la instantánea contenía todos los registros sintéticos creados antes de la copia; la copia se hizo aproximadamente cuatro minutos después de iniciar los backups. Esto valida ese punto de recuperación y no simula una pérdida real ni demuestra el comportamiento ante una ejecución diaria omitida.

Los pods y PVC de recuperación se dejan disponibles para inspección. Eliminar solo Jobs fallidos, si se desea ordenar el namespace, no elimina respaldos; no borrar PVC de recuperación/backup como parte de una práctica de recuperación.
