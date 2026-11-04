-- Completa la integridad referencial sin modificar migraciones previas.
-- Asegura que cada propietario existente figure como LEAD.
INSERT INTO project_members(project_id,user_id,role)
SELECT p.id,p.owner_id,'LEAD' FROM projects p
ON CONFLICT(project_id,user_id) DO UPDATE SET role='LEAD';

-- La relación compuesta permite garantizar que la tarea asociada a un defecto
-- pertenece al mismo proyecto del defecto.
ALTER TABLE tasks ADD CONSTRAINT uq_tasks_project_id_id UNIQUE(project_id,id);
ALTER TABLE defects ADD CONSTRAINT fk_defects_task_same_project
  FOREIGN KEY(project_id,task_id) REFERENCES tasks(project_id,id) ON DELETE SET NULL (task_id);

-- Las tareas de una release deben pertenecer al proyecto de esa release.
ALTER TABLE release_tasks ADD COLUMN project_id BIGINT;
UPDATE release_tasks rt SET project_id=r.project_id FROM releases r WHERE r.id=rt.release_id;
ALTER TABLE release_tasks ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE release_tasks ADD CONSTRAINT fk_release_tasks_release_project
  FOREIGN KEY(project_id,release_id) REFERENCES releases(project_id,id) ON DELETE CASCADE;
ALTER TABLE release_tasks ADD CONSTRAINT fk_release_tasks_task_project
  FOREIGN KEY(project_id,task_id) REFERENCES tasks(project_id,id) ON DELETE RESTRICT;
ALTER TABLE release_tasks DROP CONSTRAINT IF EXISTS release_tasks_release_id_fkey;
ALTER TABLE release_tasks DROP CONSTRAINT IF EXISTS release_tasks_task_id_fkey;
CREATE INDEX idx_release_tasks_project ON release_tasks(project_id);
