import type { Project, Task } from '../types'

const API_URL = '/api'

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Error HTTP ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}

export const projectApi = {
  getAll: () => request<Project[]>('/projects'),

  create: (data: {
    name: string
    description: string
    status: string
  }) =>
    request<Project>('/projects', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (
    id: number,
    data: {
      name: string
      description: string
      status: string
    }
  ) =>
    request<Project>(`/projects/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: number) =>
    request<void>(`/projects/${id}`, {
      method: 'DELETE',
    }),
}

export const taskApi = {
  getByProject: (projectId: number) =>
    request<Task[]>(`/projects/${projectId}/tasks`),

  create: (
    projectId: number,
    data: {
      title: string
      description: string
      status: string
    }
  ) =>
    request<Task>(`/projects/${projectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (
    id: number,
    data: {
      title: string
      description: string
      status: string
    }
  ) =>
    request<Task>(`/tasks/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: number) =>
    request<void>(`/tasks/${id}`, {
      method: 'DELETE',
    }),
}