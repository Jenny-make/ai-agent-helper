# Codex Development Plan

## Goal

基于 Spring AI 开发一个企业智能客服系统，满足以下约束：

- 中国大陆网络环境可稳定接入
- 模型优先使用 DeepSeek，备用使用 Qwen
- 向量检索使用 Milvus
- 用户触达使用飞书
- 代码结构要适合 Codex 持续接手开发

## Architecture

### Core modules

1. `controller`
   - 接收飞书事件
   - 提供调试与管理接口
2. `service`
   - 统一封装对话编排
   - 负责知识检索、回复生成、转人工判定
3. `config`
   - 统一管理模型、飞书、安全相关配置
4. `model`
   - 收敛请求响应对象

### Request flow

1. 飞书把用户消息投递到 `/api/feishu/webhook`
2. 服务层解析用户、会话、问题文本
3. 先在 Milvus 做相似检索
4. 把检索片段与系统提示词拼接到模型请求
5. 返回答案，并记录日志、引用、风险标记
6. 若命中规则，触发转人工或工单

## Recommended milestones

### Milestone 1

- 打通飞书事件校验
- 打通单模型对话
- 输出文本回复

### Milestone 2

- 引入知识库文档导入
- Milvus 检索增强回答
- 返回引用片段

### Milestone 3

- 增加敏感问题规则
- 增加未命中转人工
- 增加会话状态持久化

### Milestone 4

- 增加监控、审计、灰度切换
- 接入工单系统
- 支持多知识库、多租户

## Coding rules for Codex

- 优先保持 `DeepSeek` 为默认 provider，`Qwen` 作为配置切换
- 不要把飞书事件协议细节散落到多个类中
- RAG 相关改动优先收敛到 `KnowledgeAnswerService`
- 新增外部平台接入时，先抽象接口，再落地实现
- 每次改动同时更新 `README.md` 和配置模板

## Immediate next tasks

1. 增加飞书签名验签与事件对象反序列化
2. 增加知识文档导入接口与切分流程
3. 接入 Milvus 检索结果到 prompt
4. 增加回复后的飞书消息发送能力
5. 增加失败重试与幂等控制
