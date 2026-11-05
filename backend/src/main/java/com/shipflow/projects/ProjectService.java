
package com.shipflow.projects;

import com.shipflow.projects.dto.ProjectRequest;
import com.shipflow.projects.dto.ProjectResponse;
import com.shipflow.projects.members.ProjectMemberRepository;
import com.shipflow.users.User;
import com.shipflow.users.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectService(
            ProjectRepository repository,
            UserRepository userRepository,
            ProjectMemberRepository memberRepository) {

        this.repository = repository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {

        String email = getAuthenticatedEmail();

        User owner = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Usuario autenticado no encontrado"
            ));

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setStatus(
            request.status() == null ? "ACTIVE" : request.status()
        );
        project.setOwner(owner);

        return toResponse(repository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {

        String email = getAuthenticatedEmail();

        return repository.findAllAccessibleByEmail(email)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return toResponse(getAccessibleProject(id));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {

        Project project = getOwnedProject(id);

        project.setName(request.name().trim());
        project.setDescription(request.description());

        if (request.status() != null) {
            project.setStatus(request.status());
        }

        return toResponse(repository.save(project));
    }

    @Transactional
    public void delete(Long id) {

        Project project = getOwnedProject(id);

        repository.delete(project);
    }

    private Project getOwnedProject(Long id) {

        String email = getAuthenticatedEmail();

        System.out.println("UPDATE PROJECT ID: " + id);
        System.out.println("UPDATE USER: " + email);

        return repository.findByIdAndOwner_Email(id, email)
            .orElseThrow(() -> {

                System.err.println(
                    "LANZANDO 403 DESDE PROJECT SERVICE"
                );

                return new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permisos para modificar este proyecto"
                );
            });
    }

    private Project getAccessibleProject(Long id) {

        String email = getAuthenticatedEmail();

        Project project = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + id
            ));

        boolean isOwner =
            project.getOwner().getEmail().equals(email);

        boolean isMember =
            memberRepository.existsByProject_IdAndUser_Email(
                id, email
            );

        if (!isOwner && !isMember) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + id
            );
        }

        return project;
    }

    private String getAuthenticatedEmail() {

        var authentication =
            SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Usuario no autenticado"
            );
        }

        return authentication.getName();
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
