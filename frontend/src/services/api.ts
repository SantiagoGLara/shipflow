import type { Project, Task, Member, TaskReview, AuthUser } from '../types'

const API_URL = '/api'
const TOKEN_KEY = 'shipflow_token'
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = tokenStore.get()
  const response = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  if (!response.ok) {
    const raw = await response.text()
    let message = raw || `Error HTTP ${response.status}`
    try { const parsed = JSON.parse(raw); message = parsed.message || parsed.error || message } catch { /* texto plano */ }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const authApi = {
  login: async (email: string, password: string) => {
    const result = await request<{token:string;tokenType:string;user:AuthUser}>('/auth/login', { method:'POST', body:JSON.stringify({email,password}) })
    tokenStore.set(result.token); return result
  },
  register: async (name: string, email: string, password: string) => {
    const result = await request<{token:string;tokenType:string;user:AuthUser}>('/auth/register', { method:'POST', body:JSON.stringify({name,email,password}) })
    tokenStore.set(result.token); return result
  },
  logout: () => tokenStore.clear(),
}

export const projectApi = {
  getAll: () => request<Project[]>('/projects'),
  create: (data: {name:string;description:string;status:string}) => request<Project>('/projects',{method:'POST',body:JSON.stringify(data)}),
  update: (id:number,data:{name:string;description:string;status:string}) => request<Project>(`/projects/${id}`,{method:'PUT',body:JSON.stringify(data)}),
  delete: (id:number) => request<void>(`/projects/${id}`,{method:'DELETE'}),
}
export const taskApi = {
  getByProject: (projectId:number) => request<Task[]>(`/projects/${projectId}/tasks`),
  create: (projectId:number,data:{title:string;description:string;status:string}) => request<Task>(`/projects/${projectId}/tasks`,{method:'POST',body:JSON.stringify(data)}),
  update: (id:number,data:{title:string;description:string;status:string}) => request<Task>(`/tasks/${id}`,{method:'PUT',body:JSON.stringify(data)}),
  delete: (id:number) => request<void>(`/tasks/${id}`,{method:'DELETE'}),
}
export const memberApi = {
  list: (projectId:number) => request<Member[]>(`/projects/${projectId}/members`),
  add: (projectId:number,email:string) => request<Member>(`/projects/${projectId}/members`,{method:'POST',body:JSON.stringify({email})}),
  remove: (projectId:number,userId:number) => request<void>(`/projects/${projectId}/members/${userId}`,{method:'DELETE'}),
}
export const reviewApi = {
  list: (taskId:number) => request<TaskReview[]>(`/tasks/${taskId}/reviews`),
  request: (taskId:number) => request<TaskReview>(`/tasks/${taskId}/reviews`,{method:'POST'}),
  decide: (reviewId:number,status:'APPROVED'|'CHANGES_REQUESTED',comment:string) => request<TaskReview>(`/reviews/${reviewId}`,{method:'PATCH',body:JSON.stringify({status,comment})}),
}
