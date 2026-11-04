export interface Project {
  id: number
  name: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
}

export interface Task {
  id: number
  projectId: number
  title: string
  description: string | null
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
  createdAt: string
}