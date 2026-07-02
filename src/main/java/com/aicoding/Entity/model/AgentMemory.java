package com.aicoding.Entity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "agent_memories", indexes = {
        @Index(name = "idx_memory_project_updated", columnList = "project_id,updated_at"),
        @Index(name = "idx_memory_project_type", columnList = "project_id,type")
})
public class AgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemoryType type;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MemoryType {
        PROJECT_FACT,
        USER_PREFERENCE,
        ERROR_LESSON,
        DECISION,
        SESSION_SUMMARY
    }
}
