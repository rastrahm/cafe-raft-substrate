## Contratos WASM (Fase 2)

### Estado actual
- Se añadieron tipos y endpoints para **desplegar contratos WASM**:
  - `POST /contracts/wasm/deploy` con `{ name, wasmBase64 }`
- El runtime WASM está **en stub funcional**:
  - ✅ Validación de módulos WASM (mágico 0x00 0x61 0x73 0x6d)
  - ✅ Almacenamiento de módulos desplegados
  - ✅ Registro de contratos tipo `WasmContract`
  - ⚠️ Ejecución pendiente: requiere integrar runtime WASM (Wasmer-Java, Wasmtime-Java, etc.)

### Compatibilidad con Solidity
- Solidity compila a **EVM bytecode** por defecto, no a WASM.
- Para usar Solidity sobre WASM, se puede emplear **Solang** (compilador Solidity→WASM) con perfiles de objetivo como Substrate/Solana. Requisitos:
  - Compilar el contrato con Solang a `.wasm` compatible.
  - Definir un **ABI** simple (método+args) que el runtime mapeará.
- Alternativa: integrar una **EVM** (mucho más compleja). Recomendado: camino Solang→WASM.

### Próximos pasos técnicos
1) Integrar un runtime WASM en Java (candidatos: Wasmtime-Java, Wasmer-Java, Wasm3 JNI).
2) Definir ABI:
   - Entrada: `method` + `args` JSON → serialización binaria para WASM.
   - Salida: bytes → JSON.
   - Acceso a estado: funciones host (KV por contrato, deterministas).
3) Límites: CPU/memoria/pasos (gas-like) y sandbox.
4) Herramientas: script para compilar Solidity con **Solang** a WASM y empaquetar en Base64.

### Ejemplo de despliegue (placeholder)
```bash
curl -X POST http://localhost:8080/contracts/wasm/deploy \
  -H 'Content-Type: application/json' \
  -d '{"name":"mywasm","wasmBase64":"<base64_del_modulo>"}'
```

> Nota: hasta integrar el runtime, las invocaciones de `WasmContract` devolverán `UnsupportedOperationException`.


