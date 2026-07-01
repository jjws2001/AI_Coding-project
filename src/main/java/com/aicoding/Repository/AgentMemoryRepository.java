package com.aicoding.Repository;

import com.aicoding.Entity.model.AgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {
    List<AgentMemory> findTop100ByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
