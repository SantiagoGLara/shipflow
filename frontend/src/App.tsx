import { useEffect, useState } from 'react'
import Sidebar from './components/Sidebar'
import ProjectCard from './components/ProjectCard'
import KanbanBoard from './components/KanbanBoard'
import { projectApi, taskApi } from './services/api'
import type { Project, Task } from './types'
import './App.css'

function App() {
  const [projects, setProjects] = useState<Project[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [selectedProject, setSelectedProject] = useState<Project | null>(null)
  const [taskSearch, setTaskSearch] = useState('')
  const [taskStatusFilter, setTaskStatusFilter] = useState<
    'ALL' | Task['status']
  >('ALL')

  const [activeView, setActiveView] = useState('dashboard')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showProjectForm, setShowProjectForm] = useState(false)
  const [showTaskForm, setShowTaskForm] = useState(false)

  const [projectName, setProjectName] = useState('')
  const [projectDescription, setProjectDescription] = useState('')

  const [taskTitle, setTaskTitle] = useState('')
  const [taskDescription, setTaskDescription] = useState('')

  async function loadProjects() {
    try {
      setLoading(true)
      setError('')
      const data = await projectApi.getAll()
      setProjects(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar proyectos')
    } finally {
      setLoading(false)
    }
  }

  async function loadTasks(projectId: number) {
    try {
      const data = await taskApi.getByProject(projectId)
      setTasks(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar tareas')
    }
  }

  useEffect(() => {
    loadProjects()
  }, [])

  async function createProject(e: React.FormEvent) {
    e.preventDefault()

    try {
      await projectApi.create({
        name: projectName,
        description: projectDescription,
        status: 'ACTIVE',
      })

      setProjectName('')
      setProjectDescription('')
      setShowProjectForm(false)
      await loadProjects()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear el proyecto')
    }
  }

  async function deleteProject(id: number) {
    if (!confirm('¿Seguro que deseas eliminar este proyecto?')) return

    try {
      await projectApi.delete(id)

      if (selectedProject?.id === id) {
        setSelectedProject(null)
        setTasks([])
      }

      await loadProjects()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo eliminar el proyecto')
    }
  }

  async function selectProject(project: Project) {
    setSelectedProject(project)
    setTaskSearch('')
    setTaskStatusFilter('ALL')
    setActiveView('tasks')
    await loadTasks(project.id)
  }

  async function createTask(e: React.FormEvent) {
    e.preventDefault()
    if (!selectedProject) return

    try {
      await taskApi.create(selectedProject.id, {
        title: taskTitle,
        description: taskDescription,
        status: 'PENDING',
      })

      setTaskTitle('')
      setTaskDescription('')
      setShowTaskForm(false)
      await loadTasks(selectedProject.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear la tarea')
    }
  }

  async function changeTaskStatus(task: Task, status: Task['status']) {
    try {
      await taskApi.update(task.id, {
        title: task.title,
        description: task.description || '',
        status,
      })

      setTasks(current => current.map(item => item.id === task.id ? { ...item, status } : item))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo actualizar la tarea')
    }
  }

  async function deleteTask(id: number) {
    if (!confirm('¿Eliminar esta tarea?')) return

    try {
      await taskApi.delete(id)

      if (selectedProject) {
        await loadTasks(selectedProject.id)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo eliminar la tarea')
    }
  }

  const activeProjects = projects.filter(p => p.status === 'ACTIVE').length
  const completedTasks = tasks.filter(t => t.status === 'COMPLETED').length

  const normalizedSearch = taskSearch.trim().toLocaleLowerCase()

  const filteredTasks = tasks.filter(task => {
    const matchesText =
      task.title.toLocaleLowerCase().includes(normalizedSearch) ||
      (task.description || '').toLocaleLowerCase().includes(normalizedSearch)

    const matchesStatus =
      taskStatusFilter === 'ALL' ||
      task.status === taskStatusFilter

    return matchesText && matchesStatus
  })

  const filtersAreActive =
    taskSearch.trim() !== '' || taskStatusFilter !== 'ALL'

  return (
    <div className="app-layout">
      <Sidebar
        activeView={activeView === 'tasks' ? 'projects' : activeView}
        onChangeView={(view) => {
          setActiveView(view)
          setSelectedProject(null)
          setTasks([])
        }}
      />

      <main className="main-content">
        <header className="topbar">
          <div>
            <span className="breadcrumb">Workspace / </span>
            <strong>
              {activeView === 'tasks'
                ? selectedProject?.name
                : activeView === 'projects'
                  ? 'Proyectos'
                  : 'Dashboard'}
            </strong>
          </div>

          <div className="topbar-right">
            <span className="connection-indicator"></span>
            API conectada
          </div>
        </header>

        <section className="page-content">
          {error && (
            <div className="error-banner">
              {error}
              <button onClick={() => setError('')}>×</button>
            </div>
          )}

          {activeView === 'dashboard' && (
            <>
              <div className="page-heading">
                <div>
                  <span className="eyebrow">OVERVIEW</span>
                  <h1>Dashboard</h1>
                  <p>Resumen general de tu espacio de trabajo.</p>
                </div>

                <button
                  className="primary-button"
                  onClick={() => setShowProjectForm(true)}
                >
                  + Nuevo proyecto
                </button>
              </div>

              <div className="stats-grid">
                <div className="stat-card">
                  <span>Proyectos totales</span>
                  <strong>{projects.length}</strong>
                  <small>Registrados en ShipFlow</small>
                </div>

                <div className="stat-card">
                  <span>Proyectos activos</span>
                  <strong>{activeProjects}</strong>
                  <small>Actualmente en desarrollo</small>
                </div>

                <div className="stat-card">
                  <span>Tareas del proyecto</span>
                  <strong>{tasks.length}</strong>
                  <small>Selecciona un proyecto para consultar</small>
                </div>

                <div className="stat-card">
                  <span>Tareas completadas</span>
                  <strong>{completedTasks}</strong>
                  <small>Del proyecto seleccionado</small>
                </div>
              </div>

              <div className="section-heading">
                <div>
                  <h2>Proyectos recientes</h2>
                  <p>Accede rápidamente a tus proyectos.</p>
                </div>

                <button
                  className="text-button"
                  onClick={() => setActiveView('projects')}
                >
                  Ver todos →
                </button>
              </div>

              {loading ? (
                <p>Cargando proyectos...</p>
              ) : (
                <div className="projects-grid">
                  {projects.slice(0, 3).map(project => (
                    <ProjectCard
                      key={project.id}
                      project={project}
                      onSelect={selectProject}
                      onDelete={deleteProject}
                    />
                  ))}
                </div>
              )}
            </>
          )}

          {activeView === 'projects' && (
            <>
              <div className="page-heading">
                <div>
                  <span className="eyebrow">WORKSPACE</span>
                  <h1>Proyectos</h1>
                  <p>Administra y organiza tus proyectos de software.</p>
                </div>

                <button
                  className="primary-button"
                  onClick={() => setShowProjectForm(true)}
                >
                  + Nuevo proyecto
                </button>
              </div>

              {loading ? (
                <p>Cargando proyectos...</p>
              ) : projects.length === 0 ? (
                <div className="empty-state">
                  <h3>Aún no tienes proyectos</h3>
                  <p>Crea tu primer proyecto para comenzar.</p>
                </div>
              ) : (
                <div className="projects-grid">
                  {projects.map(project => (
                    <ProjectCard
                      key={project.id}
                      project={project}
                      onSelect={selectProject}
                      onDelete={deleteProject}
                    />
                  ))}
                </div>
              )}
            </>
          )}

          {activeView === 'tasks' && selectedProject && (
            <>
              <div className="page-heading">
                <div>
                  <span className="eyebrow">PROJECT DETAILS</span>
                  <h1>{selectedProject.name}</h1>
                  <p>{selectedProject.description}</p>
                </div>

                <button
                  className="primary-button"
                  onClick={() => setShowTaskForm(true)}
                >
                  + Nueva tarea
                </button>
              </div>

              <div className="section-heading">
                <div>
                  <h2>Tablero de tareas</h2>
                  <p>Busca, filtra y gestiona el avance de las actividades.</p>
                </div>

                <span className="task-counter">
                  {filteredTasks.length} de {tasks.length} tareas
                </span>
              </div>

              {/* Filtros */}
              <div className="task-filters">

                <div className="task-search-field">
                  <label htmlFor="task-search">
                    Buscar tarea
                  </label>

                  <input
                    id="task-search"
                    type="search"
                    value={taskSearch}
                    onChange={event => setTaskSearch(event.target.value)}
                    placeholder="Buscar por título o descripción..."
                  />
                </div>

                <div className="task-filter-field">
                  <label htmlFor="task-status-filter">
                    Estado
                  </label>

                  <select
                    id="task-status-filter"
                    value={taskStatusFilter}
                    onChange={event =>
                      setTaskStatusFilter(
                        event.target.value as 'ALL' | Task['status']
                      )
                    }
                  >
                    <option value="ALL">Todos los estados</option>
                    <option value="PENDING">Pendiente</option>
                    <option value="IN_PROGRESS">En progreso</option>
                    <option value="COMPLETED">Completada</option>
                  </select>
                </div>

                <div className="task-filter-field">
                  <label htmlFor="task-project-filter">
                    Proyecto
                  </label>

                  <select
                    id="task-project-filter"
                    value={selectedProject.id}
                    onChange={event => {
                      const project = projects.find(
                        item => item.id === Number(event.target.value)
                      )

                      if (project) {
                        void selectProject(project)
                      }
                    }}
                  >
                    {projects.map(project => (
                      <option key={project.id} value={project.id}>
                        {project.name}
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  type="button"
                  className="secondary-button clear-filters-button"
                  disabled={!filtersAreActive}
                  onClick={() => {
                    setTaskSearch('')
                    setTaskStatusFilter('ALL')
                  }}
                >
                  Limpiar filtros
                </button>

              </div>

              {/* Tablero Kanban */}
              <KanbanBoard
                tasks={filteredTasks}
                onChangeStatus={changeTaskStatus}
                onDelete={deleteTask}
              />
            </>
          )}
        </section>
      </main>

      {showProjectForm && (
        <div className="modal-backdrop">
          <form className="modal" onSubmit={createProject}>
            <h2>Nuevo proyecto</h2>
            <p>Agrega un proyecto a tu espacio de trabajo.</p>

            <label>Nombre del proyecto</label>
            <input
              value={projectName}
              onChange={e => setProjectName(e.target.value)}
              placeholder="Ej. Plataforma web"
              required
              maxLength={120}
            />

            <label>Descripción</label>
            <textarea
              value={projectDescription}
              onChange={e => setProjectDescription(e.target.value)}
              placeholder="Describe brevemente el proyecto"
            />

            <div className="modal-actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowProjectForm(false)}
              >
                Cancelar
              </button>
              <button className="primary-button" type="submit">
                Crear proyecto
              </button>
            </div>
          </form>
        </div>
      )}

      {showTaskForm && (
        <div className="modal-backdrop">
          <form className="modal" onSubmit={createTask}>
            <h2>Nueva tarea</h2>
            <p>Agrega una actividad a {selectedProject?.name}.</p>

            <label>Título</label>
            <input
              value={taskTitle}
              onChange={e => setTaskTitle(e.target.value)}
              placeholder="Ej. Diseñar pantalla de inicio"
              required
              maxLength={180}
            />

            <label>Descripción</label>
            <textarea
              value={taskDescription}
              onChange={e => setTaskDescription(e.target.value)}
              placeholder="Detalles de la tarea"
            />

            <div className="modal-actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowTaskForm(false)}
              >
                Cancelar
              </button>
              <button className="primary-button" type="submit">
                Crear tarea
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}

export default App