package com.shipflow.projects;

import com.shipflow.auth.CurrentUserService;
import com.shipflow.tasks.TaskRepository;
import com.shipflow.projects.dto.ProjectRequest;
import com.shipflow.projects.dto.ProjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final TaskRepository taskRepository;
    private final CurrentUserService currentUserService;

    public ProjectService(ProjectRepository repository, TaskRepository taskRepository,
                          CurrentUserService currentUserService) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Project project = new Project();
        project.setOwner(currentUserService.requireUser());
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setStatus(
            request.status() == null ? "ACTIVE" : request.status()
        );

        return toResponse(repository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        return repository.findByOwnerIdOrderByCreatedAtDesc(currentUserService.requireUser().getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return toResponse(getProject(id));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = getProject(id);

        project.setName(request.name().trim());
        project.setDescription(request.description());

        if (request.status() != null) {
            project.setStatus(request.status());
        }

        return toResponse(repository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        Project project = getProject(id);
        taskRepository.deleteByProjectId(project.getId());
        repository.delete(project);
    }

    private Project getProject(Long id) {
        return repository.findByIdAndOwnerId(id, currentUserService.requireUser().getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + id
            ));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getCreatedAt()
        );
    }
}
