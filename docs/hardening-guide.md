# Hardening Guide

This project now defaults to a safer baseline. Demo conveniences are opt-in.

## Local development

Use the `dev` profile when you need seeded demo users:

```bash
SEED_DEMO_USERS=true \
MULTIMODAL_AGENT_ADMIN_PASSWORD=admin123456789 \
MULTIMODAL_AGENT_STUDENT_PASSWORD=student123456 \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The passwords must be at least 12 characters.

## Production defaults

- H2 Console is disabled by default.
- Demo users are not seeded unless `SEED_DEMO_USERS=true`.
- MCP tool server is disabled unless `MCP_SERVER_ENABLED=true`.
- MCP tool calls require `X-MCP-Token: <MCP_SERVER_TOKEN>`.
- Excel tool mode defaults to `local`.
- Email alert mode defaults to `log`.
- HTTP clients have configurable connection and response timeouts.
- Knowledge uploads are limited by `KNOWLEDGE_UPLOAD_MAX_BYTES`.
- Multimodal uploads are limited by `MULTIMODAL_UPLOAD_MAX_BYTES`.
- Whisper mock filename-based risk signals are disabled unless the `dev` profile enables them.

## Docker

Copy `.env.example` to `.env` and replace every placeholder before running Compose.

```bash
docker compose up -d mysql redis chroma mailpit
```

MySQL, Redis, Chroma, and Mailpit are bound to `127.0.0.1` by default in Compose.
