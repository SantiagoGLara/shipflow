package com.shipflow.projects.members;

import com.shipflow.projects.Project;
import com.shipflow.projects.ProjectRepository;
import com.shipflow.projects.members.dto.AddMemberRequest;
import com.shipflow.projects.members.dto.MemberResponse;
import com.shipflow.users.User;
import com.shipflow.users.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectMemberService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ProjectMemberService(
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> findAll(Long projectId, String email) {
        getAccessibleProject(projectId, email);

        return memberRepository.findAllByProject_Id(projectId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public MemberResponse add(
            Long projectId,
            AddMemberRequest request,
            String email) {

        Project project = getOwnedProject(projectId, email);

        User user = userRepository.findByEmail(
                request.email().trim().toLowerCase())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un usuario con ese correo"
            ));

        if (project.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "El propietario ya pertenece al proyecto"
            );
        }

        if (memberRepository.existsByProject_IdAndUser_Id(
                projectId, user.getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El usuario ya es miembro del proyecto"
            );
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);

        return toResponse(memberRepository.save(member));
    }

    @Transactional
    public void remove(
            Long projectId,
            Long userId,
            String email) {

        getOwnedProject(projectId, email);

        ProjectMember member = memberRepository
            .findByProject_IdAndUser_Id(projectId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "El usuario no es miembro de este proyecto"
            ));

        memberRepository.delete(member);
    }

    private Project getOwnedProject(Long projectId, String email) {
        return projectRepository.findByIdAndOwner_Email(projectId, email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + projectId
            ));
    }

    private Project getAccessibleProject(Long projectId, String email) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + projectId
            ));

        boolean isOwner = project.getOwner().getEmail().equals(email);
        boolean isMember =
            memberRepository.existsByProject_IdAndUser_Email(
                projectId, email);

        if (!isOwner && !isMember) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un proyecto con ID " + projectId
            );
        }

        return project;
    }

    private MemberResponse toResponse(ProjectMember member) {
        return new MemberResponse(
            member.getUser().getId(),
            member.getUser().getName(),
            member.getUser().getEmail(),
            member.getRole(),
            member.getJoinedAt()
        );
    }
}