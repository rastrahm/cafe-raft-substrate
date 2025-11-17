#!/usr/bin/env bash
# Script completo de prueba: invoca contrato y verifica sincronización automática

set -e

CAFE_RAFT_URL="${1:-http://localhost:8080}"
CONTRACT_NAME="${2:-Counter}"

echo "=========================================="
echo "  Prueba Completa de Sincronización Automática"
echo "=========================================="
echo ""

# Verificar servidor
if ! curl -s -f "$CAFE_RAFT_URL/actuator/health" > /dev/null 2>&1; then
    echo "❌ Error: Servidor no disponible en $CAFE_RAFT_URL"
    echo ""
    echo "Inicia el servidor primero:"
    echo "  ./gradlew :raft-application-spring:bootRun --args='--cluster.properties.nodeId=0 --server.port=8080'"
    exit 1
fi

echo "✅ Servidor disponible"
echo ""

# Estado antes
echo "📊 Estado ANTES de invocar:"
STATE_BEFORE=$(curl -s "$CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME" 2>/dev/null)
if echo "$STATE_BEFORE" | grep -q "contractName"; then
    echo "$STATE_BEFORE" | jq '.' 2>/dev/null || echo "$STATE_BEFORE"
    TIMESTAMP_BEFORE=$(echo "$STATE_BEFORE" | jq -r '.lastSyncTimestamp' 2>/dev/null || echo "0")
else
    echo "  (Estado no sincronizado aún)"
    TIMESTAMP_BEFORE="0"
fi
echo ""

# Nota sobre inputHex
echo "⚠️  NOTA: Para invocar el contrato necesitas el inputHex SCALE-encoded."
echo "   Este script muestra el comando, pero necesitas proporcionar el inputHex correcto."
echo ""
echo "   Ejemplo de invocación (reemplaza 0x... con el inputHex real):"
echo ""
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

# Esperar confirmación del usuario
read -p "¿Has invocado el contrato? (s/n): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Ss]$ ]]; then
    echo "Ejecuta la invocación y luego vuelve a ejecutar este script para verificar."
    exit 0
fi

# Esperar un momento para que se complete la sincronización
echo ""
echo "⏳ Esperando 2 segundos para que se complete la sincronización..."
sleep 2
echo ""

# Estado después
echo "📊 Estado DESPUÉS de invocar:"
STATE_AFTER=$(curl -s "$CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME" 2>/dev/null)
if echo "$STATE_AFTER" | grep -q "contractName"; then
    echo "$STATE_AFTER" | jq '.' 2>/dev/null || echo "$STATE_AFTER"
    TIMESTAMP_AFTER=$(echo "$STATE_AFTER" | jq -r '.lastSyncTimestamp' 2>/dev/null || echo "0")
    
    if [ "$TIMESTAMP_AFTER" != "$TIMESTAMP_BEFORE" ] && [ "$TIMESTAMP_AFTER" != "0" ]; then
        echo ""
        echo "✅ ¡Sincronización automática funcionó!"
        echo "   Timestamp cambió de $TIMESTAMP_BEFORE a $TIMESTAMP_AFTER"
    else
        echo ""
        echo "⚠️  El timestamp no cambió. Verifica los logs para ver si hubo errores."
    fi
else
    echo "  (Estado no sincronizado)"
    echo ""
    echo "⚠️  El estado no se sincronizó. Verifica:"
    echo "   1. Que auto-sync-after-invoke esté habilitado en application.yaml"
    echo "   2. Los logs para ver errores: tail -50 logs/node-0.log | grep -i sync"
fi
echo ""

# Verificar logs
echo "📋 Últimos mensajes de sincronización en logs:"
if [ -f "logs/node-0.log" ]; then
    tail -50 logs/node-0.log | grep -E "(Sincronizando|sync|estado|auto)" -i | tail -5 || echo "  (No se encontraron mensajes)"
else
    echo "  (Log no encontrado)"
fi
echo ""

echo "=========================================="

