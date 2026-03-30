package com.aicoding.Repository;

import com.aicoding.Entity.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByUserId(Long userId);

    Optional<Project> findByIdAndUserId(Long id, Long userId);

    List<Project> findByUserIdAndStatus(Long userId, Project.ProjectStatus status);

    List<Project> findByLastBackupAtBefore(LocalDateTime time);

    boolean existsByUserIdAndName(Long userId, String name);
}
