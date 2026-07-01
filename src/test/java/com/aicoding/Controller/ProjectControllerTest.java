package com.aicoding.Controller;

import com.aicoding.Entity.DTO.FileTreeNode;
import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Entity.model.Project;
import com.aicoding.Entity.model.User;
import com.aicoding.Service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectControllerTest {

    private ProjectService projectService;
    private ProjectController controller;
    private CustomOAuth2User principal;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        controller = new ProjectController(projectService);

        User user = new User();
        user.setId(42L);
        user.setUsername("octocat");
        user.setGithubAccessToken("github-token");
        principal = new CustomOAuth2User(user, Map.of("login", "octocat"));
    }

    @Test
    void importsRepositoryWithAuthenticatedUserAndToken() {
        Project project = new Project();
        project.setId(7L);
        project.setName("demo");
        project.setStatus(Project.ProjectStatus.ACTIVE);
        when(projectService.createProjectFromGitHub(42L, "https://github.com/acme/demo", "github-token"))
                .thenReturn(project);

        var response = controller.importFromGitHub("https://github.com/acme/demo", principal);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(7L);
        verify(projectService).createProjectFromGitHub(
                42L, "https://github.com/acme/demo", "github-token"
        );
    }

    @Test
    void verifiesOwnershipBeforeReadingProjectFiles() {
        FileTreeNode tree = FileTreeNode.builder().name("demo").build();
        when(projectService.getProjectFileTree(7L)).thenReturn(tree);

        controller.getProjectFiles(7L, principal);

        verify(projectService).getProjectByIdAndUserId(7L, 42L);
        verify(projectService).getProjectFileTree(7L);
    }
}
