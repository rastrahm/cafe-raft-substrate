#!/usr/bin/env bash
# Script completo para verificar la sincronización automática

set -e

CAFE_RAFT_URL="${1:-http://localhost:8080}"
CONTRACT_NAME="${2:-Counter}"

echo "=========================================="
echo "  Verificación de Sincronización Automática"
echo "=========================================="
echo "Café Raft: $CAFE_RAFT_URL"
echo "Contrato: $CONTRACT_NAME"
echo ""

# 1. Verificar servidor
echo "[1/5] Verificando servidor..."
if curl -s -f "$CAFE_RAFT_URL/actuator/health" > /dev/null 2>&1; then
    echo "✅ Servidor disponible"
    HEALTH=$(curl -s "$CAFE_RAFT_URL/actuator/health")
    echo "   Estado: $HEALTH"
else
    echo "❌ Servidor no disponible en $CAFE_RAFT_URL"
    echo ""
    echo "   Para iniciar el servidor:"
    echo "   ./gradlew :raft-application-spring:bootRun --args='--cluster.properties.nodeId=0 --server.port=8080'"
    exit 1
fi
echo ""

# 2. Verificar configuración
echo "[2/5] Verificando configuración..."
CONFIG=$(curl -s "$CAFE_RAFT_URL/actuator/info" 2>/dev/null || echo "")
if echo "$CONFIG" | grep -q "substrate"; then
    echo "✅ Configuración Substrate detectada"
else
    echo "⚠️  No se pudo verificar configuración Substrate"
fi
echo ""

# 3. Verificar estado antes de invocación
echo "[3/5] Estado actual del contrato (antes de invocar)..."
STATE_BEFORE=$(curl -s "$CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME" 2>/dev/null)
if echo "$STATE_BEFORE" | grep -q "contractName"; then
    echo "✅ Estado encontrado:"
    echo "$STATE_BEFORE" | jq '.' 2>/dev/null || echo "$STATE_BEFORE"
    TIMESTAMP_BEFORE=$(echo "$STATE_BEFORE" | jq -r '.lastSyncTimestamp' 2>/dev/null || echo "0")
else
    echo "⚠️  Estado no sincronizado aún (normal si es la primera vez)"
    TIMESTAMP_BEFORE="0"
fi
echo ""

# 4. Instrucciones para invocar
echo "[4/5] Para probar la sincronización automática:"
echo ""
echo "   Invoca el contrato con:"
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
echo "   Después de invocar, el estado se sincronizará automáticamente."
echo ""

# 5. Verificar logs
echo "[5/5] Revisando logs para mensajes de sincronización..."
if [ -f "logs/node-0.log" ]; then
    echo ""
    echo "   Últimos mensajes relacionados:"
    tail -100 logs/node-0.log | grep -E "(Sincronizando|sync|estado|auto)" -i | tail -10 || echo "   (No se encontraron mensajes aún)"
    echo ""
    echo "   Para ver logs en tiempo real:"
    echo "   tail -f logs/node-0.log | grep -i sync"
else
    echo "   ⚠️  Archivo de log no encontrado: logs/node-0.log"
fi
echo ""

# 6. Script de verificación post-invocación
echo "=========================================="
echo "  Para verificar después de invocar:"
echo "=========================================="
echo ""
echo "   # Consultar estado sincronizado"
echo "   curl -X GET $CAFE_RAFT_URL/contracts/wasm/state?name=$CONTRACT_NAME | jq '.'"
echo ""
echo "   # Ver logs de sincronización"
echo "   tail -20 logs/node-0.log | grep -i 'sincroniz'"
echo ""
echo "=========================================="

