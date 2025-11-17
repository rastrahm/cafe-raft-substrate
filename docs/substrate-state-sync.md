# Sincronización de Estado entre Café Raft y Substrate

## Problema

Actualmente, cuando se invoca un contrato WASM en modo Substrate:
1. El comando se registra en el log de Raft (trazabilidad)
2. La invocación se ejecuta en Substrate (modifica estado en Substrate)
3. **El estado se mantiene SOLO en Substrate, no en Raft**

Esto crea una inconsistencia: Raft tiene el log de comandos, pero el estado real está en Substrate.

## Solución Propuesta

### Arquitectura

```
┌─────────────────┐         ┌──────────────────┐
│   Café Raft     │         │  Substrate Node  │
│                 │         │                  │
│  ┌───────────┐  │         │  ┌────────────┐  │
│  │ Raft Log  │  │         │  │ Contract   │  │
│  │ (Commands)│  │         │  │ State      │  │
│  └───────────┘  │         │  └────────────┘  │
│                 │         │                  │
│  ┌───────────┐  │◄────────┤  Query State     │
│  │ Sync State│  │         │  (state_call)    │
│  │ (Cache)   │  │         │                  │
│  └───────────┘  │         └──────────────────┘
└─────────────────┘
```

### Componentes

1. **Estado Sincronizado en Raft**: Cache del estado del contrato desde Substrate
2. **Comando de Sincronización**: `SyncSubstrateContractStateCommand`
3. **Query de Estado**: `ContractStateQuery` para consultar el estado sincronizado
4. **Sincronización Automática**: Opcional, después de cada invocación o periódicamente

### Flujo de Sincronización

#### Opción 1: Sincronización automática después de cada invocación ✅ IMPLEMENTADO

```
1. Cliente → InvokeContractCommand
2. Raft → Registra comando en log
3. Raft → Ejecuta invocación en Substrate
4. Raft → Obtiene resultado de Substrate
5. Raft → [Automático] Sincroniza estado desde Substrate
6. Raft → Almacena estado en cache
7. Raft → Responde al cliente
```

**Ventajas**:
- Estado siempre actualizado
- Consistencia inmediata
- Trazabilidad completa
- Automático, sin intervención manual

**Desventajas**:
- Más latencia (2 llamadas a Substrate)
- Más carga en Substrate

**Configuración**: Habilitar con `substrate.auto-sync-after-invoke: true`

#### Opción 2: Sincronización periódica

```
1. Cliente → InvokeContractCommand
2. Raft → Registra comando en log
3. Raft → Ejecuta invocación en Substrate
4. Raft → Responde al cliente
5. [Background] → Sincroniza estado periódicamente
```

**Ventajas**:
- Menor latencia
- Menos carga en Substrate

**Desventajas**:
- Estado puede estar desactualizado temporalmente
- Requiere reconciliación

#### Opción 3: Híbrido (Recomendado para producción)

- Sincronización inmediata después de invocaciones críticas
- Sincronización periódica para el resto
- Reconciliación manual cuando se detectan inconsistencias

### Implementación

#### 1. Comando de Sincronización

```java
public record SyncSubstrateContractStateCommand(
    String contractName,
    String method,  // Método a invocar para obtener estado (ej: "get")
    String inputHex // Input SCALE-encoded para la query
) implements Command {}
```

#### 2. Query de Estado

```java
public record ContractStateQuery(String contractName) implements Query {}
```

#### 3. Almacenamiento de Estado

```java
// En WasmEngine o nuevo SubstrateStateSyncService
private final Map<String, ContractState> contractStates = new ConcurrentHashMap<>();

public record ContractState(
    String contractName,
    String stateHex,      // Estado SCALE-encoded desde Substrate
    long lastSyncTimestamp,
    long lastBlockNumber   // Block number de Substrate cuando se sincronizó
) {}
```

#### 4. Método de Sincronización

```java
public void syncContractState(String contractName, String method, String inputHex) {
    // 1. Obtener dirección del contrato
    String address = nameToSubstrateAddress.get(contractName);
    
    // 2. Invocar método de lectura en Substrate (state_call)
    JsonNode result = substrateContractsClient.callViaStateCall(
        originAccountId, address, BigInteger.ZERO, gasLimit, null, inputHex
    );
    
    // 3. Extraer estado del resultado
    String stateHex = extractStateFromResult(result);
    
    // 4. Almacenar en Raft (como comando para persistencia)
    // O almacenar en memoria con opción de persistir
    contractStates.put(contractName, new ContractState(
        contractName, stateHex, System.currentTimeMillis(), getCurrentBlockNumber()
    ));
}
```

### Reconciliación

Cuando se detecta una inconsistencia:

1. **Detección**: Comparar estado en Raft vs Substrate
2. **Resolución**: 
   - Si Raft está desactualizado → Sincronizar desde Substrate
   - Si Substrate está desactualizado → Log warning (no debería pasar si solo Raft invoca)
   - Si ambos difieren → Requiere intervención manual

### Configuración

```yaml
substrate:
  enabled: true
  auto-sync-after-invoke: true  # Sincronizar estado automáticamente después de cada invocación
  sync-method: get  # Método por defecto para sincronizar estado (usado si el método invocado no es de lectura)
```

**Propiedades**:
- `auto-sync-after-invoke`: Si es `true`, después de cada invocación de contrato Substrate, se sincroniza automáticamente el estado. Por defecto: `false`.
- `sync-method`: Método a invocar para obtener el estado cuando el método invocado no es de lectura. Por defecto: `"get"`.

**Detección automática de métodos de lectura**:
El sistema detecta automáticamente si un método es de lectura (no modifica estado) basándose en su nombre:
- Métodos que empiezan con `get`, `read`, `query`, `view`
- Si el método invocado es de lectura, se usa ese método para sincronizar
- Si no es de lectura, se usa `sync-method` (por defecto `"get"`)

### API

#### Sincronizar estado manualmente

```bash
curl -X POST http://localhost:8080/contracts/wasm/sync-state \
  -H 'Content-Type: application/json' \
  -d '{
    "contractName": "Counter",
    "method": "get",
    "inputHex": "0x"
  }'
```

O usar el script de ejemplo:
```bash
./examples/sync-contract-state.sh http://localhost:8080 Counter get 0x
```

#### Consultar estado sincronizado

```bash
curl -X GET http://localhost:8080/contracts/wasm/state?name=Counter
```

Respuesta:
```json
{
  "contractName": "Counter",
  "stateHex": "0x...",
  "lastSyncTimestamp": 1234567890,
  "lastBlockNumber": 1234567890
}
```

### Consideraciones

1. **Performance**: Sincronización inmediata añade latencia
2. **Consistencia**: Estado puede estar desactualizado entre sincronizaciones
3. **Persistencia**: Estado sincronizado puede persistirse en Raft o mantenerse en memoria
4. **Escalabilidad**: Para muchos contratos, sincronización periódica es más eficiente

