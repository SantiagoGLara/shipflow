cat > README.md <<'EOF'
# ShipFlow

Plataforma web para la gestión de entregas de software.

## Tecnologías

- Java 21
- Spring Boot
- React y TypeScript
- PostgreSQL
- MongoDB
- Docker
- Kubernetes

## Arquitectura

ShipFlow utilizará un backend modular, una interfaz web y dos bases
de datos con responsabilidades diferenciadas.

PostgreSQL será la fuente de verdad de las operaciones de negocio.
MongoDB almacenará el historial de eventos y los detalles técnicos
de pruebas, despliegues e incidentes.
EOF