# Guía de Verificación de Sincronización Automática

## Estado Actual

✅ **Implementación completada**:
- Sincronización automática después de invocaciones
- Detección automática de métodos de lectura
- Configuración flexible

✅ **Configuración verificada**:
```yaml
substrate:
  auto-sync-after-invoke: true  # ✅ Habilitado
  sync-method: get  # Método por defecto
```

## Pasos para Verificar

### 1. Verificar que el servidor esté corriendo

```bash
curl http://localhost:8080/actuator/health
```

Debería responder con el estado del servidor.

### 2. Verificar que el contrato esté registrado

```bash
# Verificar que el contrato Counter esté desplegado
curl -X GET http://localhost:8080/contracts/wasm/state?name=Counter
```

Si el contrato no está registrado, primero:
1. Despliega el contrato: `./examples/deploy-counter.sh`
2. Registra la dirección: `curl -X POST http://localhost:8080/contracts/wasm/register-address -H 'Content-Type: application/json' -d '{"name":"Counter","address":"0x<direccion>"}'`

### 3. Invocar el contrato (esto activará la sincronización automática)

**Importante**: Necesitas el `inputHex` SCALE-encoded del método que quieres invocar.

Ejemplo para Counter::increment:
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

**Nota**: Reemplaza `0x...` con el inputHex SCALE-encoded real del método `increment`.

### 4. Verificar que la sincronización se ejecutó

#### a) Consultar el estado sincronizado

```bash
curl -X GET http://localhost:8080/contracts/wasm/state?name=Counter | jq '.'
```

Deberías ver:
```json
{
  "contractName": "Counter",
  "stateHex": "0x...",
  "lastSyncTimestamp": 1234567890,
  "lastBlockNumber": 1234567890
}
```

#### b) Verificar los logs

```bash
# Ver mensajes de sincronización
tail -50 logs/node-0.log | grep -i "sincroniz\|auto.*sync"

# O ver en tiempo real
tail -f logs/node-0.log | grep -i "sincroniz\|auto.*sync"
```

Deberías ver mensajes como:
```
Sincronizando automáticamente estado de Counter::get después de invocación
Estado sincronizado automáticamente para Counter
```

### 5. Verificar que el timestamp cambió

Invoca el contrato nuevamente y compara los timestamps:

```bash
# Antes
TIMESTAMP1=$(curl -s http://localhost:8080/contracts/wasm/state?name=Counter | jq -r '.lastSyncTimestamp')
echo "Timestamp antes: $TIMESTAMP1"

# Invocar contrato aquí...

# Después (esperar 2 segundos)
sleep 2
TIMESTAMP2=$(curl -s http://localhost:8080/contracts/wasm/state?name=Counter | jq -r '.lastSyncTimestamp')
echo "Timestamp después: $TIMESTAMP2"

if [ "$TIMESTAMP2" != "$TIMESTAMP1" ]; then
    echo "✅ Sincronización funcionó! Timestamp cambió."
else
    echo "⚠️  Timestamp no cambió. Verifica logs para errores."
fi
```

## Scripts de Verificación

### Script de verificación general

```bash
./examples/verify-auto-sync.sh
```

Este script verifica:
- Estado del servidor
- Configuración
- Estado actual del contrato
- Muestra instrucciones para invocar

### Script de prueba completa

```bash
./examples/test-sync-complete.sh
```

Este script:
- Verifica el estado antes de invocar
- Te pide que invoques el contrato
- Verifica el estado después
- Compara timestamps
- Muestra logs relevantes

## Troubleshooting

### El estado no se sincroniza

1. **Verifica la configuración**:
   ```bash
   grep "auto-sync-after-invoke" raft-application-spring/src/main/resources/application.yaml
   ```
   Debe ser `true`.

2. **Verifica los logs para errores**:
   ```bash
   tail -100 logs/node-0.log | grep -i "error\|exception\|warn" | tail -20
   ```

3. **Verifica que el contrato esté registrado**:
   ```bash
   curl -X GET http://localhost:8080/contracts/wasm/state?name=Counter
   ```
   Si devuelve 404, el contrato no está registrado.

4. **Verifica que la dirección del contrato esté registrada**:
   Los logs deberían mostrar: `Asociado contrato Counter a dirección Substrate 0x...`

### La sincronización falla silenciosamente

La sincronización automática maneja errores silenciosamente para no afectar la respuesta de la invocación. Los errores se registran en los logs con nivel `WARN`:

```bash
tail -100 logs/node-0.log | grep -i "error.*sincroniz\|warn.*sincroniz"
```

### El método de sincronización no es correcto

Por defecto, se usa el método `get`. Si tu contrato usa otro método para obtener el estado:

1. **Si el método invocado es de lectura** (empieza con `get`, `read`, `query`, `view`), se usa automáticamente.

2. **Si no es de lectura**, se usa `sync-method`. Puedes cambiarlo en `application.yaml`:
   ```yaml
   substrate:
     sync-method: tu_metodo_de_lectura
   ```

## Ejemplo Completo

```bash
# 1. Verificar servidor
curl http://localhost:8080/actuator/health

# 2. Verificar estado actual
curl -X GET http://localhost:8080/contracts/wasm/state?name=Counter

# 3. Invocar contrato (reemplaza 0x... con inputHex real)
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

# 4. Esperar 2 segundos
sleep 2

# 5. Verificar estado sincronizado
curl -X GET http://localhost:8080/contracts/wasm/state?name=Counter | jq '.'

# 6. Ver logs
tail -20 logs/node-0.log | grep -i "sincroniz"
```

## Resultado Esperado

Después de invocar el contrato, deberías ver:

1. **En los logs**:
   ```
   Sincronizando automáticamente estado de Counter::get después de invocación
   Estado sincronizado para Counter: 0x... (block: ...)
   Estado sincronizado automáticamente para Counter
   ```

2. **En la respuesta del estado**:
   ```json
   {
     "contractName": "Counter",
     "stateHex": "0x...",
     "lastSyncTimestamp": <timestamp_actualizado>,
     "lastBlockNumber": <block_number>
   }
   ```

3. **El timestamp debe cambiar** después de cada invocación.

