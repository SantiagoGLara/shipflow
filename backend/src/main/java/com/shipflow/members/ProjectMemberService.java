package com.shipflow.members;

import com.shipflow.auth.CurrentUserService;
import com.shipflow.members.dto.ProjectMemberRequest;
import com.shipflow.members.dto.ProjectMemberResponse;
import com.shipflow.projects.Project;
import com.shipflow.projects.ProjectRepository;
import com.shipflow.users.User;
import com.shipflow.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ProjectMemberService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final CurrentUserService current;

    public ProjectMemberService(ProjectRepository projects, ProjectMemberRepository members, UserRepository users, CurrentUserService current) {
        this.projects = projects; this.members = members; this.users = users; this.current = current;
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(Long projectId) {
        requireProjectAccess(projectId);
        return members.findByProjectIdOrderByJoinedAtAsc(projectId).stream().map(this::response).toList();
    }

    @Transactional
    public ProjectMemberResponse add(Long projectId, ProjectMemberRequest request) {
        Project project = requireLead(projectId);
        User user = users.findByEmail(request.email().trim().toLowerCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe un usuario registrado con ese correo"));
        if (members.existsByProjectIdAndUserId(projectId, user.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya pertenece al proyecto");
        ProjectMember member = new ProjectMember(); member.setProject(project); member.setUser(user); member.setRole(request.role());
        return response(members.save(member));
    }

    @Transactional
    public ProjectMemberResponse changeRole(Long projectId, Long userId, String role) {
        requireLead(projectId);
        if (!List.of("LEAD", "DEVELOPER", "REVIEWER", "DEPLOYER").contains(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido");
        ProjectMember member = members.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no es miembro del proyecto"));
        if ("LEAD".equals(member.getRole()) && !"LEAD".equals(role) && members.countByProjectIdAndRole(projectId, "LEAD") <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El proyecto debe conservar al menos un líder");
        member.setRole(role); return response(member);
    }

    @Transactional
    public void remove(Long projectId, Long userId) {
        requireLead(projectId);
        ProjectMember member = members.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no es miembro del proyecto"));
        if ("LEAD".equals(member.getRole()) && members.countByProjectIdAndRole(projectId, "LEAD") <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes retirar al único líder del proyecto");
        if (projects.findById(projectId).map(p -> p.getOwner().getId().equals(userId)).orElse(false))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes retirar al propietario del proyecto");
        members.delete(member);
    }

    private Project requireLead(Long id) {
        Project project = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        User actor = current.requireUser();
        if (project.getOwner().getId().equals(actor.getId())) return project;
        ProjectMember member = members.findByProjectIdAndUserId(id, actor.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (!"LEAD".equals(member.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere el rol LEAD");
        return project;
    }

    private void requireProjectAccess(Long id) {
        Project project = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        Long actorId = current.requireUser().getId();
        if (!project.getOwner().getId().equals(actorId) && !members.existsByProjectIdAndUserId(id, actorId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
    }

    private ProjectMemberResponse response(ProjectMember m) {
        return new ProjectMemberResponse(m.getId(), m.getUser().getId(), m.getUser().getName(), m.getUser().getEmail(), m.getRole(), m.getJoinedAt());
    }
}
