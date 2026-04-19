# Feishu Knowledge Sync

This project includes a first-pass Feishu knowledge sync pipeline for RAG ingestion.

Current scope in this version:

- supports Feishu `docx` documents
- supports single Feishu `wiki` pages when the wiki node is backed by `docx`
- supports Feishu `folder` links as discovery roots
- lists files in a Feishu folder and recursively expands nested folders
- follows shortcuts that point to supported `docx` documents or folders
- fetches document raw text through the Feishu Open API
- chunks the text locally
- writes chunks into the configured `VectorStore` / Milvus
- stores metadata such as title, source URL, source token, chunk index and synced time
- persists the last synced chunk ids in a local state file so re-sync can delete old chunks first
- optionally runs sync on application startup

Not yet included:

- full wiki tree sync
- file types like PDF / DOC / PPT parsing
- scheduled incremental sync by Feishu update time
- production database-backed sync registry
- per-tenant or per-knowledge-base isolation

## Required env vars

```powershell
$env:APP_RAG_ENABLED="true"
$env:APP_FEISHU_KNOWLEDGE_ENABLED="true"
$env:APP_FEISHU_BOT_APP_ID="<your-app-id>"
$env:APP_FEISHU_BOT_APP_SECRET="<your-app-secret>"
```

Milvus / vector store:

```powershell
$env:SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST="127.0.0.1"
$env:SPRING_AI_VECTORSTORE_MILVUS_CLIENT_PORT="19530"
$env:SPRING_AI_VECTORSTORE_MILVUS_DATABASE_NAME="default"
$env:SPRING_AI_VECTORSTORE_MILVUS_COLLECTION_NAME="customer_service_knowledge"
```

Optional tuning:

```powershell
$env:APP_FEISHU_KNOWLEDGE_AUTO_SYNC_ON_STARTUP="false"
$env:APP_FEISHU_KNOWLEDGE_CHUNK_SIZE="700"
$env:APP_FEISHU_KNOWLEDGE_CHUNK_OVERLAP="120"
$env:APP_FEISHU_KNOWLEDGE_MAX_CHUNKS_PER_DOCUMENT="200"
$env:APP_FEISHU_KNOWLEDGE_STATE_FILE="build/feishu-knowledge/sync-state.json"
```

Note: vector writes depend on the Spring AI vector store and embedding configuration. If no `VectorStore` bean is available, sync fails fast with a configuration error.

## Manual sync endpoint

```text
POST /api/admin/knowledge/sync/feishu
```

You can either:

- configure `app.feishu.knowledge.sources` in `application.yml` and call the endpoint with an empty body
- send the sources directly in the request body
- set `type` to `auto` and provide a standard Feishu `docx`, `wiki`, or `folder` link
- set `type` to `folder` to expand all supported docs under that folder recursively

Example `docx` request body:

```json
{
  "sources": [
    {
      "type": "auto",
      "title": "Refund Policy",
      "sourceUrl": "https://your-tenant.feishu.cn/docx/doccnxxxxxxxxxxxxxxxxxxxx"
    },
    {
      "token": "doccnyyyyyyyyyyyyyyyyyyyy",
      "type": "docx",
      "title": "Shipping FAQ",
      "sourceUrl": "https://your-tenant.feishu.cn/docx/doccnyyyyyyyyyyyyyyyyyyyy"
    }
  ]
}
```

Example folder request body:

```json
{
  "sources": [
    {
      "type": "folder",
      "sourceUrl": "https://your-tenant.feishu.cn/drive/folder/fldcnxxxxxxxxxxxxxxxxxxxx"
    }
  ]
}
```

Example wiki request body:

```json
{
  "sources": [
    {
      "type": "auto",
      "sourceUrl": "https://your-tenant.feishu.cn/wiki/U807wYNyJWoarkHAZcwNbOne"
    }
  ]
}
```

Example response:

```json
{
  "success": true,
  "requestedSources": 2,
  "syncedSources": 2,
  "syncedChunks": 11,
  "details": [
    {
      "token": "doccnxxxxxxxxxxxxxxxxxxxx",
      "type": "docx",
      "title": "Refund Policy",
      "status": "synced",
      "chunkCount": 5,
      "message": "Synced successfully."
    }
  ]
}
```

## Suggested first run

1. Turn on `APP_RAG_ENABLED=true`.
2. Turn on `APP_FEISHU_KNOWLEDGE_ENABLED=true`.
3. Confirm Milvus connectivity through `GET /api/admin/health/vector`.
4. Prepare either two `docx` links, one `wiki` link backed by `docx`, or one folder link.
5. Call `POST /api/admin/knowledge/sync/feishu` with those sources.
6. After sync succeeds, use `/api/feishu/debug/reply` or console Q&A to verify retrieval.

## Notes

- For better answer citations, provide the real `sourceUrl` from your tenant document link.
- If you re-sync the same document, the service tries to delete previous chunk ids before writing new ones.
- The local sync state file is for development convenience. Production should use a persistent sync registry.
- `doc` links can be parsed as a source type, but the current fetch implementation supports `docx` and `wiki` pages backed by `docx`; legacy doc parsing still needs work.
