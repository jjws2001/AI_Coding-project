package com.aicoding.Controller;

import com.aicoding.Entity.DTO.CodeDiff;
import com.aicoding.Entity.DTO.CodeUpdate;
import com.aicoding.Service.ProjectService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CodeSyncController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectService projectService;

    /**
     * 接收前端代码更改
     */
    @MessageMapping("/code.update")
    public void handleCodeUpdate(@RequestBody CodeUpdate message,
                                 SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long projectId = message.getProjectId();
            String filePath = message.getFilePath();
            String content = message.getContent();

            // 写入文件
            Path fullPath = projectService.getProjectFilePath(projectId, filePath);
            Files.writeString(fullPath, content);

            log.info("Updated file {} in project {}", filePath, projectId);

            // 广播给其他连接的客户端（协同编辑）
            messagingTemplate.convertAndSend(
                    "/topic/project." + projectId,
                    new CodeUpdate(projectId, filePath, content)
            );

        } catch (Exception e) {
            log.error("Failed to update code", e);
        }
    }

    /**
     * 代码差异同步（增量更新）
     */
    @MessageMapping("/code.diff")
    public void handleCodeDiff(@RequestBody CodeDiff message) {
        // TODO: 应用diff patch
        log.info("Received code diff for project {}", message.getProjectId());
    }
}