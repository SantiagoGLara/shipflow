import type { Project, Task } from '../types'

const API_URL = '/api'
const TOKEN_KEY = 'shipflow_token'
const USER_KEY = 'shipflow_user'

export interface AuthUser {
  id: number
  name: string
  email: string
  role: string
}

interface AuthResponse {
  token: string
  tokenType: string
  user: AuthUser
}

export const authStorage = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  getUser: (): AuthUser | null => {
    const value = localStorage.getItem(USER_KEY)
    if (!value) return null
    try {
      return JSON.parse(value) as AuthUser
    } catch {
      localStorage.removeItem(USER_KEY)
      return null
    }
  },
  save: (data: AuthResponse) => {
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_KEY, JSON.stringify(data.user))
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  const token = authStorage.getToken()
  const response = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    const message = await response.text()
    if (response.status === 401) {
      authStorage.clear()
      window.dispatchEvent(new Event('shipflow:unauthorized'))
    }
    throw new Error(message || `Error HTTP ${response.status}`)
  }

  if (response.status === 204) return undefined as T
  return response.json()
}

export const authApi = {
  login: async (email: string, password: string) => {
    const data = await request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    authStorage.save(data)
    return data
  },
  register: async (name: string, email: string, password: string) => {
    const data = await request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password }),
    })
    authStorage.save(data)
    return data
  },
}

export const projectApi = {
  getAll: () => request<Project[]>('/projects'),
  create: (data: { name: string; description: string; status: string }) =>
    request<Project>('/projects', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: { name: string; description: string; status: string }) =>
    request<Project>(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/projects/${id}`, { method: 'DELETE' }),
}

export const taskApi = {
  getByProject: (projectId: number) => request<Task[]>(`/projects/${projectId}/tasks`),
  create: (projectId: number, data: { title: string; description: string; status: string }) =>
    request<Task>(`/projects/${projectId}/tasks`, { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: { title: string; description: string; status: string }) =>
    request<Task>(`/tasks/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/tasks/${id}`, { method: 'DELETE' }),
}
