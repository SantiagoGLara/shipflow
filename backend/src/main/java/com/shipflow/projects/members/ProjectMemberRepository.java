package com.shipflow.projects.members;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findAllByProject_Id(Long projectId);

    boolean existsByProject_IdAndUser_Email(
        Long projectId,
        String email
    );

    Optional<ProjectMember> findByProject_IdAndUser_Id(
        Long projectId,
        Long userId
    );

    boolean existsByProject_IdAndUser_Id(
        Long projectId,
        Long userId
    );
}