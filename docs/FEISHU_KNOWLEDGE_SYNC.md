# Feishu Knowledge Sync

This project now includes a first-pass Feishu knowledge sync pipeline for RAG ingestion.

Current scope in this version:

- supports Feishu `docx` documents
- supports Feishu `folder` links as discovery roots
- fetches document raw text through the Feishu Open API
- lists files in a Feishu folder and recursively expands nested folders
- chunks the text locally
- writes chunks into the configured `VectorStore` / Milvus
- persists the last synced chunk ids in a local state file so re-sync can delete old chunks first

Not yet included:

- wiki tree sync
- file types like PDF / DOC / PPT parsing
- scheduled incremental sync by Feishu update time

## Required env vars

```powershell
$env:APP_RAG_ENABLED="true"
$env:APP_FEISHU_KNOWLEDGE_ENABLED="true"
$env:APP_FEISHU_BOT_APP_ID="<your-app-id>"
$env:APP_FEISHU_BOT_APP_SECRET="<your-app-secret>"
```

Optional tuning:

```powershell
$env:APP_FEISHU_KNOWLEDGE_CHUNK_SIZE="700"
$env:APP_FEISHU_KNOWLEDGE_CHUNK_OVERLAP="120"
$env:APP_FEISHU_KNOWLEDGE_STATE_FILE="build/feishu-knowledge/sync-state.json"
```

## Manual sync endpoint

```text
POST /api/admin/knowledge/sync/feishu
```

You can either:

- configure `app.feishu.knowledge.sources` in `application.yml` and call the endpoint with an empty body
- or send the sources directly in the request body
- `token` is optional when `sourceUrl` is a standard Feishu `docx` link; the service can infer it from the URL
- `type` can be `folder` to expand all supported docs under that folder recursively

Example request body:

```json
{
  "sources": [
    {
      "token": "doccnxxxxxxxxxxxxxxxxxxxx",
      "type": "docx",
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

1. Turn on `APP_RAG_ENABLED=true`
2. Turn on `APP_FEISHU_KNOWLEDGE_ENABLED=true`
3. Confirm Milvus connectivity through `GET /api/admin/health/vector`
4. Prepare either two `docx` links or one folder link
5. Call `POST /api/admin/knowledge/sync/feishu` with those sources
6. After sync succeeds, use `/api/feishu/debug/reply` or console Q&A to verify retrieval

## Notes

- For the best answer citations, provide the real `sourceUrl` from your tenant doc link.
- If you re-sync the same document, the service will try to delete the previous chunk ids before writing new ones.
- The local sync state file is for development convenience. Production usually needs a persistent sync registry.
