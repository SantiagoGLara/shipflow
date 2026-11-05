export interface AuthUser { id:number; name:string; email:string; role:string }
export interface Project { id:number; name:string; description:string|null; status:'ACTIVE'|'ARCHIVED'; createdAt:string }
export interface Task { id:number; projectId:number; title:string; description:string|null; status:'PENDING'|'IN_PROGRESS'|'COMPLETED'; createdAt:string }
export interface Member { id:number; name:string; email:string; role:string; joinedAt:string }
export interface TaskReview { id:number; taskId:number; taskTitle:string; requestedById:number; requestedByName:string; reviewedById:number|null; reviewedByName:string|null; status:'PENDING'|'APPROVED'|'CHANGES_REQUESTED'; comment:string|null; createdAt:string; reviewedAt:string|null }
