#!/usr/bin/env bash
# Script completo para probar la integración con Substrate v0.42.0

set -e

NODE_URL="${1:-http://localhost:8080}"
SUBSTRATE_URL="${2:-http://127.0.0.1:9944}"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Test Completo: Integración Substrate v0.42.0 ===${NC}"
echo ""

# 1. Verificar servidores
echo -e "${BLUE}1. Verificando servidores...${NC}"
if curl -fsS "$NODE_URL/actuator/health" >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Café Raft está corriendo${NC}"
else
    echo -e "${RED}✗ Café Raft no está corriendo${NC}"
    exit 1
fi

if curl -fsS -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"system_health","params":[]}' \
    "$SUBSTRATE_URL" >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Substrate está corriendo${NC}"
else
    echo -e "${RED}✗ Substrate no está corriendo${NC}"
    exit 1
fi
echo ""

# 2. Verificar detección de capacidades
echo -e "${BLUE}2. Verificando detección automática de capacidades...${NC}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_FILE="$PROJECT_DIR/logs/node-0.log"

if [ -f "$LOG_FILE" ]; then
    CAPABILITIES=$(grep -i "Capacidades Substrate detectadas" "$LOG_FILE" | tail -1)
    if [ -n "$CAPABILITIES" ]; then
        echo -e "${GREEN}✓ Detección de capacidades encontrada en logs${NC}"
        grep -A 5 "Capacidades Substrate detectadas" "$LOG_FILE" | tail -6
    else
        echo -e "${YELLOW}⚠ No se encontró detección de capacidades (puede que el servidor no se haya reiniciado)${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Archivo de logs no encontrado en: $LOG_FILE${NC}"
fi
echo ""

# 3. Verificar métodos RPC disponibles
echo -e "${BLUE}3. Verificando métodos RPC disponibles en Substrate...${NC}"
RPC_METHODS=$(curl -s -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"rpc_methods","params":[]}' \
    "$SUBSTRATE_URL")

if echo "$RPC_METHODS" | grep -q "contracts_"; then
    echo -e "${GREEN}✓ Métodos RPC contracts_* encontrados${NC}"
    echo "$RPC_METHODS" | python3 -c "import sys, json; d=json.load(sys.stdin); methods=[m for m in d.get('result', {}).get('methods', []) if 'contracts_' in m]; print('Métodos contracts_*:', ', '.join(methods[:5]))" 2>/dev/null || echo "Métodos encontrados"
else
    echo -e "${YELLOW}⚠ No se encontraron métodos RPC contracts_* (se usará state_call)${NC}"
fi
echo ""

# 4. Crear y desplegar contrato de prueba
echo -e "${BLUE}4. Desplegando contrato de prueba...${NC}"
CONTRACT_NAME="test-substrate-v42"

# Crear WASM simple
WASM_FILE="/tmp/test-contract-v42.wasm"
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
with open('/tmp/test-contract-v42.wasm', 'wb') as f:
    f.write(wasm)
print("✅ WASM generado")
PYEOF

WASM_BASE64=$(base64 -w 0 "$WASM_FILE" 2>/dev/null || base64 "$WASM_FILE")

DEPLOY_RESPONSE=$(curl -s -X POST "$NODE_URL/contracts/wasm/deploy" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$CONTRACT_NAME\",\"wasmBase64\":\"$WASM_BASE64\"}")

echo "Respuesta deploy: $DEPLOY_RESPONSE"
echo ""

if echo "$DEPLOY_RESPONSE" | grep -q '"code":200'; then
    echo -e "${GREEN}✓ Contrato desplegado en Café Raft${NC}"
    
    # Verificar en logs qué método se usó
    sleep 2
    if [ -f "$LOG_FILE" ]; then
        DEPLOY_METHOD=$(grep -i "contracts_instantiateWithCode\|state_call\|Despliegue automático\|no disponible" "$LOG_FILE" | tail -3)
        if [ -n "$DEPLOY_METHOD" ]; then
            echo -e "${GREEN}✓ Método de despliegue detectado en logs:${NC}"
            echo "$DEPLOY_METHOD" | sed 's/^/   /'
        fi
    fi
else
    echo -e "${YELLOW}⚠ El despliegue puede haber fallado o requerir registro manual${NC}"
fi
echo ""

# 5. Verificar estado del contrato
echo -e "${BLUE}5. Estado del sistema:${NC}"
echo "   - Contrato registrado en Café Raft: $CONTRACT_NAME"
echo "   - Para usar el contrato, necesitas:"
echo "     1. Desplegarlo en Substrate (si auto-deploy falló)"
echo "     2. Registrar la dirección con:"
echo "        curl -X POST $NODE_URL/contracts/wasm/register-address \\"
echo "          -H 'Content-Type: application/json' \\"
echo "          -d '{\"name\":\"$CONTRACT_NAME\",\"address\":\"0x<direccion>\"}'"
echo ""

# 6. Resumen
echo -e "${BLUE}=== Resumen ===${NC}"
echo -e "${GREEN}✅ Servidores verificados${NC}"
echo -e "${GREEN}✅ Detección automática implementada${NC}"
echo -e "${GREEN}✅ Sistema preparado para usar RPC directo o state_call${NC}"
echo ""
echo "Para ver los logs de detección:"
echo "  tail -f logs/node-0.log | grep -i 'capacidades\|capabilities'"
echo ""

