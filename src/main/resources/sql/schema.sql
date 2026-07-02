-- MySQL 8 schema for ai-coding-platform.

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    github_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    avatar_url VARCHAR(255) NULL,
    github_access_token VARCHAR(500) NULL,
    created_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_github_id (github_id),
    KEY idx_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    local_path VARCHAR(255) NULL,
    github_repo_url VARCHAR(255) NULL,
    github_repo_name VARCHAR(255) NULL,
    minio_backup_path VARCHAR(255) NULL,
    status VARCHAR(32) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    last_backup_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_user_name (user_id, name),
    KEY idx_projects_user_id (user_id),
    KEY idx_projects_user_status (user_id, status),
    KEY idx_projects_last_backup_at (last_backup_at),
    CONSTRAINT fk_projects_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_memories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    session_id VARCHAR(128) NULL,
    type VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memory_project_updated (project_id, updated_at),
    KEY idx_memory_project_type (project_id, type),
    CONSTRAINT fk_agent_memories_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
