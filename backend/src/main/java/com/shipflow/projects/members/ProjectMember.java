package com.shipflow.projects.members;

import com.shipflow.projects.Project;
import com.shipflow.users.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "project_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_project_member",
        columnNames = {"project_id", "user_id"}
    )
)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String role = "MEMBER";

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    public ProjectMember() {
    }

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}