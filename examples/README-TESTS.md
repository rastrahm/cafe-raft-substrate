# Guía de Tests para Integración Substrate

## Tests Disponibles

### 🎯 **Recomendado: `test-complete-substrate.sh`**
**Test completo de integración con Substrate v0.42.0**

```bash
./examples/test-complete-substrate.sh
```

**Qué prueba:**
- ✅ Verifica que ambos servidores estén corriendo
- ✅ Verifica detección automática de capacidades
- ✅ Verifica métodos RPC disponibles
- ✅ Despliega un contrato de prueba
- ✅ Verifica logs para ver qué método se usó

**Cuándo usarlo:**
- Para probar todo el flujo completo
- Para verificar que la detección automática funciona
- Para ver el estado general del sistema

---

### 🔍 **`test-detection-capabilities.sh`**
**Test específico de detección automática**

```bash
./examples/test-detection-capabilities.sh
```

**Qué prueba:**
- ✅ Verifica que Substrate esté corriendo
- ✅ Lista métodos RPC `contracts_*` disponibles
- ✅ Prueba detección manual
- ✅ Despliega contrato y verifica logs

**Cuándo usarlo:**
- Para verificar específicamente la detección automática
- Para ver qué métodos RPC están disponibles
- Para debuggear problemas de detección

---

### 📞 **`test-state-call.sh`**
**Test básico de state_call**

```bash
./examples/test-state-call.sh
```

**Qué prueba:**
- ✅ Verifica que `ContractsApi_call` esté disponible
- ✅ Despliega un contrato
- ✅ Verifica referencias a `state_call` en logs

**Cuándo usarlo:**
- Para verificar que `state_call` funciona
- Para probar la disponibilidad del runtime API

---

### 🔧 **`test-state-call-invoke.sh`**
**Test de invocación usando state_call**

```bash
./examples/test-state-call-invoke.sh
```

**Qué prueba:**
- ✅ Verifica contrato registrado
- ✅ Muestra cómo invocar usando `state_call`
- ✅ Prueba invocación (requiere dirección registrada)

**Cuándo usarlo:**
- Para probar invocación de contratos
- Para ver ejemplos de uso de `state_call`
- Cuando ya tienes un contrato desplegado

---

### 📋 **`test-invoke-state-call.sh`**
**Test de invocación con ejemplos**

```bash
./examples/test-invoke-state-call.sh
```

**Qué prueba:**
- ✅ Muestra ejemplos de invocación
- ✅ Explica cómo usar AccountId hex
- ✅ Muestra formato de datos SCALE

**Cuándo usarlo:**
- Para aprender cómo invocar contratos
- Para ver ejemplos de uso
- Como referencia de documentación

---

## Recomendación por Escenario

### 🚀 **Primera vez / Prueba completa**
```bash
./examples/test-complete-substrate.sh
```
Este es el test más completo y te da una visión general de todo.

### 🔍 **Verificar detección automática**
```bash
./examples/test-detection-capabilities.sh
```
Específico para ver qué capacidades detectó el sistema.

### 📞 **Probar invocación de contratos**
```bash
# Primero despliega y registra dirección manualmente
./examples/test-state-call-invoke.sh
```

### 🐛 **Debuggear problemas**
```bash
# Ver logs de detección
tail -f logs/node-0.log | grep -i "capacidades\|capabilities"

# Ver métodos RPC disponibles
curl -s -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"rpc_methods","params":[]}' \
  http://127.0.0.1:9944 | python3 -m json.tool | grep contracts
```

## Flujo Recomendado

1. **Iniciar servidores:**
   ```bash
   # Terminal 1: Substrate
   cd substrate-contracts-node
   ./target/release/substrate-contracts-node --dev --tmp --rpc-external --rpc-methods=Unsafe
   
   # Terminal 2: Café Raft
   ./scripts/cafe-raft.sh start-rest
   ```

2. **Ejecutar test completo:**
   ```bash
   ./examples/test-complete-substrate.sh
   ```

3. **Revisar resultados:**
   - Ver logs: `tail -f logs/node-0.log`
   - Verificar detección: buscar "Capacidades Substrate detectadas"
   - Verificar despliegue: buscar "WASM almacenado"

## Notas

- Todos los tests verifican que los servidores estén corriendo
- Los tests crean contratos temporales (puedes limpiarlos después)
- Los logs muestran qué método se usó (RPC directo vs `state_call`)

