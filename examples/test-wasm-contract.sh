#!/bin/bash
# Script para probar el despliegue e invocación de un contrato WASM

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Test de Contrato WASM ===${NC}"

# Verificar que los nodos estén funcionando
echo -e "\n${BLUE}Verificando nodos...${NC}"
for port in 8080 8081 8082; do
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Nodo en puerto $port está funcionando${NC}"
    else
        echo -e "${RED}✗ Nodo en puerto $port no está funcionando${NC}"
        exit 1
    fi
done

# Convertir el módulo WASM a Base64
WASM_FILE="$PROJECT_DIR/examples/add.wasm"
if [ ! -f "$WASM_FILE" ]; then
    echo -e "${RED}Error: No se encontró el archivo $WASM_FILE${NC}"
    exit 1
fi

WASM_BASE64=$(base64 -w 0 "$WASM_FILE")

# Desplegar el contrato WASM
echo -e "\n${BLUE}Desplegando contrato WASM 'add'...${NC}"
DEPLOY_RESPONSE=$(curl -fsS -X POST "http://localhost:8080/contracts/wasm/deploy" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"add\",\"wasmBase64\":\"$WASM_BASE64\"}")

echo "$DEPLOY_RESPONSE" | jq '.' 2>/dev/null || echo "$DEPLOY_RESPONSE"

# Verificar que el despliegue fue exitoso
if echo "$DEPLOY_RESPONSE" | grep -q '"code":200'; then
    echo -e "${GREEN}✓ Contrato WASM desplegado correctamente${NC}"
else
    echo -e "${RED}✗ Error al desplegar el contrato WASM${NC}"
    exit 1
fi

# Esperar un poco para que el contrato se propague
sleep 2

# Invocar el contrato WASM
echo -e "\n${BLUE}Invocando función 'add' con a=10, b=25...${NC}"
INVOKE_RESPONSE=$(curl -fsS -X POST "http://localhost:8080/contracts/invoke" \
    -H "Content-Type: application/json" \
    -d '{"name":"add","method":"add","args":{"a":10,"b":25}}')

echo "$INVOKE_RESPONSE" | jq '.' 2>/dev/null || echo "$INVOKE_RESPONSE"

# Verificar que la invocación fue exitosa
if echo "$INVOKE_RESPONSE" | grep -q '"code":200'; then
    RESULT=$(echo "$INVOKE_RESPONSE" | jq -r '.result // .data // empty' 2>/dev/null)
    if [ -n "$RESULT" ]; then
        echo -e "${GREEN}✓ Función invocada correctamente. Resultado: $RESULT${NC}"
        if [ "$RESULT" = "35" ]; then
            echo -e "${GREEN}✓ Resultado correcto: 10 + 25 = 35${NC}"
        else
            echo -e "${RED}✗ Resultado incorrecto: se esperaba 35, se obtuvo $RESULT${NC}"
        fi
    else
        echo -e "${GREEN}✓ Función invocada correctamente${NC}"
    fi
else
    echo -e "${RED}✗ Error al invocar el contrato WASM${NC}"
    exit 1
fi

echo -e "\n${GREEN}=== Test completado exitosamente ===${NC}"

