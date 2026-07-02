#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_OLLAMA_BIN="$(command -v ollama || true)"
if [ -z "$DEFAULT_OLLAMA_BIN" ] && [ -x "/Applications/Ollama.app/Contents/Resources/ollama" ]; then
  DEFAULT_OLLAMA_BIN="/Applications/Ollama.app/Contents/Resources/ollama"
fi
OLLAMA_BIN="${OLLAMA_BIN:-$DEFAULT_OLLAMA_BIN}"

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Cannot find Ollama."
  echo "Install Ollama or set OLLAMA_BIN to the ollama executable path."
  exit 1
fi

if [ -f "$ROOT_DIR/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf/Modelfile" ]; then
  (cd "$ROOT_DIR/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf" && "$OLLAMA_BIN" create multimodalAgent-qwen2.5-7b-ft:latest -f "Modelfile")
else
  (cd "$ROOT_DIR/models/multimodalAgent-qwen2.5-7b-ft" && "$OLLAMA_BIN" create multimodalAgent-qwen2.5-7b-ft:latest -f "Modelfile")
fi

echo "Created multimodalAgent-qwen2.5-7b-ft:latest"
echo "Run multimodalAgent with: ./scripts/run-dev.sh"
