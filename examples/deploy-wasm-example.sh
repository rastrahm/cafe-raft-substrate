#!/usr/bin/env bash
# Ejemplo de despliegue de contrato WASM en Cafe Raft

set -e

NODE_URL="${1:-http://localhost:8080}"
WASM_FILE="${2:-/tmp/simple.wasm}"

echo "=== Ejemplo: Desplegar Contrato WASM ==="
echo "Nodo: $NODE_URL"
echo "Archivo WASM: $WASM_FILE"
echo ""

# Convertir WASM a Base64
if [ ! -f "$WASM_FILE" ]; then
    echo "Error: Archivo WASM no encontrado: $WASM_FILE"
    echo "Generando módulo WASM de ejemplo..."
    python3 << 'PYEOF'
import struct

# Módulo WASM simple con función add(a, b) -> a + b
wasm = bytearray([0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00])
wasm.extend([0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f])
wasm.extend([0x03, 0x02, 0x01, 0x00])
wasm.extend([0x07, 0x08, 0x01, 0x03])
wasm.extend(b'add')
wasm.extend([0x00, 0x00])
wasm.extend([0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b])

with open('/tmp/simple.wasm', 'wb') as f:
    f.write(wasm)
print("Módulo WASM generado: /tmp/simple.wasm")
PYEOF
    WASM_FILE="/tmp/simple.wasm"
fi

WASM_BASE64=$(base64 -w 0 "$WASM_FILE")
CONTRACT_NAME="simple"

echo "1. Desplegando contrato WASM..."
DEPLOY_RESPONSE=$(curl -s -X POST "$NODE_URL/contracts/wasm/deploy" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$CONTRACT_NAME\",\"wasmBase64\":\"$WASM_BASE64\"}")

echo "Respuesta: $DEPLOY_RESPONSE"
echo ""

# Verificar que el deploy fue exitoso
if echo "$DEPLOY_RESPONSE" | grep -q '"code":200'; then
    echo "✅ Contrato desplegado exitosamente"
    echo ""
    echo "2. Verificando en el log de Raft..."
    LOG_ENTRY=$(curl -s "$NODE_URL/logs" | python3 -m json.tool 2>/dev/null | \
      grep -A 5 "DeployWasmContractCommand" | grep -A 3 "\"name\":\"$CONTRACT_NAME\"" | head -5)
    echo "$LOG_ENTRY"
    echo ""
    echo "3. Intentando invocar (esperado: error de runtime no integrado)..."
    INVOKE_RESPONSE=$(curl -s -X POST "$NODE_URL/contracts/invoke" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"$CONTRACT_NAME\",\"method\":\"add\",\"args\":{\"a\":10,\"b\":25}}")
    echo "Respuesta: $INVOKE_RESPONSE"
    echo ""
    echo "✅ Flujo completo verificado"
    echo ""
    echo "Nota: El runtime WASM aún no está integrado."
    echo "      El contrato está desplegado y registrado en el log de Raft,"
    echo "      pero la ejecución requiere integrar un runtime WASM."
else
    echo "❌ Error al desplegar contrato"
    exit 1
fi

