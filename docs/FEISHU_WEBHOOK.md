# Feishu Webhook

This project now supports a basic real Feishu message loop:

1. Receive Feishu event callback
2. Validate the configured verification token
3. Parse `im.message.receive_v1` text messages
4. Ignore group messages without an explicit `@` mention
5. Deduplicate repeated deliveries in a short time window
6. Build a conversation-oriented `sessionId`
7. Call `KnowledgeAnswerService`
8. Reply to the original Feishu message through the Feishu Open API

## Current scope

Supported in this version:

- `url_verification`
- `im.message.receive_v1`
- `text` message parsing
- `p2p` direct chat auto reply
- group message reply only when the bot is explicitly mentioned
- basic `@name` / mention metadata sanitization
- in-memory duplicate delivery suppression
- reply to the original message through `/im/v1/messages/{message_id}/reply`

Not yet included in this version:

- encrypted event body decryption
- advanced request signature validation
- non-text message types
- image/file/card replies

## Required env vars

```powershell
$env:APP_FEISHU_BASE_URL="https://open.feishu.cn/open-apis"
$env:APP_FEISHU_VERIFICATION_TOKEN="<your-verification-token>"
$env:APP_FEISHU_BOT_APP_ID="<your-app-id>"
$env:APP_FEISHU_BOT_APP_SECRET="<your-app-secret>"
```

Optional:

```powershell
$env:APP_AI_PROVIDER="minimax"
$env:SPRING_AI_MINIMAX_API_KEY="<your-model-key>"
```

## Local run

```powershell
cd D:\workspace\spring-ai-feishu-cs-starter

$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat --no-daemon --console=plain bootRun
```

## Webhook endpoint

```text
POST /api/feishu/webhook
```

## Feishu event subscription

In your Feishu app settings:

- event callback URL -> point to `/api/feishu/webhook`
- subscribe to message receive events
- use the same verification token as `APP_FEISHU_VERIFICATION_TOKEN`

## Debug endpoint

You can still test the answer pipeline locally without Feishu:

```text
POST /api/feishu/debug/reply
```

Example JSON:

```json
{
  "userId": "debug-user",
  "sessionId": "debug-session",
  "text": "Hello"
}
```
