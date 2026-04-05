# Console Q&A

This repo includes a minimal console Q&A runner so you can verify:
- Chat calls work (DeepSeek / MiniMax / OpenAI-compatible)
- Optional Milvus vector retrieval can be enabled later

## Run (MiniMax)

```powershell
cd D:\workspace\spring-ai-feishu-cs-starter

$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

$env:APP_CONSOLE_ENABLED="true"
$env:SPRING_PROFILES_ACTIVE="minimax"
$env:SPRING_AI_MINIMAX_API_KEY="<your-key>"

.\gradlew.bat --no-daemon --console=plain bootRun
```

Type your question in the console. Type `exit` to quit.

## Run (DeepSeek)

```powershell
cd D:\workspace\spring-ai-feishu-cs-starter

$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

$env:APP_CONSOLE_ENABLED="true"
$env:SPRING_AI_DEEPSEEK_API_KEY="<your-key>"

.\gradlew.bat --no-daemon --console=plain bootRun
```

## Enable Milvus RAG (optional)

If you have Milvus running and the vector store is reachable:

- `APP_RAG_ENABLED=true`
- `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST=127.0.0.1`
- `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_PORT=19530`

Note: vector search needs embeddings configured in your Spring AI setup. If you hit errors during similarity search, keep `APP_RAG_ENABLED=false` until embeddings + Milvus are ready.

