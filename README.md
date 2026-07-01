一个集成智能体能力的云端 AI＿Coding 平台。用户通过 GitHub 一键登录后即可从 GitHub 仓库拉取项目到云端工作区，平台提供大模型对话能力，帮助用户在编写、理解、重构和排错过程中获得即时的智能辅助，后续也可切换接入 OpenClaw 等 Agent 以实现更强的自动化编程与改动执行能力；系统可自动将最后修改提交到云端并与用户的 GitHub 仓库保持同步。该项目的核心价值在于把“代码资产管理 ＋ 云端编辑 ＋ AI 助手/代理”整合为一体，让开发者无需配置复杂本地环境即可随时随地在浏览器里高效完成编码、问答与迭代，并以 GitHub 为代码源实现可靠的版本与协作闭环。

```bash
# 前端依赖安装
npm install

# 前端项目启动
npm run dev
```

## Agent Engineering

The backend now builds one cached LangChain4j Assistant per project. Each Assistant uses the same project-scoped embedding store for ingestion and retrieval, Markdown-driven system prompt modules, progressively disclosed Skills, tiered chat-memory compaction, long-term JPA memory, and file/Git/OpenSandbox tools.

Harness controls are deterministic infrastructure rather than prompt-only rules:

- Workspace Hook: rejects absolute paths, traversal, symbolic-link escapes, control-file mutation, oversized writes, and deletion unless explicitly enabled.
- Verification Hook: code mutations request syntax/build/test execution from the configured Sandbox Gateway and return PASS/FAIL evidence to the Agent.
- HEARTBEAT: `/api/ai/harness/heartbeat` reports active/stale runs, violations, verification results, LLM queue depth, circuit state, and DLQ size.
- LLM resilience: fair `Semaphore`, expiring priority queue, retry with exponential backoff, circuit breaker, and bounded DLQ.

Copy `src/main/resources/application-example.yml` to a local ignored configuration file and supply secrets through environment variables. The Sandbox Gateway contract accepts `POST /api/sandbox/execute` for snippets and `POST /api/sandbox/verify` for workspace commands; generated code is never executed directly on the Spring Boot host.

Milvus is optional at build time so the zero-dependency demo remains usable:

```bash
# In-memory RAG
mvn spring-boot:run

# Milvus HNSW + COSINE implementation
AI_RAG_STORE=milvus mvn -Pmilvus spring-boot:run
```
