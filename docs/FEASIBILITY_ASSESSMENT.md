# Feasibility Assessment

更新时间：2026-04-19

## Conclusion

当前技术路线仍然可行，并且已经完成了第一阶段验证：

- 模型：`MiniMax / DeepSeek / OpenAI-compatible` 均已接入，Qwen 可通过 DashScope OpenAI-compatible 接口使用。
- 向量库：Milvus 已通过 Spring AI `VectorStore` 接入，支持检索和第一版写入链路。
- 触达：飞书文本消息已经形成最小收发闭环。
- 知识同步：飞书 `docx / wiki(docx-backed) / folder` 到 `VectorStore/Milvus` 的第一版链路已经落地。
- 会话：已具备内存和数据库两种会话记忆模式。

这意味着项目已经不是纸面方案，而是一个可在本地和飞书侧继续联调的 MVP 基座。

## Why this is feasible

### Model

- Spring AI 已支持 DeepSeek、MiniMax 和 OpenAI-compatible 模型接入。
- Qwen 可通过 DashScope OpenAI-compatible 接口接入。
- `app.ai.provider=auto` 能按可用模型 bean 做自动选择，也支持显式指定 provider。
- 供应商切换已经集中在配置和模型选择逻辑中，后续替换成本可控。

### Vector store

- Spring AI 提供 Milvus Vector Store。
- 当前项目已经有向量库健康检查、RAG 检索、检索证据注入回答流程。
- 飞书知识同步已经能把文档切片写入 `VectorStore/Milvus`。
- 如需云上托管 Milvus，阿里云路线仍更顺；如果必须使用腾讯云，更适合单独评估 Tencent Cloud VectorDB 路线。

### Feishu

- 飞书事件回调、`url_verification`、文本消息接收和文本回复已验证为可实现路径。
- 租户访问令牌获取与缓存已经实现。
- p2p 和群聊 `@` 场景已经拆分处理。
- 后续主要风险集中在安全校验、更多消息类型和生产可观测性，而不是基础闭环不可行。

### Knowledge ingestion

- 飞书 `docx` 原文读取已实现。
- 单个 wiki 页面可先解析到底层 `docx` 再读取。
- 文件夹可作为发现入口，递归展开 `docx`、嵌套文件夹和快捷方式。
- 本地切片和重复同步删除旧 chunk ids 已具备。

## Remaining risk points

1. 飞书加密事件体解密和签名校验仍是上线前必须补齐的安全项。
2. RAG 效果主要取决于切片、embedding、召回阈值、引用输出和 prompt 约束，需要持续调参，而不是单纯替换模型。
3. 当前同步状态文件适合开发验证，生产环境需要数据库化，避免多实例和部署重启带来的状态不一致。
4. 非 `docx` 文件解析、完整 wiki 树同步、按更新时间增量同步尚未完成。
5. 大模型供应商的限流、计费、模型变更和网络可达性需要继续通过 provider 抽象和配置治理隔离。
6. 企业客服需要的转人工、工单、审计、权限、限流和监控仍未实现。

## Recommended deployment direction

1. 应用服务部署在中国大陆可稳定访问的云主机或容器环境。
2. 模型优先选一个主 provider，例如 MiniMax 或 DeepSeek；Qwen/DashScope 作为可切换备选。
3. 向量库优先使用 Milvus，并提前确认 embedding 模型和维度配置。
4. 会话状态、活跃文档和知识同步状态都应迁移到数据库，避免多实例不一致。
5. 飞书接入上线前先完成加密事件解密、签名校验、限流和异常告警。
