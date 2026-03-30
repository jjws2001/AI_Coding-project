package com.aicoding.Service.Impl;

import com.aicoding.Config.MiniOConfig;
import com.aicoding.Service.StorageService;

import com.aicoding.Entity.model.Project;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;



    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${workspace.base-path}")
    private String workspaceBasePath;

    /**
     * 备份项目到MinIO
     */
    public String backupProject(Project project) throws Exception {
        String projectPath = getProjectPath(project);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String zipFileName = String.format("project_%d_%s.zip", project.getId(), timestamp);
        Path zipPath = Files.createTempFile("backup_", ".zip");

        try {
            // 压缩项目目录
            zipDirectory(Paths.get(projectPath), zipPath);

            // 上传到MinIO
            String objectName = String.format("backups/%d/%s", project.getUser().getId(), zipFileName);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(Files.newInputStream(zipPath), Files.size(zipPath), -1)
                            .contentType("application/zip")
                            .build()
            );

            log.info("Backed up project {} to MinIO: {}", project.getId(), objectName);
            return objectName;

        } finally {
            Files.deleteIfExists(zipPath);
        }
    }

    /**
     * 从MinIO恢复项目
     */
    public void restoreProject(Project project, String backupPath) throws Exception {
        String projectPath = getProjectPath(project);
        Path tempZip = Files.createTempFile("restore_", ".zip");

        try {
            // 从MinIO下载
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(backupPath)
                            .build()
            )) {
                Files.copy(stream, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            // 解压到项目目录
            unzipDirectory(tempZip, Paths.get(projectPath));

            log.info("Restored project {} from backup {}", project.getId(), backupPath);

        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    /**
     * 上传项目文件
     */
    public void uploadProjectFiles(Long userId, Long projectId, InputStream inputStream,
                                   String fileName, long size) throws Exception {
        String objectName = String.format("uploads/%d/%d/%s", userId, projectId, fileName);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, size, -1)
                        .build()
        );

        log.info("Uploaded file {} for project {}", fileName, projectId);
    }

    private void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 跳过.git目录
                    if (file.toString().contains("/.git/")) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path relative = sourceDir.relativize(file);
                    ZipEntry zipEntry = new ZipEntry(relative.toString());
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void unzipDirectory(Path zipPath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))){
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private String getProjectPath(Project project) {
        return String.format("%s/%d/%d",
                workspaceBasePath,
                project.getUser().getId(),
                project.getId());
    }
}
