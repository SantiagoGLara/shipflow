package com.shipflow.tasks;

import com.shipflow.projects.Project;
import com.shipflow.projects.ProjectRepository;
import com.shipflow.projects.members.ProjectMemberRepository;
import com.shipflow.tasks.dto.TaskRequest;
import com.shipflow.tasks.dto.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       ProjectMemberRepository memberRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public TaskResponse create(Long projectId, TaskRequest request) {
        Project project = getAccessibleProject(projectId);
        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setStatus(request.status() == null ? "PENDING" : request.status());
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findByProject(Long projectId) {
        getAccessibleProject(projectId);
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) { return toResponse(getAccessibleTask(id)); }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = getAccessibleTask(id);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        if (request.status() != null) task.setStatus(request.status());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id) { taskRepository.delete(getAccessibleTask(id)); }

    public Task getAccessibleTask(Long id) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe una tarea con ID " + id));
        assertProjectAccess(task.getProject());
        return task;
    }

    public Project getAccessibleProject(Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe un proyecto con ID " + id));
        assertProjectAccess(project);
        return project;
    }

    public boolean isProjectOwner(Project project) {
        return project.getOwner().getEmail().equalsIgnoreCase(currentEmail());
    }

    private void assertProjectAccess(Project project) {
        String email = currentEmail();
        if (!project.getOwner().getEmail().equalsIgnoreCase(email)
                && !memberRepository.existsByProject_IdAndUser_Email(project.getId(), email)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el recurso solicitado");
        }
    }

    private String currentEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        return auth.getName();
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTitle(), task.getDescription(), task.getStatus(), task.getCreatedAt());
    }
}
