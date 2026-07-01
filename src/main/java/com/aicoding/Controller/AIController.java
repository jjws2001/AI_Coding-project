package com.aicoding.Controller;

import com.aicoding.Entity.DTO.ChatRequest;
import com.aicoding.Entity.DTO.CodeExplainRequest;
import com.aicoding.Entity.DTO.CodeReviewRequest;
import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Service.ProjectService;
import com.aicoding.ai.AIService;
import com.aicoding.ai.memory.ChatMemoryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final ChatMemoryRegistry chatMemoryRegistry;
    private final ProjectService projectService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(request.getProjectId(), principal);
        String response = aiService.chat(
                request.getProjectId(), request.getMessage(), request.getSessionId()
        );
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(request.getProjectId(), principal);
        log.info("Received stream chat request from session {}", request.getSessionId());
        return aiService.chatStream(
                request.getProjectId(), request.getMessage(), request.getSessionId()
        );
    }

    @PostMapping("/projects/{projectId}/index")
    public ResponseEntity<Void> indexProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(projectId, principal);
        aiService.indexProject(projectId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/context/compact")
    public ResponseEntity<ChatResponse> compactContext(
            @RequestBody CompactContextRequest request,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(request.projectId(), principal);
        boolean compacted = chatMemoryRegistry.compact(request.projectId(), request.sessionId());
        return ResponseEntity.ok(new ChatResponse(compacted
                ? "Conversation context compacted"
                : "No active conversation context found"));
    }

    @PostMapping("/code/review")
    public ResponseEntity<ChatResponse> reviewCode(
            @RequestBody CodeReviewRequest request,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(request.getProjectId(), principal);
        String response = aiService.reviewCode(
                request.getProjectId(), request.getCode(), request.getSessionId()
        );
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @PostMapping("/code/explain")
    public ResponseEntity<ChatResponse> explainCode(
            @RequestBody CodeExplainRequest request,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(request.getProjectId(), principal);
        String response = aiService.explainCode(
                request.getProjectId(), request.getCode(), request.getSessionId()
        );
        return ResponseEntity.ok(new ChatResponse(response));
    }

    private void requireOwnership(Long projectId, CustomOAuth2User principal) {
        projectService.getProjectByIdAndUserId(projectId, principal.getId());
    }
}

record ChatResponse(String response) {
}

record CompactContextRequest(Long projectId, String sessionId) {
}
