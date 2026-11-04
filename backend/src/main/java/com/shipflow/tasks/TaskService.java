package com.shipflow.tasks;

import com.shipflow.auth.CurrentUserService;
import com.shipflow.members.ProjectMemberRepository;
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
    private final CurrentUserService currentUserService;
    private final ProjectMemberRepository members;

    public TaskService(
        TaskRepository taskRepository,
        ProjectRepository projectRepository,
        CurrentUserService currentUserService,
        ProjectMemberRepository members
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
        this.members = members;
    }

    @Transactional
    public TaskResponse create(Long projectId, TaskRequest request) {
        Project project = getProjectForRole(projectId, "LEAD", "DEVELOPER");

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
        Task task = getTaskForRole(id, "LEAD", "DEVELOPER");

        task.setTitle(request.title().trim());
        task.setDescription(request.description());

        if (request.status() != null) {
            task.setStatus(request.status());
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(getTaskForRole(id, "LEAD", "DEVELOPER"));
    }

    private Project getProject(Long id) {
        return projectRepository.findAccessibleByIdAndUser(id, currentUserService.requireUser().getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + id
            ));
    }


    private Project getProjectForRole(Long id, String... roles) {
        Project project = getProject(id);
        Long userId = currentUserService.requireUser().getId();
        String role = members.findByProjectIdAndUserId(id, userId).map(m -> m.getRole()).orElse("");
        if (!java.util.Arrays.asList(roles).contains(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para modificar tareas");
        return project;
    }

    private Task getTaskForRole(Long id, String... roles) {
        Task task = getTask(id);
        getProjectForRole(task.getProject().getId(), roles);
        return task;
    }

    private Task getTask(Long id) {
        return taskRepository.findAccessible(id, currentUserService.requireUser().getId())
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
