#!/usr/bin/env bash
# Script para probar state_call con contratos en Substrate

set -e

NODE_URL="${1:-http://localhost:8080}"
SUBSTRATE_URL="${2:-http://127.0.0.1:9944}"

echo "=== Test: state_call para Contratos ==="
echo "Café Raft: $NODE_URL"
echo "Substrate: $SUBSTRATE_URL"
echo ""

# 1. Verificar que ambos nodos estén corriendo
echo "1. Verificando nodos..."
if ! curl -fsS "$NODE_URL/actuator/health" >/dev/null 2>&1; then
    echo "❌ Café Raft no está corriendo en $NODE_URL"
    exit 1
fi
echo "✅ Café Raft está corriendo"

if ! curl -fsS -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"system_health","params":[]}' \
    "$SUBSTRATE_URL" >/dev/null 2>&1; then
    echo "❌ Substrate no está corriendo en $SUBSTRATE_URL"
    exit 1
fi
echo "✅ Substrate está corriendo"
echo ""

# 2. Verificar que ContractsApi_call esté disponible
echo "2. Verificando ContractsApi_call..."
TEST_CALL=$(curl -s -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"state_call","params":["ContractsApi_call","0x"]}' \
    "$SUBSTRATE_URL")

if echo "$TEST_CALL" | grep -q "error"; then
    ERROR_MSG=$(echo "$TEST_CALL" | python3 -c "import sys, json; print(json.load(sys.stdin)['error']['message'])" 2>/dev/null || echo "Error desconocido")
    if echo "$ERROR_MSG" | grep -q "Bad input data"; then
        echo "✅ ContractsApi_call está disponible (el error es esperado por datos vacíos)"
    else
        echo "⚠️  ContractsApi_call puede no estar disponible: $ERROR_MSG"
    fi
else
    echo "✅ ContractsApi_call está disponible"
fi
echo ""

# 3. Desplegar un contrato si no existe
echo "3. Desplegando contrato de prueba..."
CONTRACT_NAME="test-state-call"

# Crear un WASM simple si no existe
WASM_FILE="/tmp/test-contract.wasm"
if [ ! -f "$WASM_FILE" ]; then
    python3 << 'PYEOF'
import struct
# Módulo WASM mínimo válido
wasm = bytearray([0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00])
wasm.extend([0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f])
wasm.extend([0x03, 0x02, 0x01, 0x00])
wasm.extend([0x07, 0x08, 0x01, 0x03])
wasm.extend(b'add')
wasm.extend([0x00, 0x00])
wasm.extend([0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b])
with open('/tmp/test-contract.wasm', 'wb') as f:
    f.write(wasm)
print("✅ WASM generado")
PYEOF
fi

WASM_BASE64=$(base64 -w 0 "$WASM_FILE" 2>/dev/null || base64 "$WASM_FILE")

DEPLOY_RESPONSE=$(curl -s -X POST "$NODE_URL/contracts/wasm/deploy" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$CONTRACT_NAME\",\"wasmBase64\":\"$WASM_BASE64\"}")

echo "Respuesta deploy: $DEPLOY_RESPONSE"
echo ""

# Verificar si el deploy fue exitoso
if echo "$DEPLOY_RESPONSE" | grep -q '"code":200'; then
    echo "✅ Contrato desplegado"
    
    # Esperar un poco para que se propague
    sleep 2
    
    # 4. Verificar en los logs que se use state_call
    echo "4. Verificando logs de Café Raft..."
    if [ -f "logs/node-0.log" ]; then
        if grep -q "state_call\|ContractsApi_call" logs/node-0.log | tail -5; then
            echo "✅ Se encontraron referencias a state_call en los logs"
        else
            echo "⚠️  No se encontraron referencias a state_call (puede que aún no se haya invocado)"
        fi
    fi
    echo ""
    
    echo "✅ Test completado"
    echo ""
    echo "Para invocar el contrato usando state_call, usa:"
    echo "  curl -X POST $NODE_URL/contracts/invoke \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"name\":\"$CONTRACT_NAME\",\"method\":\"call\",\"args\":{\"__inputHex\":\"0x...\",\"__origin\":\"0x<AccountId-hex>\"}}'"
    echo ""
    echo "Nota: __origin debe ser AccountId en formato hex (32 bytes), no SS58/SUri"
else
    echo "⚠️  El despliegue puede haber fallado o el contrato ya existe"
    echo "   Revisa logs/node-0.log para más detalles"
fi

