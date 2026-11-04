-- Agregar la columna de propietario permitiendo temporalmente valores nulos.
ALTER TABLE projects
ADD COLUMN owner_id BIGINT;

-- Asignar los proyectos existentes al primer usuario registrado.
UPDATE projects
SET owner_id = (SELECT MIN(id) FROM users)
WHERE owner_id IS NULL;

-- A partir de ahora todos los proyectos deben tener propietario.
ALTER TABLE projects
ALTER COLUMN owner_id SET NOT NULL;

-- Relacionar cada proyecto con un usuario.
ALTER TABLE projects
ADD CONSTRAINT fk_projects_owner
FOREIGN KEY (owner_id)
REFERENCES users(id);

-- Índice para consultas de proyectos por propietario.
CREATE INDEX idx_projects_owner_id ON projects(owner_id);