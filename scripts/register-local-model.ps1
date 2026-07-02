param(
    [string]$ModelName = $env:OLLAMA_MODEL,
    [string]$OllamaBin = $env:OLLAMA_BIN,
    [switch]$Force
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

function Resolve-ModelDir {
    $candidates = @(
        (Join-Path $Root "mindbridge-qwen2.5-7b-ft-q4_k_m.gguf"),
        (Join-Path $Root "models\multimodalAgent-qwen2.5-7b-ft")
    )
    foreach ($candidate in $candidates) {
        $modelfile = Join-Path $candidate "Modelfile"
        $gguf = Get-ChildItem -LiteralPath $candidate -Filter *.gguf -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if ((Test-Path -LiteralPath $modelfile) -and $gguf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "Cannot find a GGUF model directory with Modelfile. Expected mindbridge-qwen2.5-7b-ft-q4_k_m.gguf or models\multimodalAgent-qwen2.5-7b-ft."
}

$Ollama = Resolve-Ollama $OllamaBin
$ModelDir = Resolve-ModelDir

$existing = & $Ollama list 2>$null | Select-String -SimpleMatch $ModelName
if ($existing -and -not $Force) {
    Write-Host "Ollama model already exists: $ModelName"
    exit 0
}

Write-Host "Registering local model: $ModelName"
Write-Host "Model directory: $ModelDir"
Push-Location $ModelDir
try {
    & $Ollama create $ModelName -f "Modelfile"
} finally {
    Pop-Location
}
Write-Host "Model is ready: $ModelName"
