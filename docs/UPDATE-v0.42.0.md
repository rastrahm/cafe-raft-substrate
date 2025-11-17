# Actualización a substrate-contracts-node v0.42.0

## Resumen

Se ha actualizado `substrate-contracts-node` de v0.39.0 a v0.42.0 y se ha implementado **detección automática de capacidades** para usar métodos RPC directos cuando estén disponibles.

## Cambios Realizados

### 1. Actualización del Nodo

- ✅ `substrate-contracts-node` actualizado a **v0.42.0**
- ✅ Compilado exitosamente con Rust 1.85
- ✅ Binario disponible en `substrate-contracts-node/target/release/substrate-contracts-node`

### 2. Detección Automática de Capacidades

Se ha creado `SubstrateCapabilities` que detecta automáticamente al inicio:

- ✅ Métodos RPC `contracts_*` disponibles
- ✅ `contracts_instantiateWithCode` disponible
- ✅ `contracts_call` disponible
- ✅ `contracts_uploadCode` disponible

**Logs al inicio:**
```
Detectando capacidades del nodo Substrate...
Capacidades Substrate detectadas:
  - Métodos RPC contracts_*: true/false
  - contracts_instantiateWithCode: true/false
  - contracts_call: true/false
  - contracts_uploadCode: true/false
```

### 3. Estrategia Inteligente de Invocación

`WasmEngine.invokeViaSubstrate()` ahora:

1. **Si hay `contracts_call` RPC y origin es SS58/SUri:**
   - ✅ Usa `contracts_call` RPC directo (más simple)
   - ✅ Acepta SS58/SUri sin conversión

2. **Si origin es AccountId hex o no hay RPC directo:**
   - ✅ Usa `state_call` con `ContractsApi_call`
   - ✅ Requiere AccountId hex (32 bytes)

### 4. Estrategia Inteligente de Despliegue

`WasmEngine.deployViaSubstrate()` ahora:

1. **Si hay `contracts_instantiateWithCode` RPC:**
   - ✅ Usa RPC directo para despliegue automático
   - ✅ Registra la dirección automáticamente

2. **Si no hay RPC directo:**
   - ⚠️ Muestra mensaje indicando despliegue manual necesario
   - ✅ Guía al usuario para usar `POST /contracts/wasm/register-address`

## Estado Actual (v0.42.0)

**Nota:** `substrate-contracts-node` v0.42.0 **NO incluye** métodos RPC `contracts_*` por diseño (es un nodo simple para desarrollo).

Sin embargo, el sistema está preparado para:
- ✅ **Detectar automáticamente** si en el futuro se agregan
- ✅ **Usar métodos RPC directos** cuando estén disponibles
- ✅ **Fallback a `state_call`** cuando no lo estén

## Ventajas de la Implementación

### Compatibilidad Hacia Atrás
- ✅ Funciona con nodos sin métodos RPC (usa `state_call`)
- ✅ Funciona con nodos con métodos RPC (usa RPC directo)

### Experiencia de Usuario Mejorada
- ✅ **Con RPC directo:** Acepta SS58/SUri, más simple
- ✅ **Sin RPC directo:** Usa `state_call`, requiere AccountId hex

### Preparado para el Futuro
- ✅ Si se actualiza a un nodo con métodos RPC, funcionará automáticamente
- ✅ No requiere cambios en el código

## Uso

### Con Nodo sin Métodos RPC (Estado Actual)

```bash
# Despliegue manual
cargo contract instantiate --suri //Alice --url ws://127.0.0.1:9944

# Registrar dirección
curl -X POST http://localhost:8080/contracts/wasm/register-address \
  -H 'Content-Type: application/json' \
  -d '{"name":"mi-contrato","address":"0x..."}'

# Invocar (requiere AccountId hex)
curl -X POST http://localhost:8080/contracts/invoke \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"mi-contrato",
    "method":"call",
    "args":{
      "__origin":"0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d",
      "__inputHex":"0x..."
    }
  }'
```

### Con Nodo con Métodos RPC (Futuro)

```bash
# Despliegue automático funcionaría
curl -X POST http://localhost:8080/contracts/wasm/deploy \
  -H 'Content-Type: application/json' \
  -d '{"name":"mi-contrato","wasmBase64":"..."}'

# Invocar (acepta SS58/SUri)
curl -X POST http://localhost:8080/contracts/invoke \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"mi-contrato",
    "method":"call",
    "args":{
      "__origin":"//Alice",
      "__inputHex":"0x..."
    }
  }'
```

## Próximos Pasos (Opcional)

Si en el futuro se quiere agregar métodos RPC a `substrate-contracts-node`:

1. Agregar `pallet-contracts-rpc` al `Cargo.toml`
2. Registrar en `node/src/rpc.rs`
3. Recompilar

El sistema los detectará automáticamente y los usará.

## Verificación

Para verificar las capacidades detectadas, revisa los logs al inicio:

```bash
tail -f logs/node-0.log | grep -i "capacidades\|capabilities"
```

