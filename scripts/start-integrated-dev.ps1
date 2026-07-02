param(
    [string]$ModelName = $env:OLLAMA_MODEL,
    [string]$OllamaBin = $env:OLLAMA_BIN,
    [switch]$SkipModelRegister
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if ([string]::IsNullOrWhiteSpace($ModelName)) {
    $ModelName = "multimodalAgent-qwen2.5-7b-ft:latest"
}

function Resolve-Ollama {
    param([string]$Candidate)
    if (-not [string]::IsNullOrWhiteSpace($Candidate) -and (Test-Path -LiteralPath $Candidate)) {
        return (Resolve-Path -LiteralPath $Candidate).Path
    }
    $command = Get-Command ollama -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $default = Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe"
    if (Test-Path -LiteralPath $default) {
        return $default
    }
    throw "Cannot find ollama.exe. Install Ollama or set OLLAMA_BIN."
}

$Ollama = Resolve-Ollama $OllamaBin
$env:OLLAMA_MODEL = $ModelName
$env:AI_PROVIDER = "ollama"
$env:OLLAMA_BASE_URL = if ($env:OLLAMA_BASE_URL) { $env:OLLAMA_BASE_URL } else { "http://localhost:11434" }
$env:AI_MAX_TOKENS = if ($env:AI_MAX_TOKENS) { $env:AI_MAX_TOKENS } else { "320" }
$env:CHAT_HISTORY_LIMIT = if ($env:CHAT_HISTORY_LIMIT) { $env:CHAT_HISTORY_LIMIT } else { "6" }
$env:RAG_TOP_K = if ($env:RAG_TOP_K) { $env:RAG_TOP_K } else { "3" }
$env:RAG_FAST_MODE = if ($env:RAG_FAST_MODE) { $env:RAG_FAST_MODE } else { "true" }
$env:RAG_PLANNER_ENABLED = if ($env:RAG_PLANNER_ENABLED) { $env:RAG_PLANNER_ENABLED } else { "false" }
$env:RAG_REVIEW_ENABLED = if ($env:RAG_REVIEW_ENABLED) { $env:RAG_REVIEW_ENABLED } else { "false" }
$env:RAG_MAX_QUERIES = if ($env:RAG_MAX_QUERIES) { $env:RAG_MAX_QUERIES } else { "1" }
$env:OLLAMA_KEEP_ALIVE = if ($env:OLLAMA_KEEP_ALIVE) { $env:OLLAMA_KEEP_ALIVE } else { "30m" }
$env:OLLAMA_NUM_PARALLEL = if ($env:OLLAMA_NUM_PARALLEL) { $env:OLLAMA_NUM_PARALLEL } else { "1" }
$env:OLLAMA_MAX_LOADED_MODELS = if ($env:OLLAMA_MAX_LOADED_MODELS) { $env:OLLAMA_MAX_LOADED_MODELS } else { "1" }

try {
    Invoke-RestMethod -Uri "$($env:OLLAMA_BASE_URL)/api/tags" -Method Get -TimeoutSec 3 | Out-Null
} catch {
    Write-Host "Starting Ollama service..."
    Start-Process -FilePath $Ollama -ArgumentList @("serve") -WindowStyle Hidden
    Start-Sleep -Seconds 5
}

if (-not $SkipModelRegister) {
    & (Join-Path $Root "scripts\register-local-model.ps1") -ModelName $ModelName -OllamaBin $Ollama
}

Write-Host "Starting multimodalAgent with local fine-tuned model: $ModelName"
& (Join-Path $Root "scripts\start-voice-dev.ps1")
