# Console Q&A

This repo includes a minimal console Q&A runner so you can verify:
- DeepSeek (or Qwen via OpenAI-compatible) chat calls work
- Optional Milvus vector retrieval can be enabled later

## Run

1. Set env vars (copy `.env.example` to your own `.env` and export as needed):

- `APP_CONSOLE_ENABLED=true`
- `SPRING_AI_DEEPSEEK_API_KEY=...`

2. Start the app:

```powershell
$env:APP_CONSOLE_ENABLED="true"
$env:SPRING_AI_DEEPSEEK_API_KEY="<your-key>"
.\gradlew.bat bootRun
```

3. Type questions in the console. Type `exit` to quit.

## Enable Milvus RAG (optional)

If you have Milvus running and the vector store is reachable:

- `APP_RAG_ENABLED=true`
- `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST=127.0.0.1`
- `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_PORT=19530`

Note: vector search needs embeddings configured in your Spring AI setup. If you hit errors during similarity search, keep `APP_RAG_ENABLED=false` until embeddings + Milvus are ready.
