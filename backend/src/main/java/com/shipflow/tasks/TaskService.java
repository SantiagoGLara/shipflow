package com.shipflow.tasks;

import com.shipflow.projects.Project;
import com.shipflow.projects.ProjectRepository;
import com.shipflow.tasks.dto.TaskRequest;
import com.shipflow.tasks.dto.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(
        TaskRepository taskRepository,
        ProjectRepository projectRepository
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TaskResponse create(Long projectId, TaskRequest request) {
        Project project = getProject(projectId);

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setStatus(request.status() == null ? "PENDING" : request.status());

        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findByProject(Long projectId) {
        // Verifica que el proyecto exista; evita devolver [] para un ID inexistente.
        getProject(projectId);

        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return toResponse(getTask(id));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = getTask(id);

        task.setTitle(request.title().trim());
        task.setDescription(request.description());

        if (request.status() != null) {
            task.setStatus(request.status());
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(getTask(id));
    }

    private Project getProject(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + id
            ));
    }

    private Task getTask(Long id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe una tarea con ID " + id
            ));
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
            task.getId(),
            task.getProject().getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getCreatedAt()
        );
    }
}
