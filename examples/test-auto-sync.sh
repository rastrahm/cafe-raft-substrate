#!/usr/bin/env bash
# Script para verificar la sincronización automática de estado

set -e

CAFE_RAFT_URL="${1:-http://localhost:8080}"
CONTRACT_NAME="${2:-Counter}"

echo "=== Verificación de Sincronización Automática ==="
echo "Café Raft: $CAFE_RAFT_URL"
echo "Contrato: $CONTRACT_NAME"
echo ""

# 1. Verificar que el servidor esté corriendo
echo "1. Verificando servidor Café Raft..."
if ! curl -s -f "$CAFE_RAFT_URL/actuator/health" > /dev/null 2>&1; then
    echo "❌ Error: Servidor no disponible en $CAFE_RAFT_URL"
    exit 1
fi
echo "✅ Servidor disponible"
echo ""

# 2. Verificar que el contrato esté registrado
echo "2. Verificando que el contrato esté registrado..."
# Intentar consultar el estado (puede no existir aún)
STATE_BEFORE=$(curl -s "$CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME" 2>/dev/null || echo "")
if echo "$STATE_BEFORE" | grep -q "404\|not found\|no sincronizado"; then
    echo "⚠️  Estado aún no sincronizado (esto es normal si es la primera vez)"
else
    echo "✅ Estado encontrado:"
    echo "$STATE_BEFORE" | jq '.' 2>/dev/null || echo "$STATE_BEFORE"
fi
echo ""

# 3. Invocar el contrato (esto debería activar la sincronización automática)
echo "3. Invocando contrato para activar sincronización automática..."
echo "   (Nota: Necesitas el inputHex SCALE-encoded del método)"
echo ""
echo "   Ejemplo para Counter::increment:"
echo "   curl -X POST $CAFE_RAFT_URL/contracts/invoke \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{"
echo "       \"name\": \"$CONTRACT_NAME\","
echo "       \"method\": \"increment\","
echo "       \"args\": {"
echo "         \"__origin\": \"0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d\","
echo "         \"__inputHex\": \"0x...\","
echo "         \"__gasLimit\": \"1000000000\""
echo "       }"
echo "     }'"
echo ""

# 4. Verificar logs para confirmar sincronización
echo "4. Revisando logs para confirmar sincronización automática..."
echo "   Buscando mensajes de sincronización en logs/node-0.log..."
if [ -f "logs/node-0.log" ]; then
    echo ""
    echo "   Últimas líneas relacionadas con sincronización:"
    tail -50 logs/node-0.log | grep -i "sincroniz\|sync\|estado" | tail -10 || echo "   (No se encontraron mensajes aún)"
else
    echo "   ⚠️  Archivo de log no encontrado"
fi
echo ""

# 5. Consultar estado después de la invocación
echo "5. Para verificar el estado sincronizado después de invocar:"
echo "   curl -X GET $CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME"
echo ""

echo "=== Instrucciones ==="
echo ""
echo "Para probar completamente:"
echo "1. Asegúrate de que el contrato $CONTRACT_NAME esté desplegado y registrado"
echo "2. Invoca el contrato usando el endpoint /contracts/invoke"
echo "3. Verifica los logs para ver la sincronización automática"
echo "4. Consulta el estado con GET /contracts/wasm/state?name=$CONTRACT_NAME"
echo ""

