import { useState } from 'react'
import type { DragEvent } from 'react'
import type { Task } from '../types'

interface Props {
  tasks: Task[]
  onChangeStatus: (task: Task, status: Task['status']) => Promise<void>
  onDelete: (id: number) => void
}

const columns: { status: Task['status']; title: string; hint: string }[] = [
  { status: 'PENDING', title: 'Pendiente', hint: 'Por comenzar' },
  { status: 'IN_PROGRESS', title: 'En progreso', hint: 'Trabajo en curso' },
  { status: 'COMPLETED', title: 'Completada', hint: 'Finalizadas' },
]

export default function KanbanBoard({ tasks, onChangeStatus, onDelete }: Props) {
  const [draggedTask, setDraggedTask] = useState<Task | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)

  function handleDragStart(event: DragEvent<HTMLElement>, task: Task) {
    setDraggedTask(task)
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(task.id))
  }

  async function handleDrop(event: DragEvent<HTMLDivElement>, status: Task['status']) {
    event.preventDefault()
    const id = Number(event.dataTransfer.getData('text/plain'))
    const task = tasks.find(item => item.id === id) ?? draggedTask
    setDraggedTask(null)
    if (!task || task.status === status) return
    setSavingId(task.id)
    try {
      await onChangeStatus(task, status)
    } finally {
      setSavingId(null)
    }
  }

  if (tasks.length === 0) {
    return <div className="empty-state"><div className="empty-icon">▦</div><h3>Tu tablero está vacío</h3><p>Crea una tarea para comenzar a organizar el trabajo.</p></div>
  }

  return (
    <div className="kanban-board">
      {columns.map(column => {
        const columnTasks = tasks.filter(task => task.status === column.status)
        return (
          <div
            className={`kanban-column kanban-${column.status.toLowerCase()}`}
            key={column.status}
            onDragOver={event => event.preventDefault()}
            onDrop={event => void handleDrop(event, column.status)}
          >
            <div className="kanban-column-header">
              <div><h3>{column.title}</h3><small>{column.hint}</small></div>
              <span className="kanban-count">{columnTasks.length}</span>
            </div>
            <div className="kanban-cards">
              {columnTasks.map(task => (
                <article
                  className={`kanban-card ${draggedTask?.id === task.id ? 'is-dragging' : ''}`}
                  key={task.id}
                  draggable
                  onDragStart={event => handleDragStart(event, task)}
                  onDragEnd={() => setDraggedTask(null)}
                >
                  <div className="kanban-card-top"><span className="kanban-task-id">TASK-{task.id}</span><span className="drag-handle" title="Arrastra para cambiar de columna">⠿</span></div>
                  <h4>{task.title}</h4>
                  <p>{task.description || 'Sin descripción'}</p>
                  <div className="kanban-card-footer">
                    <select aria-label={`Cambiar estado de ${task.title}`} value={task.status} disabled={savingId === task.id} onChange={event => void onChangeStatus(task, event.target.value as Task['status'])}>
                      {columns.map(option => <option key={option.status} value={option.status}>{option.title}</option>)}
                    </select>
                    <button className="delete-button" onClick={() => onDelete(task.id)}>Eliminar</button>
                  </div>
                  {savingId === task.id && <small className="saving-label">Guardando...</small>}
                </article>
              ))}
              {columnTasks.length === 0 && <div className="kanban-drop-hint">Arrastra una tarea aquí</div>}
            </div>
          </div>
        )
      })}
    </div>
  )
}
