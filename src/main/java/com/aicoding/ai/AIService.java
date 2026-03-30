package com.aicoding.ai;

import com.aicoding.Entity.model.Project;
import com.aicoding.Service.ProjectService;
import com.aicoding.ai.guardrail.GuardrailsFilter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {
    @Value("${ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${ai.openai.model}")
    private String openaiModel;

    @Value("${ai.chroma.url}")
    private String chromaUrl;

    @Value("${ai.rag.max-results:5}")
    private Integer maxResults;

    @Value("${ai.rag.min-score:0.7}")
    private Double minScore;

    @Value("${workspace.base-path}")
    private String workspaceBasePath;

    private final OpenAiEmbeddingModel embeddingModel;

    private final DocumentSplitter documentSplitter;

    private final AiServiceFactory aiServiceFactory;

    private final GuardrailsFilter guardrailsFilter;
    @Resource
    private final StreamingChatModel streamingChatModel;

    private final ProjectService projectService;


    /**
     * 为项目建立RAG索引:文件级增量更新,允许传入具体的 filePath
     * 1. 核心触发点一：项目初始化/导入时（冷启动）
     * 这是最基础的执行时机，也就是所谓的“全量构建索引”。
     * 具体动作：用户刚登录平台，选择从 GitHub Clone 一个仓库，或者从本地上传了一个项目压缩包解压后。
     * 执行逻辑：当后端的 JGit 完成代码拉取，或者文件解压落盘完成后的回调阶段，立即异步调用 indexProject(projectId)。在这个阶段，AI 需要完整地阅读整个项目。
     *
     * 2. 核心触发点二：云端代码发生变更时（增量/全量更新）
     * 当用户进入编辑页面（如你提到的 Monaco Editor 界面），代码随时都在变化。
     * 具体动作 1（用户保存代码）：当用户在前端按 Ctrl+S，或者前端防抖（Debounce）自动将修改同步到后端的物理文件中时。
     * 具体动作 2（Git 同步）：如果用户在平台上点击了 git pull，拉取了团队其他人的最新代码时。
     * 执行逻辑：文件落盘后，必须触发更新。
     * 初期简单做法：每次有文件保存，直接清空 Chroma 里的这个 Collection，然后重新全量跑一次 indexProject（适用于极小项目，大项目会极度消耗 OpenAI API 费用和时间）。
     * 成熟做法（推荐）：只把刚刚修改过的那个文件重新切片算向量，覆盖掉数据库里旧的向量（即“增量更新”）。
     *
     * 3. 辅助触发点三：手动触发（防御性设计）
     * 不论系统设计得多精密，偶尔也会出现文件状态和向量数据库不同步的情况（比如系统重启、网络抖动导致上一次算向量失败）。
     * 具体动作：在前端项目的设置面板或聊天框上方，放一个类似 “重新构建 AI 索引” (Re-index Workspace) 的按钮。
     * 执行逻辑：用户点击后，后端清空当前项目的 Chroma Collection，强制重新跑一遍完整的 indexProject。
     */
    @Transactional
    public void indexProject(Long projectId, String... filePaths) {
        try {
            List<Document> documents = new ArrayList<>();
            if (filePaths == null || filePaths.length == 0) {
                Project project = projectService.getProjectById(projectId);
                String projectPath = getProjectPath(project);
                // 加载项目文件
                documents = loadProjectDocuments(projectPath);
            } else {
                for (String filePath : filePaths) {
                    documents.add(FileSystemDocumentLoader.loadDocument(Paths.get(workspaceBasePath + filePath)));
                }
            }
            // 创建嵌入模型：动态创建与当前 projectId 绑定的 Chroma 库
            EmbeddingStore<TextSegment> projectEmbeddingStore = new InMemoryEmbeddingStore<>();
//                    ChromaEmbeddingStore.builder()
//                    .baseUrl(chromaUrl)
//                    .collectionName("project_" + projectId)
//                    .build();

            // 3. 动态组装加工流水线（结合了全局配置和动态库）
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(documentSplitter) // 使用注入的全局切分规则
                    .embeddingModel(embeddingModel)     // 使用注入的全局向量大模型
                    .embeddingStore(projectEmbeddingStore) // 使用刚刚动态建好的当前项目专属库
                    .build();

            // 4. 执行摄入
            ingestor.ingest(documents);

            log.info("Successfully indexed {} documents for project {}", documents.size(), projectId);

        } catch (Exception e) {
            log.error("Failed to index project {}", projectId, e);
        }
    }

    /**
     * 简单对话
     */
    public String chatMini(Long projectId, String userMessage, String sessionId) {
        // 安全过滤
        String filteredMessage = guardrailsFilter.filter(userMessage);
        String response = aiServiceFactory.simpleChatService().chat(sessionId, filteredMessage);
        return guardrailsFilter.filter(response);
    }

    /**
     * 方法：AI对话返回整个回答（带RAG）
     */
    public String chat(Long projectId, String userMessage, String sessionId) {
        // 安全过滤
        String filteredMessage = guardrailsFilter.filter(userMessage);
        // 获取项目专属Assistant助手
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        String response = assistant.chat(sessionId, filteredMessage);
        return guardrailsFilter.filter(response);

        // 创建聊天模型
//        ChatModel chatModel = OpenAiChatModel.builder()
//                .apiKey(openaiApiKey)
//                .modelName(openaiModel)
//                .temperature(0.7)
//                .build();
        // 创建检索器
        // 创建一个 Chroma 向量数据库的 Embedding 存储对象
        // EmbeddingStore：存储和检索 嵌入向量（embedding） 的存储库。用于将文本（如文档段落）转换为嵌入向量后存入 Chroma，根据用户查询的
        //              嵌入向量，在 Chroma 中进行相似性搜索（语义搜索），支持 RAG（Retrieval-Augmented Generation）等 AI 应用场景。
        // TextSegment：该存储库关联的元数据或内容类型是 TextSegment（通常包含文本片段及其元信息，如来源、位置等）
//        EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
//                .baseUrl(chromaUrl)  // 指定 Chroma 数据库的 HTTP API 地址
//                .collectionName("project_" + projectId)  // 指定在 Chroma 中使用的 集合（Collection）名称
//                .build();
        // 创建一个(比如openai)嵌入模型（Embedding Model），将文本转换为向量
        // EmbeddingModel：定义了如何将文本（如句子、段落）转换为数值向量（即“嵌入”）
        // 如果没有显式指定模型名称（如 .modelName("text-embedding-3-small")），LangChain4j 通常会使用 默认的 OpenAI 嵌入模型
//        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
//                .apiKey(openaiApiKey)
//                .build();
        // 创建一个基于嵌入存储的内容检索器（Content Retriever），它将使用指定的嵌入模型和嵌入存储来检索相关内容
        // 用于 RAG（Retrieval-Augmented Generation，检索增强生成）架构中，作为“记忆”或“知识库”的查询接口
//        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
//                .embeddingStore(embeddingStore)  // 指定 向量存储后端
//                .embeddingModel(embeddingModel)  // 指定 用于生成查询文本嵌入的模型
//                .maxResults(5)  // 最多返回 5 个最相关的文本片段
//                .build();
        // 创建对话式检索增强生成链，构建 具备上下文记忆 + 外部知识检索能力的智能对话系统
//        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
//                .chatModel(chatModel)  // 指定用于生成对话回复的聊天模型
//                .contentRetriever(retriever)  // 指定用于检索相关内容的检索器（RAG 组件）
//                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))  // 指定对话记忆，保留最近 10 条消息
//                .build();
//        String response = chain.execute(filteredMessage);
    }

    /**
     * AI对话（流式响应）
     */
    public Flux<String> chatStream(Long projectId, String userMessage, String sessionId) {
        log.info("AI chat stream - project: {}, session: {}", projectId, sessionId);

        String filteredMessage = guardrailsFilter.filter(userMessage);
        Project project = projectService.getProjectById(projectId);
        // TODO 如果是结合项目的模式，需要添加项目信息,
        //  从Project中获取本地代码或github仓库地址，拿到项目进行分析，
        //  因此在拿到的请求中需要加一个参数，即当前项目文件的路径，或者github仓库地址
        String enhancedMessage = String.format(
                "Project ID: %d\nUser Question: %s",
                projectId,
                filteredMessage
        );

        return Flux.create(sink -> {
            // 构建带记忆的聊天消息列表
            List<ChatMessage> messages = new ArrayList<>();

            // 1. 加载历史,已在Factory中配置，自动读取
//            ChatMemory chatMemory = chatMemoryStore.getOrCreate(sessionId);
//            messages.addAll(chatMemory.messages());

            // 2. 添加系统消息（角色设定）
            messages.add(systemMessage("""
                You are an expert coding assistant helping developers write, debug, and improve code.
                You have access to the project files and can analyze code, suggest improvements, and help fix bugs.
                Always provide clear, concise, and actionable advice.
                When suggesting code changes, provide complete, runnable code snippets.
                Project ID: %d
                """.formatted(projectId)));

            // 3. 添加用户消息
            messages.add(userMessage(enhancedMessage));

            // 4. 发起流式请求
            streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // 修正：partialResponse 本身已经是字符串文本，直接发送给 sink
                    if (!sink.isCancelled()) {
                        sink.next(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    // 可选：保存 AI 回复到记忆
                    // chatMemory.add(completeResponse.content());
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
        });
    }

    private List<Document> loadProjectDocuments(String projectPath) {
        // 加载代码文件（过滤掉node_modules, .git等）
        File projectDir = new File(projectPath);
        return FileSystemDocumentLoader.loadDocuments(
                Paths.get(projectPath),
                (PathMatcher) path -> {
                    String pathStr = path.toString();
                    return !pathStr.contains("node_modules")
                            && !pathStr.contains(".git")
                            && !pathStr.contains("target")
                            && (pathStr.endsWith(".java")
                            || pathStr.endsWith(".ts")
                            || pathStr.endsWith(".js")
                            || pathStr.endsWith(".py"));
                }
        );
    }

    private String getProjectPath(Project project) {
        return String.format("%s/%d/%d",
                workspaceBasePath,
                project.getUser().getId(),
                project.getId());
    }

    /**
     * 代码审查
     */
    public String reviewCode(Long projectId, String code, String sessionId) {
        log.info("Reviewing code for session {}", sessionId);
        String filteredCode = guardrailsFilter.filter(code);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return assistant.reviewCode(sessionId, filteredCode);
    }

    /**
     * 代码解释
     */
    public String explainCode(Long projectId, String code, String sessionId) {
        log.info("Explaining code for session {}", sessionId);
        String filteredCode = guardrailsFilter.filter(code);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return assistant.explainCode(filteredCode);
    }

    /**
     * 生成代码
     */
    public String generateCode(Long projectId, String requirements, String language) {
        log.info("Generating {} code", language);
        String filteredReq = guardrailsFilter.filter(requirements);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return assistant.generateCode(filteredReq, language);
    }

    /**
     * 修复代码
     */
    public String fixCode(Long projectId, String code, String errorMessage) {
        log.info("Fixing code with error: {}", errorMessage);
        String filteredCode = guardrailsFilter.filter(code);
        String filteredError = guardrailsFilter.filter(errorMessage);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return assistant.fixCode(filteredCode, filteredError);
    }

    /**
     * OpenClaw Agent调用
     */
    public String callOpenClaw(Project project, String task) {
        // TODO: 调用Docker容器中的OpenClaw API
        // 使用RestTemplate或WebClient
        log.info("Calling OpenClaw for project {} with task: {}", project.getId(), task);
        return "OpenClaw response";
    }
}
