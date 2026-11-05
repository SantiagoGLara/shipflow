import { useEffect, useState } from 'react'
import { memberApi, reviewApi } from '../services/api'
import type { Member, Task, TaskReview, AuthUser } from '../types'

export default function CollaborationPanel({ projectId, tasks, user }: {projectId:number;tasks:Task[];user:AuthUser}) {
  const [members,setMembers]=useState<Member[]>([])
  const [reviews,setReviews]=useState<TaskReview[]>([])
  const [email,setEmail]=useState('')
  const [comment,setComment]=useState<Record<number,string>>({})
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  async function reload(){
    setError('')
    try { const [m,...r]=await Promise.all([memberApi.list(projectId),...tasks.map(t=>reviewApi.list(t.id))]); setMembers(m); setReviews(r.flat()) }
    catch(e){setError(e instanceof Error?e.message:'No se pudo cargar colaboración')}
  }
  useEffect(()=>{void reload()},[projectId,tasks.map(t=>t.id).join(',')])
  async function addMember(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{await memberApi.add(projectId,email);setEmail('');await reload()}catch(err){setError(err instanceof Error?err.message:'No se pudo agregar')}finally{setBusy(false)}}
  async function removeMember(id:number){if(!confirm('¿Eliminar integrante del proyecto?'))return;try{await memberApi.remove(projectId,id);await reload()}catch(e){setError(e instanceof Error?e.message:'No se pudo eliminar')}}
  async function requestReview(taskId:number){try{await reviewApi.request(taskId);await reload()}catch(e){setError(e instanceof Error?e.message:'No se pudo solicitar revisión')}}
  async function decide(id:number,status:'APPROVED'|'CHANGES_REQUESTED'){try{await reviewApi.decide(id,status,comment[id]||'');await reload()}catch(e){setError(e instanceof Error?e.message:'No se pudo resolver revisión')}}
  return <section className="collab-panel">
    <div className="section-heading"><div><h2>Colaboración y revisiones</h2><p>Gestiona integrantes y el flujo de aprobación de tareas.</p></div><button className="secondary-button" onClick={()=>void reload()}>Actualizar</button></div>
    {error&&<div className="error-banner">{error}</div>}
    <div className="collab-grid">
      <div className="collab-card"><h3>Integrantes del proyecto</h3><form className="inline-form" onSubmit={addMember}><input type="email" placeholder="correo@ejemplo.com" value={email} onChange={e=>setEmail(e.target.value)} required/><button className="primary-button" disabled={busy}>Agregar</button></form>
        {members.length===0?<p className="muted">No hay integrantes adicionales.</p>:members.map(m=><div className="member-row" key={m.id}><div><strong>{m.name}</strong><small>{m.email} · {m.role}</small></div>{user.id!==m.id&&<button className="delete-button" onClick={()=>void removeMember(m.id)}>Quitar</button>}</div>)}
      </div>
      <div className="collab-card"><h3>Solicitudes de revisión</h3>{tasks.length===0?<p className="muted">Agrega tareas para habilitar revisiones.</p>:tasks.map(t=>{const pending=reviews.filter(r=>r.taskId===t.id&&r.status==='PENDING');const mine=pending.some(r=>r.requestedById===user.id);return <div className="review-task" key={t.id}><div className="review-task-head"><strong>{t.title}</strong><button className="secondary-button" disabled={mine} onClick={()=>void requestReview(t.id)}>{mine?'En revisión':'Solicitar revisión'}</button></div>{reviews.filter(r=>r.taskId===t.id).map(r=><div className="review-row" key={r.id}><div><strong>#{r.id} · {r.status==='PENDING'?'Pendiente':r.status==='APPROVED'?'Aprobada':'Cambios solicitados'}</strong><small>Solicitó: {r.requestedByName} · {new Date(r.createdAt).toLocaleString()}</small>{r.comment&&<p>{r.comment}</p>}</div>{r.status==='PENDING'&&r.requestedById!==user.id&&<div className="review-actions"><input placeholder="Comentario (opcional)" value={comment[r.id]||''} onChange={e=>setComment(s=>({...s,[r.id]:e.target.value}))}/><button className="primary-button" onClick={()=>void decide(r.id,'APPROVED')}>Aprobar</button><button className="secondary-button" onClick={()=>void decide(r.id,'CHANGES_REQUESTED')}>Solicitar cambios</button></div>}</div>)}</div>})}</div>
    </div>
  </section>
}
