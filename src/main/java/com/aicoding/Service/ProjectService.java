package com.aicoding.Service;

import com.aicoding.Entity.DTO.FileTreeNode;
import com.aicoding.Entity.DTO.ProjectDTO;
import com.aicoding.Entity.model.Project;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface ProjectService {

    /**
     * 获取用户的所有项目
     */
    List<ProjectDTO> getUserProjects(Long userId);

    /**
     * 根据ID获取项目
     */
    Project getProjectById(Long projectId);

    /**
     * 根据ID和用户ID获取项目（权限检查）
     */
    Project getProjectByIdAndUserId(Long projectId, Long userId);

    /**
     * 从上传文件创建项目
     */
    Project createProjectFromUpload(Long userId, String name, String githubRepoUrl,
                                    MultipartFile file, String githubToken);

    /**
     * 从GitHub克隆创建项目
     */
    Project createProjectFromGitHub(Long userId, String githubRepoUrl, String githubToken);

    /**
     * 同步项目到GitHub
     */
    void syncWithGitHub(Long projectId, String githubToken);

    /**
     * 从GitHub拉取最新代码
     */
    void pullFromGitHub(Long projectId, String githubToken);

    /**
     * 备份项目到MinIO
     */
    String backupProject(Long projectId);

    /**
     * 从备份恢复项目
     */
    void restoreProject(Long projectId, String backupPath);

    /**
     * 获取项目文件树
     */
    FileTreeNode getProjectFileTree(Long projectId);

    /**
     * 获取文件内容
     */
    String getFileContent(Long projectId, String filePath);

    /**
     * 更新文件内容
     */
    void updateFileContent(Long projectId, String filePath, String content);

    /**
     * 删除文件
     */
    void deleteFile(Long projectId, String filePath);

    /**
     * 创建新文件
     */
    void createFile(Long projectId, String filePath, String content);

    /**
     * 创建新目录
     */
    void createDirectory(Long projectId, String dirPath);

    /**
     * 获取项目文件的完整路径
     */
    Path getProjectFilePath(Long projectId, String filePath);

    /**
     * 获取项目根目录路径
     */
    Path getProjectRootPath(Long projectId);

    /**
     * 删除项目
     */
    void deleteProject(Long projectId);

    /**
     * 自动提交项目更改
     */
    void autoCommitChanges(Long projectId, String message);

    /**
     * 定期备份所有活跃项目
     */
    void scheduledBackup();
}
