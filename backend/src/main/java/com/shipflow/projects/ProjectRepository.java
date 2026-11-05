package com.shipflow.projects;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOwner_Email(String email);

    Optional<Project> findByIdAndOwner_Email(Long id, String email);

    @Query("""
        SELECT DISTINCT p
        FROM Project p
        LEFT JOIN ProjectMember pm ON pm.project = p
        LEFT JOIN pm.user u
        WHERE p.owner.email = :email
           OR u.email = :email
        """)
    List<Project> findAllAccessibleByEmail(
        @Param("email") String email
    );
}