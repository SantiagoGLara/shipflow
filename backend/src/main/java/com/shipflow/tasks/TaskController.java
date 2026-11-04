package com.shipflow.tasks;

import com.shipflow.tasks.dto.TaskRequest;
import com.shipflow.tasks.dto.TaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(
        @PathVariable Long projectId,
        @Valid @RequestBody TaskRequest request
    ) {
        return service.create(projectId, request);
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public List<TaskResponse> findByProject(@PathVariable Long projectId) {
        return service.findByProject(projectId);
    }

    @GetMapping("/api/tasks/{id}")
    public TaskResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/api/tasks/{id}")
    public TaskResponse update(
        @PathVariable Long id,
        @Valid @RequestBody TaskRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
