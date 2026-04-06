# Spring AI Intelligent Customer Service Starter

这个目录提供一个面向中国大陆环境的智能客服初始化工程，目标组合如下：

- 模型：MiniMax 为默认首选，Qwen 作为可切换备选
- 向量库：Milvus，优先阿里云托管形态
- 用户触达：飞书 Bot / 飞书事件回调
- 框架：Spring Boot + Spring AI

## 当前包含

- Spring Boot + Spring AI Gradle 工程骨架
- 飞书 webhook 接入控制器占位实现
- 模型路由与 RAG 编排服务骨架
- `application.yml` 配置模板
- `.env.example` 环境变量样例
- `docs/DEV_PLAN_FOR_CODEX.md` 开发方案
- `docs/FEASIBILITY_ASSESSMENT.md` 可行性评估

## 启动前提

1. 安装可用的 Gradle，或使用项目内 `gradlew`
2. 配置 JDK 17，并修正系统 `JAVA_HOME`
3. 准备以下能力：
   - DeepSeek API Key 或 DashScope/Qwen 兼容 API Key
   - Milvus 地址、用户名、密码
   - 飞书 Bot 凭据、签名校验参数

## 推荐实施顺序

1. 先打通飞书消息收发
2. 再接入单模型对话
3. 然后接入 Milvus 做知识检索
4. 最后增加工单、升级转人工、指标监控
