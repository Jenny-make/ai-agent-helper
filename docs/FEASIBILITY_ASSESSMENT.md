# Feasibility Assessment

## Conclusion

这个方案可行，推荐路线如下：

- 模型：`DeepSeek` 作为主路线，`Qwen` 作为备选或双活
- 向量库：优先阿里云 Milvus
- 触达：飞书自建应用或群机器人
- 技术栈：`Spring Boot 3.x + Spring AI 1.0 + Milvus + Feishu Open Platform`

## Why this is feasible

### Model

- Spring AI 已提供 DeepSeek 集成，接入成本低
- Qwen 可通过 OpenAI 兼容接口接入 Spring AI
- 两者都更符合中国大陆访问环境

### Vector store

- Spring AI 已提供 Milvus Vector Store
- Milvus 对 RAG 场景成熟
- 如果要求云上托管，阿里云路线更顺

### Feishu

- 飞书开放平台支持事件订阅、机器人消息发送、Webhook
- 智能客服可以先从“收到消息 -> 生成答案 -> 回发消息”闭环开始

## Risk points

1. 腾讯云没有看到明确的官方托管 Milvus 产品，若必须用腾讯云，需要评估自建 Milvus 或替换为 Tencent Cloud VectorDB
2. 飞书事件协议、签名、权限模型需要较细致的对接
3. RAG 效果主要取决于知识切分、召回策略和 prompt 约束，不是单纯换模型即可解决
4. 大模型供应商接口限流、计费和模型变更需要抽象隔离

## Recommended deployment

1. 应用服务部署在中国大陆可访问的云主机
2. 模型优先 DeepSeek API，备用 DashScope/Qwen
3. 向量库优先阿里云 Milvus
4. 飞书通过开放平台事件推送触发客服链路
