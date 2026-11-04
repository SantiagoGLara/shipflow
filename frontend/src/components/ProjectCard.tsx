import type { Project } from '../types'

interface Props {
  project: Project
  onSelect: (project: Project) => void
  onDelete: (id: number) => void
}

export default function ProjectCard({
  project,
  onSelect,
  onDelete,
}: Props) {
  return (
    <article className="project-card">
      <div className="project-card-top">
        <div className="project-icon">▱</div>

        <span className={`status ${project.status.toLowerCase()}`}>
          {project.status === 'ACTIVE' ? 'Activo' : 'Archivado'}
        </span>
      </div>

      <h3>{project.name}</h3>

      <p>{project.description || 'Sin descripción disponible.'}</p>

      <div className="project-card-footer">
        <button
          className="text-button"
          onClick={() => onSelect(project)}
        >
          Ver tareas →
        </button>

        <button
          className="delete-button"
          onClick={() => onDelete(project.id)}
          title="Eliminar proyecto"
        >
          Eliminar
        </button>
      </div>
    </article>
  )
}