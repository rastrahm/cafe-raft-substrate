#!/usr/bin/env bash
# Script para sincronizar el estado de un contrato desde Substrate a Café Raft

set -e

CAFE_RAFT_URL="${1:-http://localhost:8080}"
CONTRACT_NAME="${2:-Counter}"
METHOD="${3:-get}"  # Método a invocar para obtener estado
INPUT_HEX="${4:-0x}"  # Input SCALE-encoded (por defecto vacío)

echo "=== Sincronizando estado de contrato ==="
echo "Contrato: $CONTRACT_NAME"
echo "Método: $METHOD"
echo "Input: $INPUT_HEX"
echo ""

# Sincronizar estado
echo "Sincronizando estado desde Substrate..."
RESPONSE=$(curl -s -X POST "$CAFE_RAFT_URL/contracts/wasm/sync-state" \
  -H 'Content-Type: application/json' \
  -d "{
    \"contractName\": \"$CONTRACT_NAME\",
    \"method\": \"$METHOD\",
    \"inputHex\": \"$INPUT_HEX\"
  }")

echo "Respuesta: $RESPONSE"
echo ""

# Consultar estado sincronizado
echo "Consultando estado sincronizado..."
STATE=$(curl -s -X GET "$CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME")

echo "Estado sincronizado:"
echo "$STATE" | jq '.' 2>/dev/null || echo "$STATE"
echo ""

echo "✅ Sincronización completada"

