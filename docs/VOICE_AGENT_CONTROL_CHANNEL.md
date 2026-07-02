# LiveKit Agent Control Channel

This project keeps the browser, Spring Boot backend, and external LiveKit Agent loosely coupled.
The backend owns user/session/risk/RAG state. The LiveKit Agent owns realtime media: ASR, TTS playback,
and barge-in handling.

## Runtime Flow

1. Browser calls `POST /api/voice/sessions`.
2. Backend creates a LiveKit room token and registers a voice runtime session.
3. Browser subscribes to `GET /api/voice/sessions/{roomName}/events`.
4. LiveKit Agent joins the same room and subscribes to `GET /api/voice/agent/sessions/{roomName}/commands`.
5. Agent streams ASR status through `POST /api/voice/agent/events`.
6. Agent can either call Doubao directly or proxy audio through `POST /api/voice/agent/asr/doubao`.
7. Agent submits final ASR text to `POST /api/voice/agent/transcripts`.
8. Backend reuses the existing `ChatService` Qwen/RAG/SSE pipeline and returns assistant tokens as SSE.
9. Agent can either call the configured TTS vendor directly or proxy text through `POST /api/voice/agent/tts`.
10. Agent plays TTS audio in LiveKit and stops playback when it receives `interrupt_tts`.
11. Browser sees the same transcript, assistant tokens, status, and done events.

## Browser Endpoints

### Create Session

`POST /api/voice/sessions`

```json
{
  "sessionId": "optional-existing-chat-session",
  "knowledgeScope": "student-support",
  "supportGoal": "current-dialog"
}
```

### Subscribe Room Events

`GET /api/voice/sessions/{roomName}/events`

Returns `text/event-stream` with `VoiceControlEvent` payloads.

### Interrupt TTS

`POST /api/voice/sessions/{roomName}/interrupt`

```json
{
  "command": "interrupt_tts",
  "reason": "student-barge-in"
}
```

### End Session

`POST /api/voice/sessions/{roomName}/end`

```json
{
  "command": "end_session",
  "reason": "student-ended"
}
```

## LiveKit Agent Endpoints

The Agent should authenticate with the same student session credentials for the current room in this demo setup.
For production, replace this with a service account or signed agent token.

### Subscribe Commands

`GET /api/voice/agent/sessions/{roomName}/commands`

Important commands:

- `interrupt_tts`: immediately stop current TTS playback and keep listening.
- `end_session`: disconnect from the LiveKit room and release local ASR/TTS resources.

### Publish Agent Status

`POST /api/voice/agent/events`

```json
{
  "roomName": "mindcare-3-student-support-xxxx",
  "type": "tts_started",
  "phase": "tts",
  "text": "playback started"
}
```

Useful `type` values: `asr_started`, `asr_partial`, `tts_started`, `tts_stopped`, `barge_in_detected`, `agent_error`.

### Doubao ASR Proxy

`POST /api/voice/agent/asr/doubao`

```json
{
  "roomName": "mindcare-3-student-support-xxxx",
  "sessionId": "optional-chat-session-id",
  "audioBase64": "base64 encoded pcm or wav chunk",
  "format": "pcm",
  "sampleRate": 16000,
  "language": "zh-CN",
  "finalTranscript": true
}
```

Response:

```json
{
  "provider": "doubao",
  "model": "doubao-streaming-asr",
  "text": "I have not slept well recently.",
  "confidence": 0.92,
  "finalTranscript": true
}
```

The backend also broadcasts a `transcript` event to browser and Agent subscribers.

### Submit ASR Transcript

`POST /api/voice/agent/transcripts`

Partial transcript:

```json
{
  "roomName": "mindcare-3-student-support-xxxx",
  "text": "I feel",
  "finalTranscript": false
}
```

Final transcript:

```json
{
  "roomName": "mindcare-3-student-support-xxxx",
  "sessionId": "optional-chat-session-id",
  "text": "I feel stressed and cannot sleep.",
  "finalTranscript": true
}
```

Final transcript responses are also `text/event-stream`. The Agent should stream `assistant_token` values into the configured TTS provider.

### TTS Proxy

Recommended endpoint:

`POST /api/voice/agent/tts`

Provider-specific compatibility endpoints:

- `POST /api/voice/agent/tts/doubao`
- `POST /api/voice/agent/tts/minimax`

```json
{
  "roomName": "mindcare-3-student-support-xxxx",
  "sessionId": "optional-chat-session-id",
  "text": "I hear that you have been under a lot of pressure lately. Let's slow down and look at tonight first.",
  "voice": "your-doubao-voice-id",
  "format": "mp3"
}
```

Response:

```json
{
  "provider": "doubao",
  "model": "doubao-tts",
  "voice": "your-doubao-voice-id",
  "format": "mp3",
  "audioBase64": "base64 encoded audio",
  "byteLength": 12345
}
```

The backend broadcasts `phase=tts` before synthesis and `tts_ready` after audio is ready.

## Event Schema

```json
{
  "type": "assistant_token",
  "roomName": "mindcare-3-student-support-xxxx",
  "sessionId": "chat-session-id",
  "command": null,
  "phase": "llm",
  "text": null,
  "token": "partial assistant text",
  "finalTranscript": null,
  "sequence": 12,
  "at": "2026-06-28T05:45:20.482Z"
}
```

## Minimal Agent Loop

```text
join LiveKit room with livekitToken
connect Doubao streaming ASR to participant audio
subscribe /api/voice/agent/sessions/{room}/commands
on ASR partial -> POST /api/voice/agent/transcripts finalTranscript=false
optional ASR proxy -> POST /api/voice/agent/asr/doubao with base64 audio
on ASR final -> POST /api/voice/agent/transcripts finalTranscript=true and read SSE
on assistant_token -> append text, then POST /api/voice/agent/tts
on TTS response -> decode audioBase64 and publish audio into LiveKit room
on interrupt_tts -> stop current TTS playback immediately
on end_session -> disconnect room
```

## Environment Variables

```text
VOICE_ENABLED=true
LIVEKIT_URL=wss://your-livekit-host
LIVEKIT_API_KEY=...
LIVEKIT_API_SECRET=...

VOICE_ASR_PROVIDER=doubao
VOICE_ASR_ENDPOINT=wss-or-https://your-doubao-asr-endpoint
VOICE_ASR_API_KEY=your-volcengine-asr-access-key
VOICE_ASR_APP_ID=your-volcengine-asr-app-key
VOICE_ASR_CLUSTER=your-volcengine-asr-resource-id
VOICE_ASR_FORMAT=pcm
VOICE_ASR_SAMPLE_RATE=16000
VOICE_ASR_LANGUAGE=zh-CN

VOICE_TTS_PROVIDER=doubao
VOICE_TTS_ENDPOINT=wss://openspeech.bytedance.com/api/v1/tts/ws_binary
VOICE_TTS_API_KEY=your-volcengine-api-key
VOICE_TTS_RESOURCE_ID=seed-tts-2.0
VOICE_TTS_MODEL=doubao-tts
VOICE_TTS_VOICE=your-doubao-voice-id
VOICE_TTS_AUDIO_FORMAT=mp3
VOICE_TTS_SAMPLE_RATE=32000
```

For Doubao TTS, the backend sends these Volcengine-style headers:

- `X-Api-Key`: `VOICE_TTS_API_KEY`
- `X-Api-Resource-Id`: `VOICE_TTS_RESOURCE_ID`, for example `seed-tts-2.0` or `seed-icl-2.0`
- `X-Api-Request-Id`: generated per request

Backward-compatible aliases are still accepted:

- `VOICE_TTS_GROUP_ID` -> `VOICE_TTS_RESOURCE_ID`
- `VOICE_TTS_ACCESS_KEY` -> `VOICE_TTS_API_KEY`
