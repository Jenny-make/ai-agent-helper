# Spring AI Intelligent Customer Service Starter

这个仓库是一个面向中国大陆环境的智能客服初始化工程，目标是用 Spring Boot + Spring AI 搭出“控制台调试 + 飞书 Bot + 企业知识库检索”的第一版客服基座。

技术组合：

- 模型：MiniMax / DeepSeek / OpenAI-compatible（可用于 DashScope/Qwen）
- 向量库：Milvus，通过 Spring AI `VectorStore` 接入
- 用户触达：飞书 Bot / 飞书事件回调
- 框架：Spring Boot 3.4.x + Spring AI 1.0.x + Java 17

## 当前进展快照

更新时间：2026-04-19

项目仍处于早期开发阶段。`build.gradle` 当前版本号为 `0.0.1-SNAPSHOT`，文档和里程碑按 `0.1.0-SNAPSHOT` 收尾目标组织。

当前已经具备：

- 本地控制台问答入口，可验证模型调用、会话上下文、追问和语言切换。
- 多模型 provider 路由，支持 `auto`、`minimax`、`deepseek`、`openai/qwen`。
- 自动排除未启用 provider 的 Spring AI 自动配置，降低多 provider 并存时的启动失败概率。
- 飞书文本消息最小闭环：`url_verification`、`im.message.receive_v1`、群聊 `@` 识别、消息清洗、重复投递去重、租户 token 缓存、文本回复。
- `/new` 会话重置命令，飞书侧会清理当前会话记忆和当前激活文档。
- 会话记忆：默认内存实现；配置 `DataSource` 后自动使用 JDBC 持久化，并通过 Flyway 创建表。
- 活跃文档状态：支持基于当前 session 记录最近命中的知识文档，配置数据库后可持久化。
- RAG 读取侧：可选启用 `VectorStore` 检索、将检索片段注入回答、返回引用来源、提供向量库健康检查。
- 飞书知识同步第一版：支持 `docx`、由 `docx` 承载的 `wiki` 页面、飞书文件夹递归发现 `docx` 和快捷方式，完成文本拉取、切片、写入 `VectorStore/Milvus`、同步状态文件记录。
- 管理接口：`GET /api/admin/health/vector`、`POST /api/admin/knowledge/sync/feishu`。
- 单元测试覆盖飞书消息过滤与去重、`/new`、知识同步、会话记忆持久化、文档问答策略、语言与 prompt 清洗等核心路径。

当前主要限制：

- 飞书加密事件体解密和更严格的请求签名校验尚未完成。
- 飞书消息目前只处理文本，暂不支持图片、文件、卡片等消息类型。
- 知识同步仍是第一版，尚未支持 PDF / DOC / PPT 解析、完整 wiki 树同步、按更新时间增量同步。
- 知识同步状态文件适合开发验证，生产环境需要持久化同步注册表。
- RAG 入库依赖可用的 Spring AI embedding / Milvus 配置；没有向量库和 embedding 能力时只能使用普通模型问答。
- 转人工、工单、审计日志、限流、多租户、多知识库隔离、监控告警仍未实现。

## 启动前提

1. 安装 JDK 17。
2. 使用项目内 `gradlew` / `gradlew.bat`，或准备本机 Gradle。
3. 至少配置一种模型 API Key：
   - MiniMax
   - DeepSeek
   - DashScope/Qwen 的 OpenAI-compatible 接口
4. 若启用 RAG 和知识同步，需要准备 Milvus 与 embedding 相关配置。
5. 若启用飞书，需要准备飞书应用的 `app_id`、`app_secret`、事件订阅校验参数。
6. 若需要持久化会话，需要配置 MySQL 数据源，Flyway 会创建 `conversation_thread`、`conversation_message`、`active_document` 表。

## 主要文档

- [docs/DEV_PLAN_FOR_CODEX.md](docs/DEV_PLAN_FOR_CODEX.md)：阶段目标、架构分层和下一步计划
- [docs/CHANGELOG.md](docs/CHANGELOG.md)：项目进展记录
- [docs/CONSOLE_QA.md](docs/CONSOLE_QA.md)：控制台问答联调
- [docs/FEISHU_WEBHOOK.md](docs/FEISHU_WEBHOOK.md)：飞书消息闭环接入
- [docs/FEISHU_KNOWLEDGE_SYNC.md](docs/FEISHU_KNOWLEDGE_SYNC.md)：飞书知识同步到 Milvus
- [docs/FEASIBILITY_ASSESSMENT.md](docs/FEASIBILITY_ASSESSMENT.md)：技术路线可行性评估
- [docs/REFERENCE_LINKS.md](docs/REFERENCE_LINKS.md)：参考链接

## 推荐下一步

1. 补齐飞书加密事件解密与请求签名校验。
2. 强化 RAG 输出：引用片段展示、未命中兜底、低置信度处理。
3. 将飞书知识同步状态从本地 JSON 文件迁移到数据库。
4. 增加更多文件类型解析和 wiki 树级同步。
5. 增加转人工、工单、审计日志、限流和监控。
