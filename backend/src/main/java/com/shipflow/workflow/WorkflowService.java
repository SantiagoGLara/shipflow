package com.shipflow.workflow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shipflow.auth.CurrentUserService;

@Service
public class WorkflowService {
  private final JdbcTemplate db;
  private final CurrentUserService current;

  public WorkflowService(JdbcTemplate db, CurrentUserService current) {
    this.db = db;
    this.current = current;
  }

  private long uid() {
    return current.requireUser().getId();
  }

  private ResponseStatusException error(HttpStatus s, String m) {
    return new ResponseStatusException(s, m);
  }

  private Map<String, Object> one(String sql, Object... a) {
    try {
      return db.queryForMap(sql, a);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw error(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }
  }

  private List<Map<String, Object>> list(String sql, Object... a) {
    return db.queryForList(sql, a);
  }

  private String req(Map<String, Object> b, String k) {
    Object v = b.get(k);
    if (v == null || v.toString().isBlank())
      throw error(HttpStatus.BAD_REQUEST, "Campo requerido: " + k);
    return v.toString().trim();
  }

  private String opt(Map<String, Object> b, String k) {
    Object v = b.get(k);
    return v == null ? null : v.toString();
  }

  private long n(Map<String, Object> b, String k) {
    try {
      return Long.parseLong(req(b, k));
    } catch (NumberFormatException e) {
      throw error(HttpStatus.BAD_REQUEST, "Campo numérico inválido: " + k);
    }
  }

  private void access(long p, String... roles) {
    Map<String, Object> x = one("select owner_id from projects where id=?", p);
    if (((Number) x.get("owner_id")).longValue() == uid())
      return;
    List<Map<String, Object>> m = list("select role from project_members where project_id=? and user_id=?", p, uid());
    if (m.isEmpty())
      throw error(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
    if (roles.length > 0 && !Arrays.asList(roles).contains(m.get(0).get("role")))
      throw error(HttpStatus.FORBIDDEN, "Permiso insuficiente");
  }

  private long taskProject(long id) {
    return ((Number) one("select project_id from tasks where id=?", id).get("project_id")).longValue();
  }

  private long releaseProject(long id) {
    return ((Number) one("select project_id from releases where id=?", id).get("project_id")).longValue();
  }

  private long runProject(long id) {
    return ((Number) one("select project_id from test_runs where id=?", id).get("project_id")).longValue();
  }

  private long envProject(long id) {
    return ((Number) one("select project_id from environments where id=?", id).get("project_id")).longValue();
  }

  private long deployProject(long id) {
    return ((Number) one("select project_id from deployments where id=?", id).get("project_id")).longValue();
  }

  private void audit(long p, String type, String aggregate, long id) {
    db.update(
        "insert into audit_events(project_id,actor_id,event_type,aggregate_type,aggregate_id,payload) values (?,?,?,?,?,'{}'::jsonb)",
        p, uid(), type, aggregate, "" + id);
  }

  // C - tareas, comentarios y revisiones
  @Transactional
  public Map<String, Object> updateTask(long id, Map<String, Object> b) {
    long p = taskProject(id);
    access(p, "LEAD", "DEVELOPER");
    if (b.containsKey("assigneeId")) {
      Object a = b.get("assigneeId");
      if (a == null)
        db.update("update tasks set assignee_id=null where id=?", id);
      else {
        long u = Long.parseLong(a.toString());
        if (!Boolean.TRUE.equals(db.queryForObject(
            "select exists(select 1 from project_members where project_id=? and user_id=?)", Boolean.class, p, u)))
          throw error(HttpStatus.BAD_REQUEST, "El responsable debe ser miembro del proyecto");
        db.update("update tasks set assignee_id=? where id=?", u, id);
      }
    }
    if (b.containsKey("priority")) {
      String v = req(b, "priority").toUpperCase();
      if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(v))
        throw error(HttpStatus.BAD_REQUEST, "Prioridad inválida");
      db.update("update tasks set priority=? where id=?", v, id);
    }
    if (b.containsKey("dueDate"))
      db.update("update tasks set due_date=?::timestamptz where id=?", opt(b, "dueDate"), id);
    if (b.containsKey("status")) {
      String v = req(b, "status").toUpperCase();
      if (!Set.of("PENDING", "IN_PROGRESS", "COMPLETED").contains(v))
        throw error(HttpStatus.BAD_REQUEST, "Estado inválido");
      db.update("update tasks set status=? where id=?", v, id);
    }
    db.update("update tasks set updated_at=now() where id=?", id);
    audit(p, "TASK_UPDATED", "TASK", id);
    return one(
        "select id,project_id,title,description,status,priority,due_date,assignee_id,created_at,updated_at from tasks where id=?",
        id);
  }

  @Transactional
  public Map<String, Object> comment(long task, Map<String, Object> b) {
    long p = taskProject(task);
    access(p);
    long id = db.queryForObject("insert into task_comments(task_id,author_id,content) values (?,?,?) returning id",
        Long.class, task, uid(), req(b, "content"));
    audit(p, "TASK_COMMENTED", "TASK_COMMENT", id);
    return one("select * from task_comments where id=?", id);
  }

  public List<Map<String, Object>> comments(long task) {
    access(taskProject(task));
    return list("select * from task_comments where task_id=? order by created_at", task);
  }

  @Transactional
  public Map<String, Object> requestReview(long task, Map<String, Object> b) {
    long p = taskProject(task);
    access(p, "LEAD", "DEVELOPER");
    long reviewer = n(b, "reviewerId");
    if (!Boolean.TRUE.equals(db.queryForObject(
        "select exists(select 1 from project_members where project_id=? and user_id=? and role in ('LEAD','REVIEWER'))",
        Boolean.class, p, reviewer)))
      throw error(HttpStatus.BAD_REQUEST, "El revisor debe tener rol LEAD o REVIEWER");
    long id = db.queryForObject(
        "insert into task_reviews(task_id,reviewer_id,status,summary) values (?,?,'PENDING',?) returning id",
        Long.class, task, reviewer, opt(b, "summary"));
    audit(p, "TASK_REVIEW_REQUESTED", "TASK_REVIEW", id);
    return one("select * from task_reviews where id=?", id);
  }

  @Transactional
  public Map<String, Object> resolveReview(long id, Map<String, Object> b) {
    Map<String, Object> r = one("select * from task_reviews where id=?", id);
    long p = taskProject(((Number) r.get("task_id")).longValue());
    access(p, "LEAD", "REVIEWER");
    if (((Number) r.get("reviewer_id")).longValue() != uid())
      throw error(HttpStatus.FORBIDDEN, "Solo el revisor asignado puede resolverla");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("APPROVED", "CHANGES_REQUESTED").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Estado inválido");
    db.update("update task_reviews set status=?,summary=?,resolved_at=now() where id=?", s, opt(b, "summary"), id);
    audit(p, "TASK_REVIEW_RESOLVED", "TASK_REVIEW", id);
    return one("select * from task_reviews where id=?", id);
  }

  // D - defectos y pruebas
  @Transactional
  public Map<String, Object> defect(long p, Map<String, Object> b) {
    access(p, "LEAD", "DEVELOPER", "REVIEWER");
    String sev = req(b, "severity").toUpperCase();
    if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(sev))
      throw error(HttpStatus.BAD_REQUEST, "Severidad inválida");
    Long task = b.get("taskId") == null ? null : Long.valueOf(b.get("taskId").toString());
    if (task != null && taskProject(task) != p)
      throw error(HttpStatus.BAD_REQUEST, "La tarea no pertenece al proyecto");
    long id = db.queryForObject(
        "insert into defects(project_id,task_id,title,description,severity,reported_by) values (?,?,?,?,?,?) returning id",
        Long.class, p, task, req(b, "title"), opt(b, "description"), sev, uid());
    audit(p, "DEFECT_CREATED", "DEFECT", id);
    return one("select * from defects where id=?", id);
  }

  public List<Map<String, Object>> defects(long p) {
    access(p);
    return list("select * from defects where project_id=? order by created_at desc", p);
  }

  @Transactional
  public Map<String, Object> updateDefect(long id, Map<String, Object> b) {
    Map<String, Object> d = one("select * from defects where id=?", id);
    long p = ((Number) d.get("project_id")).longValue();
    access(p, "LEAD", "DEVELOPER", "REVIEWER");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "REOPENED").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Estado inválido");
    db.update(
        "update defects set status=?,updated_at=now(),resolved_at=case when ? in ('RESOLVED','CLOSED') then now() else null end where id=?",
        s, s, id);
    audit(p, "DEFECT_UPDATED", "DEFECT", id);
    return one("select * from defects where id=?", id);
  }

  @Transactional
  public Map<String, Object> startRun(long p, Map<String, Object> b) {
    access(p, "LEAD", "REVIEWER");
    long id = db.queryForObject(
        "insert into test_runs(project_id,version_label,executed_by,status,summary) values (?,?,?,'RUNNING',?) returning id",
        Long.class, p, opt(b, "versionLabel"), uid(), opt(b, "summary"));
    audit(p, "TEST_RUN_STARTED", "TEST_RUN", id);
    return one("select * from test_runs where id=?", id);
  }

  @Transactional
  public Map<String, Object> result(long run, Map<String, Object> b) {
    long p = runProject(run);
    access(p, "LEAD", "REVIEWER");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("PASSED", "FAILED", "SKIPPED", "ERROR").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Resultado inválido");
    long id = db.queryForObject(
        "insert into test_results(test_run_id,test_name,status,duration_ms,details) values (?,?,?,?, '{}'::jsonb) returning id",
        Long.class, run, req(b, "testName"), s, b.get("durationMs"));
    return one("select * from test_results where id=?", id);
  }

  @Transactional
  public Map<String, Object> finishRun(long id, Map<String, Object> b) {
    long p = runProject(id);
    access(p, "LEAD", "REVIEWER");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("PASSED", "FAILED", "CANCELLED").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Estado final inválido");
    db.update("update test_runs set status=?,summary=?,finished_at=now() where id=? and status='RUNNING'", s,
        opt(b, "summary"), id);
    audit(p, "TEST_RUN_FINISHED", "TEST_RUN", id);
    return one("select * from test_runs where id=?", id);
  }

  public List<Map<String, Object>> runs(long p) {
    access(p);
    return list("select * from test_runs where project_id=? order by started_at desc", p);
  }

  // E - versiones y aprobaciones
  @Transactional
  public Map<String, Object> release(long p, Map<String, Object> b) {
    access(p, "LEAD", "DEVELOPER");
    long id = db.queryForObject(
        "insert into releases(project_id,version,description,created_by) values (?,?,?,?) returning id", Long.class, p,
        req(b, "version"), opt(b, "description"), uid());
    audit(p, "RELEASE_CREATED", "RELEASE", id);
    return one("select * from releases where id=?", id);
  }

  @Transactional
  public Map<String, Object> releaseTask(long r, long t) {
    long p = releaseProject(r);
    access(p, "LEAD", "DEVELOPER");

    if (taskProject(t) != p) {
      throw error(HttpStatus.BAD_REQUEST, "La tarea no pertenece al proyecto");
    }

    int inserted = db.update("""
        INSERT INTO release_tasks (release_id, task_id, project_id)
        VALUES (?, ?, ?)
        ON CONFLICT DO NOTHING
        """, r, t, p);

    return one(
        "SELECT * FROM release_tasks WHERE release_id=? AND task_id=?",
        r, t);
  }

  @Transactional
  public Map<String, Object> submitRelease(long id) {
    long p = releaseProject(id);
    access(p, "LEAD", "DEVELOPER");
    int incomplete = db.queryForObject(
        "select count(*) from release_tasks rt join tasks t on t.id=rt.task_id where rt.release_id=? and t.status<>'COMPLETED'",
        Integer.class, id);
    int critical = db.queryForObject(
        "select count(*) from defects where project_id=? and severity='CRITICAL' and status not in ('RESOLVED','CLOSED')",
        Integer.class, p);
    if (incomplete > 0 || critical > 0)
      throw error(HttpStatus.CONFLICT, "Hay tareas incompletas o defectos críticos abiertos");
    db.update("update releases set status='IN_REVIEW' where id=? and status='DRAFT'", id);
    audit(p, "RELEASE_SUBMITTED", "RELEASE", id);
    return one("select * from releases where id=?", id);
  }

  @Transactional
  public Map<String, Object> decideRelease(long id, Map<String, Object> b) {
    long p = releaseProject(id);
    access(p, "LEAD", "REVIEWER");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("APPROVED", "REJECTED").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Decisión inválida");
    if (s.equals("APPROVED")) {
      int incomplete = db.queryForObject(
          "select count(*) from release_tasks rt join tasks t on t.id=rt.task_id where rt.release_id=? and t.status<>'COMPLETED'",
          Integer.class, id);
      int critical = db.queryForObject(
          "select count(*) from defects where project_id=? and severity='CRITICAL' and status not in ('RESOLVED','CLOSED')",
          Integer.class, p);
      if (incomplete > 0 || critical > 0)
        throw error(HttpStatus.CONFLICT, "La versión no cumple los criterios de aprobación");
    }
    db.update("insert into release_approvals(release_id,approver_id,status,comment) values (?,?,?,?)", id, uid(), s,
        opt(b, "comment"));
    db.update(
        "update releases set status=?,approved_at=case when ?='APPROVED' then now() else approved_at end where id=?", s,
        s, id);
    audit(p, "RELEASE_DECIDED", "RELEASE", id);
    return one("select * from releases where id=?", id);
  }

  public List<Map<String, Object>> releases(long p) {
    access(p);
    return list("select * from releases where project_id=? order by created_at desc", p);
  }

  // F - ambientes, despliegues, incidentes y rollback
  @Transactional
  public Map<String, Object> environment(long p, Map<String, Object> b) {
    access(p, "LEAD", "DEPLOYER");
    String t = req(b, "type").toUpperCase();
    if (!Set.of("DEVELOPMENT", "TESTING", "STAGING", "PRODUCTION").contains(t))
      throw error(HttpStatus.BAD_REQUEST, "Tipo de ambiente inválido");
    long id = db.queryForObject("insert into environments(project_id,name,type,base_url) values (?,?,?,?) returning id",
        Long.class, p, req(b, "name"), t, opt(b, "baseUrl"));
    audit(p, "ENVIRONMENT_CREATED", "ENVIRONMENT", id);
    return one("select * from environments where id=?", id);
  }

  public List<Map<String, Object>> environments(long p) {
    access(p);
    return list("select * from environments where project_id=? order by name", p);
  }

  @Transactional
  public Map<String, Object> deploy(long p, Map<String, Object> b) {
    access(p, "LEAD", "DEPLOYER");
    long r = n(b, "releaseId"), e = n(b, "environmentId");
    if (releaseProject(r) != p || envProject(e) != p)
      throw error(HttpStatus.BAD_REQUEST, "La versión y el ambiente deben pertenecer al proyecto");
    String type = one("select type from environments where id=?", e).get("type").toString();
    String state = one("select status from releases where id=?", r).get("status").toString();
    if (type.equals("PRODUCTION")) {
      if (!state.equals("APPROVED"))
        throw error(HttpStatus.CONFLICT, "Producción requiere versión aprobada");
      if (db.queryForObject("select count(*) from test_runs where project_id=? and status='PASSED'", Integer.class,
          p) == 0)
        throw error(HttpStatus.CONFLICT, "Producción requiere pruebas exitosas");
      if (db.queryForObject("select count(*) from release_approvals where release_id=? and status='APPROVED'",
          Integer.class, r) == 0)
        throw error(HttpStatus.CONFLICT, "Producción requiere aprobación registrada");
    } else if (!Set.of("APPROVED", "IN_REVIEW").contains(state))
      throw error(HttpStatus.CONFLICT, "La versión no está lista para despliegue");
    long id = db.queryForObject(
        "insert into deployments(project_id,release_id,environment_id,deployed_by,image_reference,status) values (?,?,?,?,?,'RUNNING') returning id",
        Long.class, p, r, e, uid(), opt(b, "imageReference"));
    audit(p, "DEPLOYMENT_STARTED", "DEPLOYMENT", id);
    return one("select * from deployments where id=?", id);
  }

  @Transactional
  public Map<String, Object> finishDeploy(long id, Map<String, Object> b) {
    long p = deployProject(id);
    access(p, "LEAD", "DEPLOYER");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("SUCCEEDED", "FAILED").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Estado inválido");
    db.update("update deployments set status=?,result_summary=?,finished_at=now() where id=? and status='RUNNING'", s,
        opt(b, "resultSummary"), id);
    if (s.equals("FAILED"))
      db.update(
          "insert into incidents(project_id,deployment_id,title,description,severity,opened_by) values (?,?,?,?,'HIGH',?)",
          p, id, "Fallo de despliegue #" + id, opt(b, "resultSummary"), uid());
    audit(p, "DEPLOYMENT_FINISHED", "DEPLOYMENT", id);
    return one("select * from deployments where id=?", id);
  }

  @Transactional
  public Map<String, Object> rollback(long id) {
    long p = deployProject(id);
    access(p, "LEAD", "DEPLOYER");
    Map<String, Object> d = one("select * from deployments where id=?", id);
    Map<String, Object> previous = db.query(
        "select id,release_id,image_reference from deployments where project_id=? and environment_id=? and status='SUCCEEDED' and id<>? order by finished_at desc limit 1",
        rs -> {
          if (!rs.next())
            return null;
          Map<String, Object> x = new HashMap<>();
          x.put("id", rs.getLong("id"));
          x.put("release_id", rs.getLong("release_id"));
          x.put("image_reference", rs.getString("image_reference"));
          return x;
        }, p, d.get("environment_id"), id);
    if (previous == null)
      throw error(HttpStatus.CONFLICT, "No existe despliegue exitoso anterior");
    long target = ((Number) previous.get("id")).longValue();
    long rid = db.queryForObject(
        "insert into deployments(project_id,release_id,environment_id,deployed_by,image_reference,status,rollback_of,result_summary,finished_at) values (?,?,?,?,?,'ROLLED_BACK',?,?,now()) returning id",
        Long.class, p, previous.get("release_id"), d.get("environment_id"), uid(), previous.get("image_reference"), id,
        "Rollback registrado al deployment previo #" + target);
    audit(p, "ROLLBACK_RECORDED", "DEPLOYMENT", rid);
    return one("select * from deployments where id=?", rid);
  }

  @Transactional
  public Map<String, Object> incident(long p, Map<String, Object> b) {
    access(p, "LEAD", "DEPLOYER");
    String s = req(b, "severity").toUpperCase();
    if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Severidad inválida");
    long id = db.queryForObject(
        "insert into incidents(project_id,title,description,severity,opened_by) values (?,?,?,?,?) returning id",
        Long.class, p, req(b, "title"), opt(b, "description"), s, uid());
    audit(p, "INCIDENT_CREATED", "INCIDENT", id);
    return one("select * from incidents where id=?", id);
  }

  @Transactional
  public Map<String, Object> updateIncident(long id, Map<String, Object> b) {
    Map<String, Object> i = one("select * from incidents where id=?", id);
    long p = ((Number) i.get("project_id")).longValue();
    access(p, "LEAD", "DEPLOYER");
    String s = req(b, "status").toUpperCase();
    if (!Set.of("OPEN", "INVESTIGATING", "MITIGATED", "RESOLVED", "CLOSED").contains(s))
      throw error(HttpStatus.BAD_REQUEST, "Estado inválido");
    db.update(
        "update incidents set status=?,resolved_at=case when ? in ('RESOLVED','CLOSED') then now() else null end where id=?",
        s, s, id);
    audit(p, "INCIDENT_UPDATED", "INCIDENT", id);
    return one("select * from incidents where id=?", id);
  }

  public List<Map<String, Object>> incidents(long p) {
    access(p);
    return list("select * from incidents where project_id=? order by opened_at desc", p);
  }
}
