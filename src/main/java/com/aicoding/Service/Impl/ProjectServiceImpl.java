package com.aicoding.Service.Impl;

import com.aicoding.Entity.DTO.FileTreeNode;
import com.aicoding.Entity.DTO.ProjectDTO;
import com.aicoding.Entity.model.Project;
import com.aicoding.Entity.model.User;
import com.aicoding.Exception.ProjectException;
import com.aicoding.Exception.ResourceNotFoundException;
import com.aicoding.Git.GitService;
import com.aicoding.Repository.ProjectRepository;
import com.aicoding.Repository.UserRepository;
import com.aicoding.Service.ProjectService;
import com.aicoding.Service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private static final String PENDING_LOCAL_PATH = "PENDING_PATH";

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final GitService gitService;
    private final StorageService storageService;

    @Value("${workspace.base-path}")
    private String workspaceBasePath;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectDTO> getUserProjects(Long userId) {
        log.info("Fetching projects for user {}", userId);

        List<Project> projects = projectRepository.findByUserId(userId);

        return projects.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Project getProjectById(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public Project getProjectByIdAndUserId(Long projectId, Long userId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found or access denied"));
    }
    @Override
    @Transactional
    public Project createProjectFromUpload(Long userId, String name, String githubRepoUrl,
                                           MultipartFile file, String githubToken) {
        log.info("Creating project from upload for user {}: {}", userId, name);

        // 楠岃瘉鐢ㄦ埛
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 妫€鏌ラ」鐩悕绉版槸鍚﹂噸澶?
        if (projectRepository.existsByUserIdAndName(userId, name)) {
            throw new ProjectException("Project with name '" + name + "' already exists");
        }

        // 鍒涘缓椤圭洰瀹炰綋
        Project project = new Project();
        project.setUser(user);
        project.setName(name);
        project.setGithubRepoUrl(githubRepoUrl);
        project.setStatus(Project.ProjectStatus.INITIALIZING);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        project.setLocalPath(PENDING_LOCAL_PATH);  // 涓存椂鍗犱綅锛屼繚瀛樺悗浼氭浛鎹负鐪熷疄璺緞

        // 淇濆瓨浠ヨ幏鍙朓D
        project = projectRepository.save(project);

        try {
            // 鍒涘缓椤圭洰鐩綍
            Path projectPath = buildWorkspaceProjectPath(userId, project.getId());
            Files.createDirectories(projectPath);
            project.setLocalPath(projectPath.toString());

            // 瑙ｅ帇涓婁紶鐨勬枃浠?
            if (file != null && !file.isEmpty()) {
                unzipFile(file, projectPath);
            }

            // 鍒濆鍖朑it浠撳簱
            gitService.initRepository(project);
            gitService.commitChanges(project, "Initial commit from upload");

            // 濡傛灉鎻愪緵浜咷itHub浠撳簱URL锛屽皾璇曟帹閫?
            if (githubRepoUrl != null && !githubRepoUrl.isEmpty() && githubToken != null) {
                try {
                    project.setGithubRepoName(extractRepoName(githubRepoUrl));
                    gitService.addRemote(project, githubRepoUrl);
                    gitService.pushToGitHub(project, githubToken);
                } catch (Exception e) {
                    log.warn("Failed to push to GitHub, continuing anyway", e);
                }
            }

            // 鏇存柊鐘舵€?
            project.setStatus(Project.ProjectStatus.ACTIVE);
            project.setUpdatedAt(LocalDateTime.now());

            log.info("Successfully created project {} for user {}", project.getId(), userId);

        } catch (Exception e) {
            log.error("Failed to create project from upload", e);
            project.setStatus(Project.ProjectStatus.ERROR);
            throw new ProjectException("Failed to create project: " + e.getMessage(), e);
        } finally {
            projectRepository.save(project);
        }

        return project;
    }

    @Override
    @Transactional
    public Project createProjectFromGitHub(Long userId, String githubRepoUrl, String githubToken) {
        log.info("Creating project from GitHub for user {}: {}", userId, githubRepoUrl);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String repoName = extractRepoName(githubRepoUrl);

        // 妫€鏌ラ」鐩悕绉版槸鍚﹂噸澶?
        if (projectRepository.existsByUserIdAndName(userId, repoName)) {
            throw new ProjectException("Project with name '" + repoName + "' already exists");
        }

        Project project = new Project();
        project.setUser(user);
        project.setName(repoName);
        project.setGithubRepoUrl(githubRepoUrl);
        project.setGithubRepoName(repoName);
        project.setStatus(Project.ProjectStatus.INITIALIZING);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        project.setLocalPath(PENDING_LOCAL_PATH);

        project = projectRepository.save(project);

        try {
            // 鍒涘缓椤圭洰鐩綍
            Path projectPath = buildWorkspaceProjectPath(userId, project.getId());
            Files.createDirectories(projectPath);
            project.setLocalPath(projectPath.toString());

            // 鍏嬮殕浠撳簱
            gitService.cloneRepository(project, githubToken);

            project.setStatus(Project.ProjectStatus.ACTIVE);
            project.setUpdatedAt(LocalDateTime.now());

            log.info("Successfully cloned project {} from GitHub", project.getId());

        } catch (Exception e) {
            log.error("Failed to clone from GitHub", e);
            project.setStatus(Project.ProjectStatus.ERROR);
            throw new ProjectException("Failed to clone repository: " + e.getMessage(), e);
        } finally {
            projectRepository.save(project);
        }

        return project;
    }

    @Override
    @Transactional
    public void syncWithGitHub(Long projectId, String githubToken) {
        log.info("Syncing project {} with GitHub", projectId);

        Project project = getProjectById(projectId);

        try {
            // 鎻愪氦鏈湴鏇存敼
            gitService.commitChanges(project, "Auto-sync: " + LocalDateTime.now());

            // 鎺ㄩ€佸埌GitHub
            gitService.pushToGitHub(project, githubToken);

            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully synced project {} with GitHub", projectId);

        } catch (Exception e) {
            log.error("Failed to sync with GitHub", e);
            throw new ProjectException("Failed to sync with GitHub: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void pullFromGitHub(Long projectId, String githubToken) {
        log.info("Pulling project {} from GitHub", projectId);

        Project project = getProjectById(projectId);

        try {
            gitService.pullFromGitHub(project, githubToken);

            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully pulled project {} from GitHub", projectId);

        } catch (Exception e) {
            log.error("Failed to pull from GitHub", e);
            throw new ProjectException("Failed to pull from GitHub: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public String backupProject(Long projectId) {
        log.info("Backing up project {}", projectId);

        Project project = getProjectById(projectId);

        try {
            String backupPath = storageService.backupProject(project);

            project.setMinioBackupPath(backupPath);
            project.setLastBackupAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully backed up project {} to {}", projectId, backupPath);
            return backupPath;

        } catch (Exception e) {
            log.error("Failed to backup project", e);
            throw new ProjectException("Failed to backup project: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void restoreProject(Long projectId, String backupPath) {
        log.info("Restoring project {} from {}", projectId, backupPath);

        Project project = getProjectById(projectId);

        try {
            storageService.restoreProject(project, backupPath);

            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully restored project {}", projectId);

        } catch (Exception e) {
            log.error("Failed to restore project", e);
            throw new ProjectException("Failed to restore project: " + e.getMessage(), e);
        }
    }

    @Override
    public FileTreeNode getProjectFileTree(Long projectId) {
        log.debug("Building file tree for project {}", projectId);

        Path rootPath = getProjectRootPath(projectId);

        if (!Files.exists(rootPath)) {
            throw new ProjectException("Project directory not found");
        }

        try {
            return buildFileTree(rootPath, rootPath);
        } catch (IOException e) {
            log.error("Failed to build file tree", e);
            throw new ProjectException("Failed to build file tree: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFileContent(Long projectId, String filePath) {
        log.debug("Reading file content: project={}, file={}", projectId, filePath);

        Path fullPath = getProjectFilePath(projectId, filePath);

        if (!Files.exists(fullPath)) {
            throw new ResourceNotFoundException("File not found: " + filePath);
        }

        if (Files.isDirectory(fullPath)) {
            throw new ProjectException("Path is a directory, not a file");
        }

        try {
            return Files.readString(fullPath);
        } catch (IOException e) {
            log.error("Failed to read file", e);
            throw new ProjectException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void updateFileContent(Long projectId, String filePath, String content) {
        log.debug("Updating file content: project={}, file={}", projectId, filePath);

        Path fullPath = getProjectFilePath(projectId, filePath);

        try {
            // 纭繚鐖剁洰褰曞瓨鍦?
            Files.createDirectories(fullPath.getParent());

            // 鍐欏叆鏂囦欢
            Files.writeString(fullPath, content, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            // 鏇存柊椤圭洰鏃堕棿鎴?
            Project project = getProjectById(projectId);
            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.debug("Successfully updated file {}", filePath);

        } catch (IOException e) {
            log.error("Failed to update file", e);
            throw new ProjectException("Failed to update file: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteFile(Long projectId, String filePath) {
        log.info("Deleting file: project={}, file={}", projectId, filePath);

        Path fullPath = getProjectFilePath(projectId, filePath);

        try {
            Files.deleteIfExists(fullPath);

            Project project = getProjectById(projectId);
            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully deleted file {}", filePath);

        } catch (IOException e) {
            log.error("Failed to delete file", e);
            throw new ProjectException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void createFile(Long projectId, String filePath, String content) {
        log.info("Creating file: project={}, file={}", projectId, filePath);

        Path fullPath = getProjectFilePath(projectId, filePath);

        if (Files.exists(fullPath)) {
            throw new ProjectException("File already exists: " + filePath);
        }

        updateFileContent(projectId, filePath, content);
    }

    @Override
    @Transactional
    public void createDirectory(Long projectId, String dirPath) {
        log.info("Creating directory: project={}, dir={}", projectId, dirPath);

        Path fullPath = getProjectFilePath(projectId, dirPath);

        try {
            Files.createDirectories(fullPath);

            Project project = getProjectById(projectId);
            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully created directory {}", dirPath);

        } catch (IOException e) {
            log.error("Failed to create directory", e);
            throw new ProjectException("Failed to create directory: " + e.getMessage(), e);
        }
    }

    @Override
    public Path getProjectFilePath(Long projectId, String filePath) {
        Path rootPath = getProjectRootPath(projectId).toAbsolutePath().normalize();
        String normalizedFilePath = sanitizeRelativePath(filePath);
        Path fullPath = rootPath.resolve(normalizedFilePath).normalize();

        // 瀹夊叏妫€鏌ワ細闃叉璺緞閬嶅巻鏀诲嚮
        if (!fullPath.startsWith(rootPath)) {
            throw new ProjectException("Invalid file path: " + filePath);
        }

        return fullPath;
    }

    @Override
    public Path getProjectRootPath(Long projectId) {
        Project project = getProjectById(projectId);

        if (hasUsableLocalPath(project.getLocalPath())) {
            return Paths.get(project.getLocalPath()).toAbsolutePath().normalize();
        }

        // 濡傛灉鏁版嵁搴撲腑娌℃湁淇濆瓨璺緞锛屼娇鐢ㄩ粯璁よ矾寰?
        return buildWorkspaceProjectPath(project.getUser().getId(), projectId);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        log.info("Deleting project {}", projectId);

        Project project = getProjectById(projectId);

        try {
            // 鍒犻櫎鏂囦欢绯荤粺涓殑椤圭洰鐩綍
            Path projectPath = getProjectRootPath(projectId);
            if (Files.exists(projectPath)) {
                deleteDirectory(projectPath);
            }

            // 鍒犻櫎鏁版嵁搴撹褰?
            projectRepository.delete(project);

            log.info("Successfully deleted project {}", projectId);

        } catch (IOException e) {
            log.error("Failed to delete project directory", e);
            throw new ProjectException("Failed to delete project: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void autoCommitChanges(Long projectId, String message) {
        log.info("Auto-committing changes for project {}", projectId);

        Project project = getProjectById(projectId);

        try {
            gitService.commitChanges(project,
                    message != null ? message : "Auto-commit: " + LocalDateTime.now());

            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Successfully auto-committed project {}", projectId);

        } catch (Exception e) {
            log.error("Failed to auto-commit changes", e);
            // 涓嶆姏鍑哄紓甯革紝鍏佽闈欓粯澶辫触
        }
    }

    @Override
    @Scheduled(cron = "${workspace.backup-cron}")
    @Transactional
    public void scheduledBackup() {
        log.info("Starting scheduled backup of all active projects");

        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<Project> projects = projectRepository.findByLastBackupAtBefore(threshold);

        int successCount = 0;
        int failCount = 0;

        for (Project project : projects) {
            try {
                if (project.getStatus() == Project.ProjectStatus.ACTIVE) {
                    backupProject(project.getId());
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Failed to backup project {}", project.getId(), e);
                failCount++;
            }
        }

        log.info("Scheduled backup completed: {} succeeded, {} failed", successCount, failCount);
    }

    // ==================== 绉佹湁杈呭姪鏂规硶 ====================

    private ProjectDTO convertToDTO(Project project) {
        Path projectPath = hasUsableLocalPath(project.getLocalPath())
                ? Paths.get(project.getLocalPath()).toAbsolutePath().normalize()
                : buildWorkspaceProjectPath(project.getUser().getId(), project.getId());

        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .githubRepoUrl(project.getGithubRepoUrl())
                .githubRepoName(project.getGithubRepoName())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .lastBackupAt(project.getLastBackupAt())
                .fileCount(countFiles(projectPath))
                .totalSize(calculateSize(projectPath))
                .build();
    }

    private FileTreeNode buildFileTree(Path root, Path current) throws IOException {
        String name = current.equals(root) ? root.getFileName().toString() : current.getFileName().toString();
        String relativePath = root.relativize(current).toString();

        FileTreeNode node = FileTreeNode.builder()
                .name(name)
                .path(relativePath.isEmpty() ? "/" : relativePath)
                .build();

        if (Files.isDirectory(current)) {
            node.setType(FileTreeNode.FileType.DIRECTORY);
            node.setChildren(new ArrayList<>());

            // 璺宠繃 .git 鐩綍
            if (current.getFileName().toString().equals(".git")) {
                return node;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                for (Path entry : stream) {
                    // 璺宠繃闅愯棌鏂囦欢鍜岀壒娈婄洰褰?
                    String fileName = entry.getFileName().toString();
                    if (!fileName.startsWith(".") &&
                            !fileName.equals("node_modules") &&
                            !fileName.equals("target") &&
                            !fileName.equals("build")) {
                        node.addChild(buildFileTree(root, entry));
                    }
                }
            }
        } else {
            node.setType(FileTreeNode.FileType.FILE);
            node.setSize(Files.size(current));
            node.setExtension(getFileExtension(name));
        }

        return node;
    }

    private void unzipFile(MultipartFile file, Path destination) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destination.resolve(entry.getName()).normalize();

                // 瀹夊叏妫€鏌?
                if (!entryPath.startsWith(destination)) {
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String extractRepoName(String githubRepoUrl) {
        // https://github.com/owner/repo.git -> repo
        // https://github.com/owner/repo -> repo
        String[] parts = githubRepoUrl.replaceAll("\\.git$", "").split("/");
        return parts[parts.length - 1];
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    private Long countFiles(Path directory) {
        if (!Files.exists(directory)) {
            return 0L;
        }

        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/"))
                    .count();
        } catch (IOException e) {
            log.error("Failed to count files", e);
            return 0L;
        }
    }

    private Long calculateSize(Path directory) {
        if (!Files.exists(directory)) {
            return 0L;
        }

        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/"))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("Failed to calculate size", e);
            return 0L;
        }
    }

    private boolean hasUsableLocalPath(String localPath) {
        return localPath != null
                && !localPath.isBlank()
                && !PENDING_LOCAL_PATH.equals(localPath);
    }

    private Path buildWorkspaceProjectPath(Long userId, Long projectId) {
        return Paths.get(workspaceBasePath, String.valueOf(userId), String.valueOf(projectId))
                .toAbsolutePath()
                .normalize();
    }

    private String sanitizeRelativePath(String filePath) {
        if (filePath == null) {
            return "";
        }
        String normalized = filePath.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}



