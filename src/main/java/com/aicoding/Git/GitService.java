package com.aicoding.Git;

import com.aicoding.Entity.model.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    @Value("${workspace.base-path}")
    private String workspaceBasePath;

    /**
     * 克隆GitHub仓库到本地工作区
     */
    public void cloneRepository(Project project, String githubToken) throws GitAPIException {
        String localPath = getProjectPath(project);

        Git.cloneRepository()
                .setURI(project.getGithubRepoUrl())
                .setDirectory(new File(localPath))
                .setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(githubToken, "")
                )
                .call();

        log.info("Cloned repository {} to {}", project.getGithubRepoUrl(), localPath);
    }

    /**
     * 提交本地更改
     */
    public void commitChanges(Project project, String message) throws GitAPIException, IOException {
        File repoDir = new File(getProjectPath(project));

        try (Git git = Git.open(repoDir)) {
            // 检查是否有更改
            boolean hasChanges = !git.status().call().isClean();

            if (hasChanges) {
                git.add()
                        .addFilepattern(".")
                        .call();

                git.commit()
                        .setMessage(message)
                        .setAuthor("AI Coding Platform", "system@aicode.com")
                        .call();

                log.info("Committed changes for project {}: {}", project.getName(), message);
            } else {
                log.debug("No changes to commit for project {}", project.getName());
            }
        }
    }

    /**
     * 推送到GitHub
     */
    public void pushToGitHub(Project project, String githubToken) throws GitAPIException, IOException {
        File repoDir = new File(getProjectPath(project));

        try (Git git = Git.open(repoDir)) {
            git.push()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(githubToken, "")
                    )
                    .call();

            log.info("Pushed changes to GitHub for project {}", project.getName());
        }
    }

    /**
     * 从GitHub拉取最新代码
     */
    public void pullFromGitHub(Project project, String githubToken) throws GitAPIException, IOException {
        File repoDir = new File(getProjectPath(project));

        try (Git git = Git.open(repoDir)) {
            git.pull()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(githubToken, "")
                    )
                    .call();

            log.info("Pulled latest changes from GitHub for project {}", project.getName());
        }
    }

    /**
     * 初始化新仓库
     */
    public void initRepository(Project project) throws GitAPIException {
        String localPath = getProjectPath(project);

        Git.init()
                .setDirectory(new File(localPath))
                .call();

        log.info("Initialized git repository at {}", localPath);
    }

    /**
     * 添加远程仓库
     */
    public void addRemote(Project project, String remoteUrl) throws IOException, GitAPIException {
        File repoDir = new File(getProjectPath(project));

        try (Git git = Git.open(repoDir)) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", remoteUrl);
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();

            log.info("Added remote origin: {}", remoteUrl);
        }
    }

    /**
     * 获取当前分支名
     */
    public String getCurrentBranch(Project project) throws IOException {
        File repoDir = new File(getProjectPath(project));

        try (Git git = Git.open(repoDir)) {
            return git.getRepository().getBranch();
        }
    }

    /**
     * 检查是否有未提交的更改
     */
    public boolean hasUncommittedChanges(Project project) throws IOException, GitAPIException {
        File repoDir = new File(getProjectPath(project));

        try (Git git = Git.open(repoDir)) {
            return !git.status().call().isClean();
        }
    }

    private String getProjectPath(Project project) {
        if (project.getLocalPath() != null && !project.getLocalPath().isEmpty()) {
            return project.getLocalPath();
        }

        return String.format("%s/%d/%d",
                workspaceBasePath,
                project.getUser().getId(),
                project.getId());
    }
}