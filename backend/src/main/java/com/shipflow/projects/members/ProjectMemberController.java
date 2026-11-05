package com.shipflow.projects.members;

import com.shipflow.projects.members.dto.AddMemberRequest;
import com.shipflow.projects.members.dto.MemberResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService service;

    public ProjectMemberController(ProjectMemberService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberResponse> findAll(
            @PathVariable Long projectId,
            Authentication authentication) {
        return service.findAll(projectId, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(
            @PathVariable Long projectId,
            @Valid @RequestBody AddMemberRequest request,
            Authentication authentication) {
        return service.add(
            projectId, request, authentication.getName());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            Authentication authentication) {
        service.remove(
            projectId, userId, authentication.getName());
    }
}