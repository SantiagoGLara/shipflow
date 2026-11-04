import type { Task } from '../types'

interface Props {
  tasks: Task[]
  onChangeStatus: (task: Task, status: Task['status']) => void
  onDelete: (id: number) => void
}

export default function TaskList({
  tasks,
  onChangeStatus,
  onDelete,
}: Props) {
  const statusLabels = {
    PENDING: 'Pendiente',
    IN_PROGRESS: 'En progreso',
    COMPLETED: 'Completada',
  }

  if (tasks.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon">✓</div>
        <h3>No hay tareas todavía</h3>
        <p>Crea una tarea para comenzar a organizar el trabajo.</p>
      </div>
    )
  }

  return (
    <div className="task-list">
      {tasks.map((task) => (
        <div className="task-row" key={task.id}>
          <div className="task-info">
            <strong>{task.title}</strong>
            <small>{task.description || 'Sin descripción'}</small>
          </div>

          <select
            className={`task-status ${task.status.toLowerCase()}`}
            value={task.status}
            onChange={(e) =>
              onChangeStatus(task, e.target.value as Task['status'])
            }
          >
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>

          <button
            className="delete-button"
            onClick={() => onDelete(task.id)}
          >
            Eliminar
          </button>
        </div>
      ))}
    </div>
  )
}