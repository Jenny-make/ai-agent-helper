# Reference Links

## Spring AI

- Spring AI DeepSeek reference: https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html
- Spring AI OpenAI-compatible model reference: https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
- Spring AI Milvus vector store reference: https://docs.spring.io/spring-ai/reference/api/vectordbs/milvus.html

## Model vendors

- DeepSeek API docs: https://api-docs.deepseek.com/
- Alibaba Cloud DashScope OpenAI compatible mode: https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope

## Cloud vector database

- Alibaba Cloud Milvus: https://www.alibabacloud.com/help/en/milvus
- Tencent Cloud VectorDB: https://www.tencentcloud.com/products/vdb

## Feishu

- Feishu custom bot guide: https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot
- Feishu message send API reference: https://open.feishu.cn/document/server-docs/im-v1/message/create

## Notes

- 推荐以 DeepSeek 作为 Spring AI 默认主模型。
- 若使用 Qwen，优先走 DashScope 的 OpenAI 兼容接口。
- 若坚持“云上托管 Milvus”，优先选阿里云；腾讯云更适合作为 VectorDB 路线，而不是托管 Milvus。
