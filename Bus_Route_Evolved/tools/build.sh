#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# build.sh - regenera e compila todas as linhas do Bus Route Evolved.
#
#   ./tools/build.sh                 # usa o gta3sc do PATH
#   GTA3SC=/caminho/gta3sc ./tools/build.sh
#
# Para cada data/routes/<id>_route.json:
#   1. gera source/BUS<id>.sc   (gta3script, legivel)
#   2. compila  CLEO/BUS<id>.cs (pronto para instalar no jogo)
#
# Nao edite os arquivos .sc/.cs a mao: eles sao gerados a partir dos JSON.
# ---------------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
GTA3SC="${GTA3SC:-gta3sc}"

if ! command -v "$GTA3SC" >/dev/null 2>&1 && [ ! -x "$GTA3SC" ]; then
    echo "erro: gta3sc nao encontrado (defina GTA3SC=/caminho/do/gta3sc)" >&2
    exit 1
fi

mkdir -p source CLEO
shopt -s nullglob
routes=(data/routes/*.json)
if [ ${#routes[@]} -eq 0 ]; then
    echo "nenhuma rota encontrada em data/routes/" >&2
    exit 1
fi

for json in "${routes[@]}"; do
    id="$(python3 -c "import json,sys;print(json.load(open('$json'))['id'])" 2>/dev/null || echo 000)"
    sc="source/BUS${id}.sc"
    cs="source/BUS${id}.cs"
    echo "== rota ${id}: ${json}"
    python3 tools/generate_script.py "$json" -o "$sc"
    "$GTA3SC" compile "$sc" --config=gtasa --guesser --cs -o "$cs"
    cp -f "$cs" "CLEO/"
done

echo
echo "pronto. scripts em CLEO/ (instale em GTA San Andreas/CLEO/)."
