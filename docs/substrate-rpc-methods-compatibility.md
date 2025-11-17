# Compatibilidad con Métodos RPC de Substrate

## Escenario: Nodo con Métodos RPC `contracts_*` Disponibles

Si actualizamos `substrate-contracts-node` a una versión que exponga los métodos RPC `contracts_*` (como `contracts_instantiateWithCode`, `contracts_call`, etc.), el código actual funcionaría **inmediatamente** sin cambios, pero podríamos mejorarlo.

## Comportamiento Actual

### 1. **Invocación de Contratos** (`invokeViaSubstrate`)

**Lógica actual:**
```java
// Intenta state_call primero (si origin es AccountId hex)
try {
    callViaStateCall(...)  // Usa ContractsApi_call vía state_call
} catch (IllegalArgumentException e) {
    // Fallback a método RPC directo
    dryRunCall(...)  // Usa contracts_call RPC
}
```

**Si el nodo tiene `contracts_call` RPC:**
- ✅ Funcionaría automáticamente en el fallback
- ✅ Aceptaría SS58/SUri sin necesidad de conversión
- ✅ Más simple de usar (no requiere AccountId hex)

### 2. **Despliegue de Contratos** (`deployViaSubstrate`)

**Lógica actual:**
```java
// Siempre intenta contracts_instantiateWithCode
instantiateWithCode(...)  // Lanza excepción si no existe
```

**Si el nodo tiene `contracts_instantiateWithCode` RPC:**
- ✅ Funcionaría automáticamente
- ✅ Despliegue automático funcionaría sin cambios
- ✅ No necesitaría registro manual de direcciones

## Mejoras Propuestas

### Opción 1: Detección Automática de Métodos Disponibles

Agregar detección al inicio para saber qué métodos están disponibles:

```java
@Component
public class SubstrateCapabilities {
    private final SubstrateRpcClient rpcClient;
    private Boolean hasContractsRpc = null;
    
    @PostConstruct
    void detectCapabilities() {
        try {
            JsonNode methods = rpcClient.call("rpc_methods");
            JsonNode methodsList = methods.get("methods");
            hasContractsRpc = methodsList.toString().contains("contracts_");
            log.info("Substrate capabilities: contracts_* RPC = {}", hasContractsRpc);
        } catch (Exception e) {
            log.warn("No se pudo detectar capacidades Substrate", e);
            hasContractsRpc = false;
        }
    }
    
    public boolean hasContractsRpc() {
        return hasContractsRpc != null && hasContractsRpc;
    }
}
```

### Opción 2: Estrategia de Fallback Mejorada

Mejorar `WasmEngine` para intentar métodos en orden de preferencia:

```java
private void deployViaSubstrate(String name, byte[] module) {
    // 1. Intentar contracts_instantiateWithCode (más simple)
    try {
        JsonNode response = substrateContractsClient.instantiateWithCode(...);
        // Éxito
        return;
    } catch (IllegalStateException e) {
        if (e.getMessage().contains("Method not found")) {
            // 2. Fallback a state_call + author_submitExtrinsic
            log.info("contracts_instantiateWithCode no disponible, usando state_call");
            deployViaStateCallAndExtrinsic(name, module);
        } else {
            throw e;
        }
    }
}

private Object invokeViaSubstrate(...) {
    // 1. Si origin es SS58/SUri y tenemos contracts_call RPC, usarlo
    if (substrateCapabilities.hasContractsRpc() && !isAccountIdHex(origin)) {
        return substrateContractsClient.dryRunCall(...);
    }
    
    // 2. Si origin es AccountId hex, usar state_call
    if (isAccountIdHex(origin)) {
        return substrateContractsClient.callViaStateCall(...);
    }
    
    // 3. Fallback: intentar convertir SS58 a AccountId
    // ...
}
```

### Opción 3: Configuración Explícita

Agregar configuración para forzar un método específico:

```yaml
substrate:
  enabled: true
  rpc-url: http://127.0.0.1:9944
  # Estrategia de invocación: "auto", "rpc-direct", "state-call"
  invocation-strategy: auto
  # Estrategia de despliegue: "auto", "rpc-direct", "state-call-extrinsic"
  deployment-strategy: auto
```

## Ventajas de Cada Método

### Métodos RPC Directos (`contracts_*`)
- ✅ Más simple de usar (acepta SS58/SUri)
- ✅ Mejor integración con herramientas existentes
- ✅ Menos código SCALE manual
- ❌ Requiere nodo con extensión `contracts-rpc`

### `state_call` (Runtime API)
- ✅ Disponible en todos los nodos Substrate
- ✅ Más bajo nivel (acceso directo al runtime)
- ✅ Útil para casos avanzados
- ❌ Requiere AccountId hex
- ❌ Requiere codificación SCALE manual

## Recomendación

**Estrategia Híbrida:**
1. **Detección automática** al inicio para saber qué métodos están disponibles
2. **Fallback inteligente**: intentar RPC directo primero, luego `state_call`
3. **Configuración opcional** para forzar un método específico si es necesario

Esto proporcionaría:
- ✅ Compatibilidad con nodos antiguos (solo `state_call`)
- ✅ Mejor experiencia con nodos modernos (RPC directo)
- ✅ Flexibilidad para casos especiales

## Implementación Sugerida

```java
@Service
public class WasmEngine {
    private final SubstrateCapabilities capabilities;
    
    private void deployViaSubstrate(String name, byte[] module) {
        if (capabilities.hasContractsRpc()) {
            // Usar método RPC directo (más simple)
            deployViaRpcDirect(name, module);
        } else {
            // Usar state_call + extrinsic (más complejo)
            deployViaStateCall(name, module);
        }
    }
    
    private Object invokeViaSubstrate(...) {
        if (capabilities.hasContractsRpc() && !isAccountIdHex(origin)) {
            // RPC directo acepta SS58/SUri
            return substrateContractsClient.dryRunCall(...);
        } else {
            // state_call requiere AccountId hex
            return substrateContractsClient.callViaStateCall(...);
        }
    }
}
```

## Conclusión

Si actualizamos a un nodo con métodos RPC `contracts_*`:
- ✅ **Funcionaría inmediatamente** sin cambios
- ✅ **Despliegue automático** funcionaría
- ✅ **Invocación con SS58/SUri** funcionaría
- 🔧 **Mejora opcional**: detección automática para mejor experiencia

