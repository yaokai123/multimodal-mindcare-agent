# 本地微调模型整合说明

本项目当前已经按“项目内 GGUF + Ollama + Spring AI + 语音链路”的方式整合。

## 模型位置

当前微调模型文件位于：

```text
mindbridge-qwen2.5-7b-ft-q4_k_m.gguf/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

同目录下的 `Modelfile` 会把该 GGUF 注册为：

```text
multimodalAgent-qwen2.5-7b-ft:latest
```

后端默认配置已经指向该模型名：

```yaml
multimodal-agent:
  ai:
    provider: ${AI_PROVIDER:ollama}
    ollama:
      model: ${OLLAMA_MODEL:multimodalAgent-qwen2.5-7b-ft:latest}
```

## Windows 一键启动

```powershell
cd D:\mutilagent
.\scripts\start-integrated-dev.ps1
```

该脚本会：

1. 查找 `ollama.exe`。
2. 若 Ollama 未运行，自动启动 Ollama。
3. 注册项目内 GGUF 微调模型。
4. 设置 `AI_PROVIDER=ollama` 和 `OLLAMA_MODEL=multimodalAgent-qwen2.5-7b-ft:latest`。
5. 调用现有 `scripts/start-voice-dev.ps1` 启动 Spring Boot、LiveKit、Doubao ASR、Doubao TTS 链路。

只注册模型、不启动后端：

```powershell
.\scripts\register-local-model.ps1
```

强制重新注册模型：

```powershell
.\scripts\register-local-model.ps1 -Force
```

## macOS / Linux

```bash
./scripts/create-finetuned-model.sh
./scripts/run-dev.sh
```

`run-dev.sh` 会优先使用当前项目根目录下的 `mindbridge-qwen2.5-7b-ft-q4_k_m.gguf/Modelfile`，如果不存在才回退到旧的 `models/multimodalAgent-qwen2.5-7b-ft/Modelfile`。

## Docker Compose

`docker-compose.yml` 已包含 `ollama` 服务，并把当前项目内的 GGUF 目录挂载到容器：

```text
./mindbridge-qwen2.5-7b-ft-q4_k_m.gguf:/models/mindbridge:ro
```

首次启动依赖后，在 Ollama 容器中注册模型：

```powershell
docker compose up -d ollama mysql redis chroma mailpit
docker exec -w /models/mindbridge multimodalAgent-ollama ollama create multimodalAgent-qwen2.5-7b-ft:latest -f Modelfile
docker compose up -d app
```

容器内应用默认使用：

```text
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=multimodalAgent-qwen2.5-7b-ft:latest
```

## 验证

确认 Ollama 已注册模型：

```powershell
ollama list
```

确认后端当前调用微调模型：

```powershell
$auth = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("student:student123456"))
Invoke-RestMethod -Uri http://localhost:8080/api/agent/status -Headers @{Authorization=$auth}
```

期望返回：

```text
provider = ollama
model = multimodalAgent-qwen2.5-7b-ft:latest
realModelEnabled = true
```
