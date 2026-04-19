# Console Q&A

控制台问答用于在没有飞书回调的情况下验证核心回答链路。

可以验证：

- Chat 调用是否可用（DeepSeek / MiniMax / OpenAI-compatible）。
- provider 自动选择或显式选择是否符合预期。
- 会话记忆、追问、语言切换、prompt 清洗等回答策略。
- 在 RAG 和 Milvus 可用时，验证知识检索增强回答。

## Run (MiniMax)

```powershell
cd D:\workspace\spring-ai-feishu-cs-starter

$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

$env:APP_CONSOLE_ENABLED="true"
$env:SPRING_PROFILES_ACTIVE="minimax"
$env:APP_AI_PROVIDER="minimax"
$env:SPRING_AI_MINIMAX_API_KEY="<your-key>"

.\gradlew.bat --no-daemon --console=plain bootRun
```

Type your question in the console. Type `exit` or `quit` to stop.

Console commands:

- `/new` start a fresh session
- `/history` show recent turns and summary for the current session
- `/session` show current session id
- `/help` show available commands

## Run (DeepSeek)

```powershell
cd D:\workspace\spring-ai-feishu-cs-starter

$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

$env:APP_CONSOLE_ENABLED="true"
$env:APP_AI_PROVIDER="deepseek"
$env:SPRING_AI_DEEPSEEK_API_KEY="<your-key>"

.\gradlew.bat --no-daemon --console=plain bootRun
```

## Enable Milvus RAG (optional)

If Milvus and embedding configuration are ready:

```powershell
$env:APP_RAG_ENABLED="true"
$env:SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST="127.0.0.1"
$env:SPRING_AI_VECTORSTORE_MILVUS_CLIENT_PORT="19530"
$env:SPRING_AI_VECTORSTORE_MILVUS_DATABASE_NAME="default"
$env:SPRING_AI_VECTORSTORE_MILVUS_COLLECTION_NAME="customer_service_knowledge"
```

Then sync Feishu knowledge first, or make sure the Milvus collection already contains documents. See `docs/FEISHU_KNOWLEDGE_SYNC.md`.

Note: vector search and vector writes depend on Spring AI embedding / vector store beans. If startup or similarity search fails, keep `APP_RAG_ENABLED=false` until embedding and Milvus are configured.

## Session memory

Console Q&A uses the same `ConversationMemoryService` as the Feishu channel.

Default behavior:

- Without a configured `DataSource`, memory is kept in process.
- With a configured `DataSource` and transaction manager, memory is persisted in `conversation_thread` and `conversation_message`.

Useful env vars:

- `APP_MEMORY_ENABLED=true`
- `APP_MEMORY_MAX_TURNS=6`
- `APP_MEMORY_MAX_CHARS_PER_MESSAGE=1200`
- `APP_MEMORY_PROMPT_RECENT_TURNS=4`
- `APP_MEMORY_MAX_SUMMARY_CHARS=1200`
- `APP_CONSOLE_TIMEOUT_SECONDS=120`
- `APP_CONSOLE_CHARSET=GBK` on classic Windows PowerShell if Chinese input looks garbled; use `UTF-8` on terminals already configured for UTF-8
