-- MySQL schema for ai-coding-platform
-- Generated based on JPA entities:
--   com.aicoding.Entity.model.User
--   com.aicoding.Entity.model.Project

CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `github_id` VARCHAR(255) NOT NULL,
  `username` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) DEFAULT NULL,
  `avatar_url` VARCHAR(255) DEFAULT NULL,
  `github_access_token` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `last_login_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_github_id` (`github_id`),
  KEY `idx_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `projects` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `local_path` VARCHAR(255) NOT NULL,
  `github_repo_url` VARCHAR(255) DEFAULT NULL,
  `github_repo_name` VARCHAR(255) DEFAULT NULL,
  `minio_backup_path` VARCHAR(255) DEFAULT NULL,
  `status` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `last_backup_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_projects_user_name` (`user_id`, `name`),
  KEY `idx_projects_user_id` (`user_id`),
  KEY `idx_projects_user_id_status` (`user_id`, `status`),
  KEY `idx_projects_last_backup_at` (`last_backup_at`),
  CONSTRAINT `fk_projects_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
