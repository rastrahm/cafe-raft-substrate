#!/usr/bin/env bash
# Script para desplegar Counter.wasm en Café Raft

set -e

NODE_URL="${1:-http://localhost:8080}"
WASM_FILE="${2:-examples/Counter.wasm}"

if [ ! -f "$WASM_FILE" ]; then
    echo "Error: Archivo WASM no encontrado: $WASM_FILE"
    exit 1
fi

echo "=== Desplegando Counter.wasm ==="
echo "Archivo: $WASM_FILE"
echo "Nodo: $NODE_URL"
echo ""

# Convertir WASM a Base64
WASM_BASE64=$(base64 -w 0 "$WASM_FILE" 2>/dev/null || base64 "$WASM_FILE")
CONTRACT_NAME="Counter"

echo "Desplegando contrato '$CONTRACT_NAME'..."
DEPLOY_RESPONSE=$(curl -s -X POST "$NODE_URL/contracts/wasm/deploy" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$CONTRACT_NAME\",\"wasmBase64\":\"$WASM_BASE64\"}")

echo "Respuesta: $DEPLOY_RESPONSE"
echo ""

if echo "$DEPLOY_RESPONSE" | grep -q '"code":200'; then
    echo "✅ Contrato '$CONTRACT_NAME' desplegado exitosamente en Café Raft"
    echo ""
    echo "Nota: Si substrate.enabled=true y auto-deploy=true, el sistema intentará"
    echo "      desplegarlo automáticamente en Substrate. Revisa los logs para ver el resultado."
    echo ""
    echo "Para usar el contrato:"
    echo "  1. Si el despliegue automático falló, despliega manualmente en Substrate"
    echo "  2. Registra la dirección con:"
    echo "     curl -X POST $NODE_URL/contracts/wasm/register-address \\"
    echo "       -H 'Content-Type: application/json' \\"
    echo "       -d '{\"name\":\"$CONTRACT_NAME\",\"address\":\"0x<direccion>\"}'"
    echo "  3. Invoca usando state_call con AccountId hex"
else
    echo "❌ Error al desplegar el contrato"
    exit 1
fi



