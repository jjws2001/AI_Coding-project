package com.aicoding.Controller;

import com.aicoding.Entity.DTO.ChatRequest;
import com.aicoding.Entity.DTO.CodeExplainRequest;
import com.aicoding.Entity.DTO.CodeReviewRequest;
import com.aicoding.ai.AIService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j  // Lombok注解，自动生成日志对象 log
@RestController  // Spring注解，表示这是一个REST API控制器
@RequestMapping("/api/ai")  // 所有接口的基础路径都是 /api/ai
@RequiredArgsConstructor  // Lombok注解，自动生成构造器注入
public class AIController {

    private final AIService aiService;

    /**
     *前端发送
     * ↓
     * POST /api/ai/chat
     * {
     *   "message": "如何优化这段代码？",
     *   "sessionId": "session-123",
     *   "projectId": 1
     * }
     * ↓
     * AIService.chat() 调用大模型
     * ↓
     * 返回完整响应
     * {
     *   "response": "你可以这样优化..."
     * }
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            String response = aiService.chat(
                    request.getProjectId(),
                    request.getMessage(),
                    request.getSessionId()
            );

            return ResponseEntity.ok(new ChatResponse(response));

        } catch (Exception e) {
            log.error("Chat request failed", e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("对话失败: " + e.getMessage()));
        }
    }

    /**
     * 流式对话（SSE - Server-Sent Events）
     * 前端发送
     * POST /api/ai/chat/stream
     * {
     *   "message": "解释一下这个函数",
     *   "sessionId": "session-123",
     *   "projectId": 1
     * }
     * ↓
     * 返回 SSE 流
     * data: 这
     * data: 个
     * data: 函数
     * data: 的
     * data: 作用
     * data: 是...
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Received stream chat request from session: {}", request.getSessionId());

        return aiService.chatStream(
                request.getProjectId(),
                request.getMessage(),
                request.getSessionId()
        );
    }

    /**
     * 调用 OpenClaw
     */
    @PostMapping("/openclaw")
    public ResponseEntity<String> openClaw(@RequestBody OpenClawRequest request) {
        String result = aiService.callOpenClaw(null, request.task());
        return ResponseEntity.ok(result);
    }

    /**
     * 索引项目
     * 用户上传项目代码，类似于copilot上传附件
     * ↓
     * POST /api/ai/projects/1/index
     * ↓
     * 读取所有代码文件
     * ↓
     * 将代码分块
     * ↓
     * 调用 OpenAI Embeddings API 转换成向量
     * ↓
     * 存储到 ChromaDB
     * ↓
     * { collection_project_1
     *   - "DatabaseConfig.java": [0.123, 0.456, ...]
     *   - "UserController.java": [0.789, 0.234, ...]
     * }
     * 后续使用：
     * 用户问："这个项目如何连接数据库？"
     * ↓
     * 将问题转成向量
     * ↓
     * 从 ChromaDB 检索最相关的代码（DatabaseConfig.java）
     * ↓
     * 把相关代码 + 用户问题一起发给 AI
     * ↓
     * AI 基于实际代码给出精准回答
     */
    @PostMapping("/projects/{projectId}/index")
    public ResponseEntity<Void> indexProject(@PathVariable Long projectId) {
         aiService.indexProject(projectId, null);
        return ResponseEntity.ok().build();
    }

    /**
     * 代码审查
     * 前端编辑器中选中一段代码
     * ↓
     * 点击"AI审查"按钮
     * ↓
     * POST /api/ai/code/review
     * {
     *   "code": "public void login(String username, String password) { ... }",
     *   "sessionId": "session-123"
     * }
     * ↓
     * AI分析代码
     * ↓
     * 返回审查建议
     * {
     *   "response": "发现以下问题：
     *   1. 密码未加密，存在安全风险
     *   2. 缺少参数校验
     *   3. 建议使用 BCrypt 加密密码
     *   ..."
     * }
     */
    @PostMapping("/code/review")
    public ResponseEntity<ChatResponse> reviewCode(@RequestBody CodeReviewRequest request) {
        log.info("Received code review request from session: {}", request.getSessionId());

        try {
            String response = aiService.reviewCode(
                    request.getProjectId(),
                    request.getCode(),
                    request.getSessionId()
            );

            return ResponseEntity.ok(new ChatResponse(response));

        } catch (Exception e) {
            log.error("Code review failed", e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("代码审查失败: " + e.getMessage()));
        }
    }

    /**
     * 代码解释
     * 用户看不懂某段代码
     * ↓
     * 选中代码 → 点击"解释"
     * ↓
     * POST /api/ai/code/explain
     * {
     *   "code": "List<User> users = userRepository.findAll()
     *                 .stream()
     *                 .filter(u -> u.getAge() > 18)
     *                 .collect(Collectors.toList());"
     * }
     * ↓
     * AI 返回解释
     * {
     *   "response": "这段代码的作用是：
     *   1. 从数据库获取所有用户
     *   2. 使用 Java Stream 过滤年龄大于18岁的用户
     *   3. 收集结果为 List 集合
     *   这是一个典型的函数式编程写法..."
     * }
     */
    @PostMapping("/code/explain")
    public ResponseEntity<ChatResponse> explainCode(@RequestBody CodeExplainRequest request) {
        log.info("Received code explain request");

        try {
            String response = aiService.explainCode(
                    request.getProjectId(),
                    request.getCode(),
                    request.getSessionId()
            );
            return ResponseEntity.ok(new ChatResponse(response));

        } catch (Exception e) {
            log.error("Code explanation failed", e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("代码解释失败: " + e.getMessage()));
        }
    }
}

record ChatResponse(String response) {}
record OpenClawRequest(String task) {}

/**
 * [前端]
 * 用户在编辑器输入："如何优化这个查询？"
 * ↓
 * const eventSource = new EventSource('/api/ai/chat/stream', {
 *   method: 'POST',
 *   body: JSON.stringify({
 *     message: "如何优化这个查询？",
 *     sessionId: "session-abc",
 *     projectId: 1
 *   })
 * });
 *
 * eventSource.onmessage = (e) => {
 *   displayMessage(e.data);  // 逐字显示
 * };
 *
 * [后端 - AIController]
 * @PostMapping("/chat/stream")
 * ↓
 * 接收 ChatRequest
 * ↓
 * 调用 aiService.chatStream(projectId, message, sessionId)
 *
 * [后端 - AIService]
 * ↓
 * 1. 从 ChromaDB 检索相关代码
 * 2. 构建 Prompt: "代码上下文: ... \n 用户问题: ..."
 * 3. 调用 OpenAI Stream API
 * 4. 将流式响应包装为 Flux<String>
 *
 * [后端 - AIController]
 * ↓
 * return Flux<String>
 * ↓
 * Spring 自动转换为 SSE 格式
 *
 * [前端]
 * 收到流
 * data: 你
 * data: 可以
 * data: 添加
 * data: 索引
 * data: 来
 * data: 优化
 * data: 查询
 * ...
 * ↓
 * 在界面上实时显示（打字机效果）
 */
