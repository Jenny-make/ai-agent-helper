# Spring AI Intelligent Customer Service Starter

这个目录提供一个面向中国大陆环境的智能客服初始化工程，目标组合如下：

- 模型：MiniMax 为默认首选，Qwen 作为可切换备选
- 向量库：Milvus，优先阿里云托管形态
- 用户触达：飞书 Bot / 飞书事件回调
- 框架：Spring Boot + Spring AI

## 当前包含

- Spring Boot + Spring AI Gradle 工程骨架
- 多模型接入与路由：
  - `DeepSeek`
  - `MiniMax`
  - `OpenAI-compatible / Qwen`
- 自动排除未配置 provider 的 Spring AI 自动配置，减少多模型并存时的启动问题
- 控制台问答入口，可本地验证模型调用、会话上下文与基础追问
- 基于内存的会话记忆与摘要压缩
- 基础飞书消息闭环：
  - `url_verification`
  - `im.message.receive_v1` 文本消息接收
  - 群聊 `@` 识别与消息清洗
  - 重复投递去重
  - 通过飞书 Open API 回发文本消息
- 可选 RAG 检索入口：
  - `VectorStore` 检索接入
  - Milvus 配置模板
  - 向量库健康检查接口
- `application.yml` 配置模板
- `.env.example` 环境变量样例
- `docs/DEV_PLAN_FOR_CODEX.md` 开发方案
- `docs/FEASIBILITY_ASSESSMENT.md` 可行性评估
- `docs/CONSOLE_QA.md` 控制台联调说明
- `docs/FEISHU_WEBHOOK.md` 飞书接入说明
- `docs/FEISHU_KNOWLEDGE_SYNC.md` 飞书文档同步入库说明

## 当前状态

当前版本已经具备“控制台问答 + 飞书文本消息问答”的最小闭环，但仍处于 `0.1.0-SNAPSHOT` 阶段。

已完成：

- 多模型接入与自动 provider 选择
- 控制台问答调试链路
- 飞书基础文本消息收发闭环
- 会话记忆、追问消歧、语言切换修复
- RAG 读取侧接入点
- 飞书 `docx / folder -> chunk -> Milvus` 的第一版同步链路

尚未完成：

- 飞书加密事件解密与更严格的签名校验
- 文档导入、切片、embedding、Milvus 入库闭环
- 转人工、工单、审计日志、持久化会话状态

## 启动前提

1. 安装可用的 Gradle，或使用项目内 `gradlew`
2. 配置 JDK 17，并修正系统 `JAVA_HOME`
3. 准备以下能力：
   - 至少一种模型 API Key：
     - DeepSeek
     - MiniMax
     - DashScope/Qwen 兼容 OpenAI 接口
   - 若启用 RAG：Milvus 地址、用户名、密码
   - 若启用飞书：飞书 Bot 凭据、校验参数

## 推荐实施顺序

1. 完成知识文档导入、切片、embedding 与 Milvus 入库闭环
2. 补齐 RAG 引用输出与未命中兜底策略
3. 增强飞书安全能力，包括加密事件与签名校验
4. 最后增加转人工、工单、审计与监控
