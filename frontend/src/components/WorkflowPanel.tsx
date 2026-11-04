import { useEffect, useState } from 'react'
import { workflowApi } from '../services/api'
import type { Project, Task } from '../types'

type Row = Record<string, unknown>
type Section = 'defects' | 'tests' | 'releases' | 'environments' | 'deployments' | 'incidents' | 'reviews'
const sections: { id: Section; label: string }[] = [
  { id: 'defects', label: 'Defectos' }, { id: 'tests', label: 'Pruebas' },
  { id: 'releases', label: 'Versiones' }, { id: 'environments', label: 'Ambientes' },
  { id: 'deployments', label: 'Despliegues' }, { id: 'incidents', label: 'Incidentes' },
  { id: 'reviews', label: 'Revisiones' },
]
const val = (row: Row, ...keys: string[]) => {
  for (const key of keys) if (row[key] !== undefined && row[key] !== null) return String(row[key])
  return '—'
}
const idOf = (row: Row) => Number(row.id)

export default function WorkflowPanel({ project, tasks }: { project: Project; tasks: Task[] }) {
  const [section, setSection] = useState<Section>('defects')
  const [rows, setRows] = useState<Row[]>([])
  const [runs, setRuns] = useState<Row[]>([])
  const [releases, setReleases] = useState<Row[]>([])
  const [environments, setEnvironments] = useState<Row[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [form, setForm] = useState<Record<string, string>>({})
  const [selectedRun, setSelectedRun] = useState('')
  const [selectedTask, setSelectedTask] = useState('')
  const [createdDeploymentId, setCreatedDeploymentId] = useState('')

  const load = async () => {
    setError('')
    try {
      if (section === 'defects') setRows(await workflowApi.defects(project.id))
      if (section === 'tests') setRuns(await workflowApi.testRuns(project.id))
      if (section === 'releases') setReleases(await workflowApi.releases(project.id))
      if (section === 'environments') setEnvironments(await workflowApi.environments(project.id))
      if (section === 'incidents') setRows(await workflowApi.incidents(project.id))
      if (section === 'deployments') { setReleases(await workflowApi.releases(project.id)); setEnvironments(await workflowApi.environments(project.id)) }
      if (section === 'reviews') setRows([])
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudieron cargar los datos') }
  }
  useEffect(() => { void load(); setForm({}) }, [section, project.id])

  async function run(action: () => Promise<unknown>) {
    setBusy(true); setError(''); setNotice('')
    try { await action(); setNotice('Cambios guardados correctamente.'); setForm({}); await load() }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo completar la operación') }
    finally { setBusy(false) }
  }
  const field = (key: string, label: string, placeholder = '', type = 'text') => (
    <label className="wf-field" key={key}>{label}
      <input type={type} value={form[key] || ''} placeholder={placeholder} onChange={e => setForm(v => ({ ...v, [key]: e.target.value }))} />
    </label>
  )
  const select = (key: string, label: string, options: [string, string][]) => (
    <label className="wf-field" key={key}>{label}
      <select value={form[key] || ''} onChange={e => setForm(v => ({ ...v, [key]: e.target.value }))}>
        <option value="">Seleccionar...</option>{options.map(([v, l]) => <option value={v} key={v}>{l}</option>)}
      </select>
    </label>
  )
  const submit = (fn: () => Promise<unknown>, label = 'Guardar') => <button className="primary-button" disabled={busy} onClick={() => void run(fn)}>{busy ? 'Guardando…' : label}</button>
  const cards = (items: Row[], titleKeys: string[], subKeys: string[]) => items.length ? <div className="wf-list">{items.map(r => <article className="wf-card" key={String(r.id)}><div><strong>{val(r, ...titleKeys)}</strong><small>{val(r, ...subKeys)}</small></div><span className="wf-state">{val(r, 'status', 'severity', 'type')}</span></article>)}</div> : <div className="empty-state"><h3>Sin registros todavía</h3><p>Los elementos que agregues aparecerán aquí.</p></div>

  return <section className="wf-panel">
    <div className="wf-heading"><div><span className="eyebrow">GESTIÓN DEL CICLO DE ENTREGA</span><h2>Flujo de trabajo</h2><p>Controla calidad, versiones y despliegues del proyecto.</p></div></div>
    <div className="wf-tabs">{sections.map(s => <button key={s.id} className={section === s.id ? 'wf-tab active' : 'wf-tab'} onClick={() => setSection(s.id)}>{s.label}</button>)}</div>
    {error && <div className="error-banner">{error}</div>}{notice && <div className="wf-notice">{notice}</div>}
    {section === 'defects' && <>
      <div className="wf-form"><h3>Registrar defecto</h3><div className="wf-fields">{field('title','Título','Descripción breve')}{field('description','Detalle','Pasos para reproducir')}{select('severity','Severidad',[['LOW','Baja'],['MEDIUM','Media'],['HIGH','Alta'],['CRITICAL','Crítica']])}{select('taskId','Tarea relacionada',[['','Sin tarea'],...tasks.map(t => [String(t.id), `TASK-${t.id} · ${t.title}`] as [string,string])])}</div>{submit(() => workflowApi.createDefect(project.id,{title:form.title,description:form.description,severity:form.severity,taskId:form.taskId || null}), 'Crear defecto')}</div>
      {cards(rows,['title'],['description'])}
      {rows.map(r => <div className="wf-inline" key={`d${r.id}`}><span>Defecto #{String(r.id)}</span><select defaultValue={val(r,'status')} onChange={e => void run(() => workflowApi.updateDefect(idOf(r),{status:e.target.value}))}>{['OPEN','IN_PROGRESS','RESOLVED','CLOSED','REOPENED'].map(x=><option key={x}>{x}</option>)}</select></div>)}
    </>}
    {section === 'tests' && <>
      <div className="wf-form"><h3>Nueva ejecución de pruebas</h3><div className="wf-fields">{field('versionLabel','Versión','v1.0.0')}{field('summary','Resumen','Objetivo de la ejecución')}</div>{submit(() => workflowApi.startTestRun(project.id,{versionLabel:form.versionLabel,summary:form.summary}),'Iniciar ejecución')}</div>
      {runs.map(r => <article className="wf-card wf-run" key={String(r.id)}><div><strong>Ejecución #{String(r.id)} · {val(r,'version_label','versionLabel')}</strong><small>{val(r,'summary','started_at')}</small><span className="wf-state">{val(r,'status')}</span></div><div className="wf-actions"><button className="secondary-button" onClick={() => setSelectedRun(String(r.id))}>Agregar resultado</button>{val(r,'status') === 'RUNNING' && <button className="primary-button" onClick={() => void run(() => workflowApi.finishTestRun(idOf(r),{status:'PASSED',summary:'Ejecución finalizada desde ShipFlow'}))}>Finalizar aprobada</button>}</div></article>)}
      {selectedRun && <div className="wf-form"><h3>Resultado de prueba · ejecución #{selectedRun}</h3><div className="wf-fields">{field('testName','Nombre de prueba','Ej. login válido')}{select('resultStatus','Resultado',[['PASSED','Correcta'],['FAILED','Fallida'],['SKIPPED','Omitida'],['ERROR','Error']])}{field('durationMs','Duración (ms)','120','number')}</div>{submit(() => workflowApi.addTestResult(Number(selectedRun),{testName:form.testName,status:form.resultStatus,durationMs:Number(form.durationMs || 0)}),'Guardar resultado')} <button className="secondary-button" onClick={() => setSelectedRun('')}>Cerrar</button></div>}
    </>}
    {section === 'releases' && <>
      <div className="wf-form"><h3>Crear versión</h3><div className="wf-fields">{field('version','Identificador','v1.0.0')}{field('description','Descripción','Cambios incluidos')}</div>{submit(() => workflowApi.createRelease(project.id,{version:form.version,description:form.description}),'Crear versión')}</div>
      {releases.map(r => <article className="wf-card wf-run" key={String(r.id)}><div><strong>{val(r,'version')}</strong><small>{val(r,'description','created_at')}</small><span className="wf-state">{val(r,'status')}</span></div><div className="wf-actions"><select aria-label="Tarea para asociar" value={selectedTask} onChange={e=>setSelectedTask(e.target.value)}><option value="">Asociar tarea…</option>{tasks.map(t=><option key={t.id} value={t.id}>TASK-{t.id} · {t.title}</option>)}</select><button className="secondary-button" disabled={!selectedTask} onClick={()=>void run(()=>workflowApi.linkReleaseTask(idOf(r),Number(selectedTask)))}>Vincular</button><button className="secondary-button" onClick={()=>void run(()=>workflowApi.submitRelease(idOf(r)))}>Enviar a revisión</button><button className="primary-button" onClick={()=>void run(()=>workflowApi.decideRelease(idOf(r),{status:'APPROVED',summary:'Aprobada desde ShipFlow'}))}>Aprobar</button></div></article>)}
    </>}
    {section === 'environments' && <>
      <div className="wf-form"><h3>Registrar ambiente</h3><div className="wf-fields">{field('name','Nombre','Producción')}{select('type','Tipo',[['DEVELOPMENT','Desarrollo'],['TESTING','Pruebas'],['STAGING','Preproducción'],['PRODUCTION','Producción']])}{field('baseUrl','URL base','https://')}</div>{submit(()=>workflowApi.createEnvironment(project.id,{name:form.name,type:form.type,baseUrl:form.baseUrl}),'Crear ambiente')}</div>{cards(environments,['name'],['base_url','type'])}
    </>}
    {section === 'deployments' && <>
      <div className="wf-form"><h3>Iniciar despliegue</h3><div className="wf-fields">{select('releaseId','Versión',releases.map(r=>[String(r.id),`${r.version} (${r.status})`] as [string,string]))}{select('environmentId','Ambiente',environments.map(e=>[String(e.id),`${e.name} (${e.type})`] as [string,string]))}{field('imageReference','Imagen / artefacto','registry/app:tag')}</div><button className="primary-button" disabled={busy} onClick={() => void run(async () => { const created = await workflowApi.createDeployment(project.id,{releaseId:Number(form.releaseId),environmentId:Number(form.environmentId),imageReference:form.imageReference}); setCreatedDeploymentId(String(created.id ?? '')); })}>Iniciar despliegue</button></div>
      {createdDeploymentId && <div className="wf-notice">Despliegue creado. ID: <strong>{createdDeploymentId}</strong>. Ya puedes finalizarlo o registrar rollback.</div>}
      <div className="wf-note">El backend registra los despliegues y sus resultados. El historial de despliegues no tiene un endpoint GET en la API actual, por lo que esta pantalla permite iniciarlos y finalizar el ID devuelto.</div>
      {field('deploymentId','ID del despliegue',createdDeploymentId || 'ID devuelto al iniciarlo')}
      <div className="wf-actions"><button className="primary-button" onClick={()=>void run(()=>workflowApi.finishDeployment(Number(form.deploymentId),{status:'SUCCEEDED',resultSummary:'Despliegue completado'}))}>Marcar exitoso</button><button className="secondary-button" onClick={()=>void run(()=>workflowApi.finishDeployment(Number(form.deploymentId),{status:'FAILED',resultSummary:'Despliegue fallido'}))}>Marcar fallido</button><button className="secondary-button" onClick={()=>void run(()=>workflowApi.rollback(Number(form.deploymentId)))}>Registrar rollback</button></div>
    </>}
    {section === 'incidents' && <>
      <div className="wf-form"><h3>Registrar incidente</h3><div className="wf-fields">{field('title','Título','Descripción breve')}{field('description','Detalle','Impacto y diagnóstico')}{select('severity','Severidad',[['LOW','Baja'],['MEDIUM','Media'],['HIGH','Alta'],['CRITICAL','Crítica']])}</div>{submit(()=>workflowApi.createIncident(project.id,{title:form.title,description:form.description,severity:form.severity}),'Crear incidente')}</div>
      {cards(rows,['title'],['description'])}
      {rows.map(r=><div className="wf-inline" key={`i${r.id}`}><span>Incidente #{String(r.id)}</span><select defaultValue={val(r,'status')} onChange={e=>void run(()=>workflowApi.updateIncident(idOf(r),{status:e.target.value}))}>{['OPEN','INVESTIGATING','MITIGATED','RESOLVED','CLOSED'].map(x=><option key={x}>{x}</option>)}</select></div>)}
    </>}
    {section === 'reviews' && <div className="wf-form"><h3>Solicitar revisión de tarea</h3><p>El backend permite solicitar revisiones y resolverlas por el revisor asignado. Selecciona la tarea y proporciona el ID del revisor.</p><div className="wf-fields">{select('reviewTask','Tarea',tasks.map(t=>[String(t.id),`TASK-${t.id} · ${t.title}`] as [string,string]))}{field('reviewerId','ID del revisor','ID de usuario','number')}{field('summary','Resumen','Qué debe revisar')}</div>{submit(()=>workflowApi.requestReview(Number(form.reviewTask),{reviewerId:Number(form.reviewerId),summary:form.summary}),'Solicitar revisión')}<div className="wf-note">La API actual no expone un listado de revisiones. Para resolver una revisión se necesita su ID, mediante el endpoint PATCH /task-reviews/{'{id}'}.</div>{field('reviewId','ID de revisión','ID')}{select('reviewStatus','Decisión',[['APPROVED','Aprobada'],['CHANGES_REQUESTED','Solicitar cambios']])}{submit(()=>workflowApi.resolveReview(Number(form.reviewId),{status:form.reviewStatus,summary:form.summary}),'Resolver revisión')}</div>}
  </section>
}
