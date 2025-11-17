# Cómo obtener la dirección del contrato Counter

## Problema

El despliegue automático no funciona porque `contracts_instantiateWithCode` no está disponible en `substrate-contracts-node` v0.42.0. El contrato se registró en Café Raft, pero no se desplegó en Substrate, por lo que no hay dirección.

## Soluciones

### Opción 1: Desplegar manualmente con cargo-contract (Recomendado)

1. **Instalar cargo-contract** (si no lo tienes):
   ```bash
   cargo install cargo-contract --force
   ```

2. **Desplegar el contrato**:
   ```bash
   cd /ruta/al/contrato
   cargo contract instantiate \
     --constructor new \
     --args "" \
     --suri //Alice \
     --url ws://127.0.0.1:9944
   ```

3. **Copiar la dirección** que se muestra en la salida (formato `0x...`)

4. **Registrar en Café Raft**:
   ```bash
   curl -X POST http://localhost:8080/contracts/wasm/register-address \
     -H 'Content-Type: application/json' \
     -d '{"name":"Counter","address":"0x<direccion_copiada>"}'
   ```

### Opción 2: Usar Polkadot-JS Apps

1. Abre https://polkadot.js.org/apps
2. Conecta a `ws://127.0.0.1:9944`
3. Ve a **Developer > Contracts**
4. Sube el código del contrato (`Counter.wasm`)
5. Instancia el contrato
6. Copia la dirección generada
7. Regístrala en Café Raft (como en Opción 1, paso 4)

### Opción 3: Usar state_call para dry-run (Solo para obtener dirección esperada)

**Nota**: `state_call` con `ContractsApi_instantiate` es solo para dry-run y no despliega realmente. Pero puedes usarlo para obtener la dirección que se generaría.

```bash
# Requiere implementación completa de SCALE encoding
# Por ahora, usa las opciones 1 o 2
```

## Verificar que el contrato está registrado

```bash
# Verificar en los logs
tail -f logs/node-0.log | grep -i "Counter"

# Deberías ver:
# WASM almacenado en modo Substrate (sin precompilación): Counter (37935 bytes)
```

## Después de registrar la dirección

Una vez registrada la dirección, puedes invocar el contrato usando:

```bash
curl -X POST http://localhost:8080/contracts/invoke \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Counter",
    "method": "increment",
    "args": {
      "__origin": "0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d",
      "__inputHex": "0x...",
      "__gasLimit": "1000000000"
    }
  }'
```

## Nota sobre el despliegue automático

El sistema intentará desplegar automáticamente cuando:
- `substrate.enabled=true`
- `substrate.auto-deploy=true`
- El nodo Substrate expone `contracts_instantiateWithCode` RPC

Como `substrate-contracts-node` v0.42.0 no expone este método, el despliegue automático no funciona. Esto es normal y esperado.



