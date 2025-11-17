#!/usr/bin/env bash
# Script para obtener la dirección esperada de un contrato usando state_call (dry-run)

set -e

SUBSTRATE_URL="${1:-http://127.0.0.1:9944}"
CONTRACT_NAME="${2:-Counter}"
WASM_FILE="${3:-examples/Counter.wasm}"

if [ ! -f "$WASM_FILE" ]; then
    echo "Error: Archivo WASM no encontrado: $WASM_FILE"
    exit 1
fi

echo "=== Obteniendo dirección esperada para $CONTRACT_NAME ==="
echo ""

# 1. Calcular codeHash del WASM
echo "1. Calculando codeHash del contrato..."
WASM_HEX=$(xxd -p -c 1000000 "$WASM_FILE" | tr -d '\n')
# Calcular hash SHA256 (simplificado, en producción usaría el hash real del runtime)
# Por ahora, usamos un hash simulado
CODE_HASH=$(echo -n "$WASM_HEX" | sha256sum | cut -d' ' -f1)
echo "   CodeHash (simulado): 0x$CODE_HASH"
echo ""

# 2. Obtener AccountId del origin (usar Alice por defecto)
# Alice en Substrate: 5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY
# Convertir a AccountId hex (32 bytes)
ORIGIN_ACCOUNT_ID="0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
echo "2. Usando origin: $ORIGIN_ACCOUNT_ID (Alice)"
echo ""

# 3. Preparar parámetros para ContractsApi_instantiate
echo "3. Preparando parámetros para state_call..."
echo "   (Nota: Esto es un dry-run, no despliega realmente)"
echo ""

# Parámetros SCALE-encoded simplificados
# Estructura: (origin: AccountId, value: u128, gas_limit: u128, storage_deposit_limit: Option<u128>, 
#              code_hash: CodeHash, data: Vec<u8>, salt: Vec<u8>)

# Por ahora, mostramos cómo hacerlo manualmente
echo "Para obtener la dirección real, necesitas:"
echo ""
echo "1. Subir el código del contrato usando cargo-contract o Polkadot-JS"
echo "2. Instanciar el contrato y obtener la dirección"
echo "3. Registrar la dirección en Café Raft:"
echo ""
echo "   curl -X POST http://localhost:8080/contracts/wasm/register-address \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"name\":\"$CONTRACT_NAME\",\"address\":\"0x<direccion>\"}'"
echo ""
echo "O usar state_call con ContractsApi_instantiate para un dry-run:"
echo "   (requiere implementación completa de SCALE encoding)"
echo ""



