package com.aicoding.Controller;

import com.aicoding.Entity.DTO.CodeUpdate;
import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CodeSyncController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectService projectService;

    @MessageMapping("/code.update")
    public void handleCodeUpdate(
            @Payload CodeUpdate message,
            SimpMessageHeaderAccessor headers) {
        CustomOAuth2User user = authenticatedUser(headers);
        projectService.getProjectByIdAndUserId(message.getProjectId(), user.getId());
        projectService.updateFileContent(
                message.getProjectId(), message.getFilePath(), message.getContent()
        );

        messagingTemplate.convertAndSend(
                "/topic/project." + message.getProjectId(), message
        );
        log.debug("Updated {} in project {}", message.getFilePath(), message.getProjectId());
    }

    private CustomOAuth2User authenticatedUser(SimpMessageHeaderAccessor headers) {
        if (headers.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomOAuth2User user) {
            return user;
        }
        throw new AccessDeniedException("Authenticated WebSocket session required");
    }
}
