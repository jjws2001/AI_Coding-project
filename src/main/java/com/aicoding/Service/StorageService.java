package com.aicoding.Service;

import com.aicoding.Entity.model.Project;

import java.io.InputStream;

public interface StorageService {

    /**
     * 备份项目到MinIO
     */
    String backupProject(Project project) throws Exception;

    /**
    * 从MinIO恢复项目
    */
    void restoreProject(Project project, String backupPath) throws Exception;

    /**
     * 上传项目文件
     */
    void uploadProjectFiles(Long userId, Long projectId, InputStream fileStream, String fileName, long Size) throws Exception;
}
