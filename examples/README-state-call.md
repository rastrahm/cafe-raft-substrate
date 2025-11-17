# Uso de `state_call` para Invocar Contratos en Substrate

## Resumen

Café Raft ahora soporta invocación de contratos WASM usando `state_call` de Substrate, que permite llamar a métodos del runtime API (`ContractsApi_call`) sin necesidad de métodos RPC específicos de contratos.

## Flujo Completo

### 1. Desplegar Contrato en Substrate

Como `substrate-contracts-node` no expone `contracts_instantiateWithCode`, necesitas desplegar el contrato manualmente:

**Opción A: Usando `cargo contract`**
```bash
# Compilar contrato
cargo contract build --release

# Subir código
cargo contract upload --suri //Alice --url ws://127.0.0.1:9944

# Instanciar contrato
cargo contract instantiate --suri //Alice --url ws://127.0.0.1:9944 \
  --constructor new --args "valor_inicial"
```

**Opción B: Usando Polkadot-JS Apps**
1. Abre https://polkadot.js.org/apps
2. Conecta a `ws://127.0.0.1:9944`
3. Ve a "Contracts" → "Upload & deploy code"
4. Sube el `.wasm` y el `.contract` (metadata)
5. Instancia el contrato
6. Copia la dirección del contrato (AccountId)

### 2. Registrar Dirección en Café Raft

Una vez que tengas la dirección del contrato en Substrate:

```bash
curl -X POST http://localhost:8080/contracts/wasm/register-address \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "mi-contrato",
    "address": "0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
  }'
```

**Nota:** La dirección debe ser el AccountId del contrato en formato hex (32 bytes).

### 3. Invocar Contrato usando `state_call`

```bash
curl -X POST http://localhost:8080/contracts/invoke \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "mi-contrato",
    "method": "call",
    "args": {
      "__origin": "0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d",
      "__inputHex": "0x<datos-scale-encoded>",
      "__gasLimit": "500000000000",
      "__storageDepositLimit": "0"
    }
  }'
```

**Parámetros:**
- `__origin`: AccountId del origin (quien invoca) en formato hex (32 bytes)
- `__inputHex`: Datos SCALE-encoded de la llamada (selector de función + parámetros)
- `__gasLimit`: Límite de gas (opcional, por defecto 500000000000)
- `__storageDepositLimit`: Límite de depósito de almacenamiento (opcional, por defecto 0)

### 4. Codificar Datos SCALE

Los datos en `__inputHex` deben incluir:
1. **Selector de función**: 4 bytes (primeros 4 bytes del hash de la firma de la función)
2. **Parámetros**: Codificados en SCALE según sus tipos

**Ejemplo para un contrato Ink! con función `get()`:**
```bash
# Selector de get() en Ink! suele ser 0x2f865bd9
# Sin parámetros, solo el selector
INPUT_HEX="0x2f865bd9"
```

**Ejemplo para `set(value: u32)`:**
```python
# Selector (ejemplo): 0x633aa551
# Parámetro u32=42 en little-endian: 0x2a000000
INPUT_HEX="0x633aa5512a000000"
```

## AccountIds Conocidos (Modo Desarrollo)

En `substrate-contracts-node` en modo desarrollo:

- **Alice**: `0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d`
- **Bob**: `0x8eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48`
- **Charlie**: `0x90b5ab205c6974c9ea841be6888646336379e8c5e6b5b5b5b5b5b5b5b5b5b5b5`

## Comportamiento del Sistema

1. **Si `__origin` es AccountId hex (64 caracteres)**: 
   - Café Raft intenta usar `state_call` con `ContractsApi_call`
   - Si falla, usa el método antiguo como fallback

2. **Si `__origin` es SS58/SUri**:
   - Café Raft usa el método antiguo (`contracts_call` dry-run)
   - Esto requiere que el nodo tenga métodos RPC de contratos (no disponible en `substrate-contracts-node`)

## Verificación

Para verificar que `state_call` está funcionando, revisa los logs:

```bash
tail -f logs/node-0.log | grep -i "state_call\|ContractsApi"
```

Deberías ver mensajes como:
```
Llamando ContractsApi_call con params: 0x...
Resultado state_call Substrate (contrato::método) => {...}
```

## Troubleshooting

**Error: "No se ha registrado la dirección del contrato"**
- Solución: Registra la dirección usando `POST /contracts/wasm/register-address`

**Error: "Se requiere AccountId en formato hex"**
- Solución: Usa AccountId en formato hex (32 bytes) en lugar de SS58/SUri

**Error: "Bad input data provided to call"**
- Solución: Verifica que `__inputHex` esté correctamente codificado en SCALE

**Error: "Method not found"**
- Solución: El nodo Substrate no tiene el método RPC. Usa `state_call` en su lugar (ya implementado)

