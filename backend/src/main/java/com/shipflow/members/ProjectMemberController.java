package com.shipflow.members;

import com.shipflow.members.dto.ProjectMemberRequest;
import com.shipflow.members.dto.ProjectMemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {
    private final ProjectMemberService service;
    public ProjectMemberController(ProjectMemberService service) { this.service = service; }
    @GetMapping public List<ProjectMemberResponse> list(@PathVariable Long projectId) { return service.list(projectId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse add(@PathVariable Long projectId, @Valid @RequestBody ProjectMemberRequest request) { return service.add(projectId, request); }
    @PatchMapping("/{userId}/role")
    public ProjectMemberResponse changeRole(@PathVariable Long projectId, @PathVariable Long userId, @RequestParam String role) { return service.changeRole(projectId, userId, role); }
    @DeleteMapping("/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long projectId, @PathVariable Long userId) { service.remove(projectId, userId); }
}
