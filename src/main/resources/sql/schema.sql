-- PostgreSQL schema for ai-coding-platform.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    github_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    avatar_url VARCHAR(255),
    github_access_token VARCHAR(500),
    created_at TIMESTAMP,
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);

CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users (id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    local_path VARCHAR(255),
    github_repo_url VARCHAR(255),
    github_repo_name VARCHAR(255),
    minio_backup_path VARCHAR(255),
    status VARCHAR(32),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    last_backup_at TIMESTAMP,
    CONSTRAINT uk_projects_user_name UNIQUE (user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects (user_id);
CREATE INDEX IF NOT EXISTS idx_projects_user_status ON projects (user_id, status);
CREATE INDEX IF NOT EXISTS idx_projects_last_backup_at ON projects (last_backup_at);

CREATE TABLE IF NOT EXISTS agent_memories (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    session_id VARCHAR(128),
    type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memory_project_updated
    ON agent_memories (project_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_memory_project_type
    ON agent_memories (project_id, type);
