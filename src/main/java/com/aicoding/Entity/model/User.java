package com.aicoding.Entity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String githubId;

    @Column(nullable = false)
    private String username;

    private String email;
    private String avatarUrl;

    @Column(length = 500)
    @JsonIgnore
    private String githubAccessToken;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
