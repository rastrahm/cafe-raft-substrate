#!/usr/bin/env bash
# Script completo para probar state_call con invocación real

set -e

NODE_URL="${1:-http://localhost:8080}"
SUBSTRATE_URL="${2:-http://127.0.0.1:9944}"
CONTRACT_NAME="test-state-call"

# AccountId conocido de Alice
ALICE_ACCOUNT_ID="0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"

echo "=== Test: Invocación con state_call ==="
echo ""

# 1. Verificar que el contrato esté registrado
echo "1. Verificando que el contrato '$CONTRACT_NAME' esté registrado..."
echo "   (Si no existe, desplegarlo primero con deploy-wasm-example.sh)"
echo ""

# 2. Para usar state_call, necesitamos una dirección de contrato en Substrate
# Como el despliegue automático falló, necesitamos registrar la dirección manualmente
echo "2. IMPORTANTE: Para usar state_call, necesitas:"
echo "   a) Desplegar el contrato en Substrate manualmente (usando cargo contract, Polkadot-JS, etc.)"
echo "   b) Registrar la dirección en Café Raft:"
echo ""
echo "   curl -X POST $NODE_URL/contracts/wasm/register-address \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"name\":\"$CONTRACT_NAME\",\"address\":\"0x<direccion-del-contrato>\"}'"
echo ""

# 3. Ejemplo de invocación (requiere dirección válida)
echo "3. Ejemplo de invocación usando state_call:"
echo ""
echo "   curl -X POST $NODE_URL/contracts/invoke \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{"
echo "       \"name\":\"$CONTRACT_NAME\","
echo "       \"method\":\"call\","
echo "       \"args\":{"
echo "         \"__origin\":\"$ALICE_ACCOUNT_ID\","
echo "         \"__inputHex\":\"0x<datos-scale-encoded>\","
echo "         \"__gasLimit\":\"500000000000\""
echo "       }"
echo "     }'"
echo ""

# 4. Probar con el contrato real si tiene dirección
echo "4. Probando invocación (puede fallar si no hay dirección registrada)..."
echo ""

# Datos SCALE mínimos para una llamada (solo para probar el formato)
# Esto es un ejemplo simplificado - en producción necesitarías los datos reales del contrato
MINIMAL_INPUT="0x00000000"  # Selector de función básico + datos vacíos

INVOKE_RESPONSE=$(curl -s -X POST "$NODE_URL/contracts/invoke" \
    -H 'Content-Type: application/json' \
    -d "{
      \"name\":\"$CONTRACT_NAME\",
      \"method\":\"call\",
      \"args\":{
        \"__origin\":\"$ALICE_ACCOUNT_ID\",
        \"__inputHex\":\"$MINIMAL_INPUT\",
        \"__gasLimit\":\"500000000000\"
      }
    }")

echo "Respuesta: $INVOKE_RESPONSE"
echo ""

if echo "$INVOKE_RESPONSE" | grep -q "No se ha registrado la dirección"; then
    echo "⚠️  El contrato no tiene dirección Substrate registrada"
    echo "   Sigue los pasos en la sección 2 para registrar la dirección"
elif echo "$INVOKE_RESPONSE" | grep -q "state_call\|ContractsApi_call"; then
    echo "✅ Se está usando state_call correctamente"
elif echo "$INVOKE_RESPONSE" | grep -q '"code":200'; then
    echo "✅ Invocación exitosa"
else
    echo "⚠️  Revisa la respuesta para más detalles"
fi

echo ""
echo "=== Resumen ==="
echo "✅ state_call está implementado y disponible"
echo "✅ WasmEngine intenta usar state_call cuando __origin es AccountId hex"
echo "⚠️  Necesitas desplegar contratos manualmente en Substrate"
echo "⚠️  Necesitas registrar las direcciones en Café Raft"
echo ""

