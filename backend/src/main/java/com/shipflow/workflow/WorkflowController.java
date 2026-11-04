package com.shipflow.workflow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService s;

    public WorkflowController(WorkflowService s) {
        this.s = s;
    }

    @PatchMapping("/tasks/{id}/workflow")
    public Map<String, Object> updateTask(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.updateTask(id, b);
    }

    @PostMapping("/tasks/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> comment(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.comment(id, b);
    }

    @GetMapping("/tasks/{id}/comments")
    public List<Map<String, Object>> comments(@PathVariable long id) {
        return s.comments(id);
    }

    @PostMapping("/tasks/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> review(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.requestReview(id, b);
    }

    @PatchMapping("/task-reviews/{id}")
    public Map<String, Object> resolveReview(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.resolveReview(id, b);
    }

    @PostMapping("/projects/{id}/defects")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> defect(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.defect(id, b);
    }

    @GetMapping("/projects/{id}/defects")
    public List<Map<String, Object>> defects(@PathVariable long id) {
        return s.defects(id);
    }

    @PatchMapping("/defects/{id}")
    public Map<String, Object> updateDefect(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.updateDefect(id, b);
    }

    @PostMapping("/projects/{id}/test-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> run(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.startRun(id, b);
    }

    @GetMapping("/projects/{id}/test-runs")
    public List<Map<String, Object>> runs(@PathVariable long id) {
        return s.runs(id);
    }

    @PostMapping("/test-runs/{id}/results")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> result(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.result(id, b);
    }

    @PatchMapping("/test-runs/{id}/finish")
    public Map<String, Object> finishRun(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.finishRun(id, b);
    }

    @PostMapping("/projects/{id}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> release(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.release(id, b);
    }

    @GetMapping("/projects/{id}/releases")
    public List<Map<String, Object>> releases(@PathVariable long id) {
        return s.releases(id);
    }

    @PostMapping("/releases/{id}/tasks/{taskId}")
    public Map<String, Object> releaseTask(@PathVariable long id, @PathVariable long taskId) {
        return s.releaseTask(id, taskId);
    }

    @PostMapping("/releases/{id}/submit")
    public Map<String, Object> submit(@PathVariable long id) {
        return s.submitRelease(id);
    }

    @PostMapping("/releases/{id}/decision")
    public Map<String, Object> decision(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.decideRelease(id, b);
    }

    @PostMapping("/projects/{id}/environments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> environment(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.environment(id, b);
    }

    @GetMapping("/projects/{id}/environments")
    public List<Map<String, Object>> environments(@PathVariable long id) {
        return s.environments(id);
    }

    @PostMapping("/projects/{id}/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> deploy(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.deploy(id, b);
    }

    @PostMapping("/deployments/{id}/finish")
    public Map<String, Object> finishDeploy(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.finishDeploy(id, b);
    }

    @PostMapping("/deployments/{id}/rollback")
    public Map<String, Object> rollback(@PathVariable long id) {
        return s.rollback(id);
    }

    @PostMapping("/projects/{id}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> incident(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.incident(id, b);
    }

    @GetMapping("/projects/{id}/incidents")
    public List<Map<String, Object>> incidents(@PathVariable long id) {
        return s.incidents(id);
    }

    @PatchMapping("/incidents/{id}")
    public Map<String, Object> updateIncident(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return s.updateIncident(id, b);
    }
}
