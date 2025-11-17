# Capacidades actuales del sistema

## Ejecución de contratos

- **Contratos JVM (nativos)**  
  - Cualquier clase Java que implemente `gc.garcol.caferaft.application.contract.Contract`.  
  - Despliegue dinámico vía `POST /contracts/deploy` (`name`, `className`).  
  - Ejecución vía `POST /contracts/invoke` (método + argumentos).  
  - Estado replicado a través de Raft.

- **Contratos WebAssembly (WASM)**  
  - Soportados si el módulo es “standalone” (sin imports externos).  
  - Despliegue vía `POST /contracts/wasm/deploy` enviando el WASM en Base64.  
  - Persistencia en disco (`storage/wasm` por defecto) y restauración automática al reiniciar.  
  - Ejemplo operativo: `examples/add.wasm`.
  - *Limitación actual*: módulos que dependen de host functions externas (ej. `seal_*` de Substrate) fallan, porque Wasmtime-Java no provee esas funciones. Para ellos se usa el modo Substrate descrito abajo.

## Infraestructura

- Clúster Raft controlado con `./scripts/cafe-raft.sh` (start, stop, status).  
- Endpoints REST expuestos en el puerto configurado (por defecto 8080):  
  - `/contracts/deploy`, `/contracts/invoke`, `/contracts/wasm/deploy`, `/contracts/wasm/register-address`  
  - `/logs`, `/actuator/health`, `/swagger-ui/index.html`
- Persistencia de estados/contratos replicada entre nodos (directorio `disk-node-*`).  
- Logs detallados en `logs/node-*.log`.

## Componentes principales

- `ContractEngine`: orquesta despliegue/invocación de contratos JVM/WASM.  
- `WasmEngine`: carga, precompila y persiste módulos WASM usando Wasmtime.  
- `ContractRegistry`: mantiene el registro de contratos disponibles.  
- `DocumentSignerContractConfiguration`: ejemplo de registro automático de contrato JVM.

## Limitaciones conocidas

- No se soporta Wasm con host functions específicas (Substrate `seal_*`, Solana, Soroban, etc.).  
- No hay integración con Polkadot/EVM nativa; los contratos con imports externos entran en modo “stub”.  
- La persistencia actual guarda el binario WASM, pero no implementa snapshots de memoria/estado del contrato.

---

## Investigación: soporte `seal_*` (Substrate / Polkadot)

### Contexto

Los contratos compilados con `solang --target polkadot` esperan host functions expuestas por el pallet `contracts` de Substrate. Nuestro backend, al ejecutar con Wasmtime-Java, no define esas funciones; el resultado es el error `WasmtimeException: expected N imports, found 0`.

### Funciones host observadas

Las importaciones más comunes residen en el módulo `seal0` (y para versiones nuevas `seal1`). Ejemplos:

| Función                    | Propósito / Comentario breve                                                   |
|----------------------------|--------------------------------------------------------------------------------|
| `seal0.seal_input`         | Lee la entrada proporcionada al contrato.                                      |
| `seal0.seal_return`        | Devuelve datos a la llamada externa y termina la ejecución.                    |
| `seal0.seal_set_storage`   | Escribe un valor en el storage persistente.                                    |
| `seal0.seal_get_storage`   | Lee un valor del storage persistente.                                          |
| `seal0.seal_clear_storage` | Elimina una entrada del storage.                                               |
| `seal0.seal_contains_storage` | Verifica si existe una clave en el storage.                                 |
| `seal0.seal_get_storage_origin` | Obtiene la clave original del storage (para iteraciones).                |
| `seal0.seal_transfer`      | Transfiere valor (tokens) a otra cuenta.                                       |
| `seal0.seal_call`          | Invoca a otro contrato.                                                        |
| `seal0.seal_delegate_call` | Ejecuta el código de otro contrato en el contexto del actual.                  |
| `seal0.seal_instantiate`   | Despliega un nuevo contrato.                                                   |
| `seal0.seal_terminate`     | Finaliza el contrato actual transfiriendo su saldo.                           |
| `seal0.seal_restore_to`    | Restaura estado a un contrato previamente guardado.                            |
| `seal0.seal_address`       | Devuelve la dirección del contrato actual.                                     |
| `seal0.seal_balance`       | Devuelve el balance del contrato.                                              |
| `seal0.seal_value_transferred` | Valor transferido junto con la llamada.                                    |
| `seal0.seal_minimum_balance` | Balance mínimo del sistema (existencial).                                   |
| `seal0.seal_random`        | Entropía pseudoaleatoria provista por la cadena.                               |
| `seal0.seal_now`           | Timestamp actual del bloque.                                                   |
| `seal0.seal_weight_to_fee` | Conversión de peso (compute weight) a fee.                                     |
| `seal0.seal_gas_left`      | Gas restante (actualmente llamado “weight”).                                   |
| `seal0.seal_debug_message` / `seal_println` | Mensajes de depuración en logs.                             |
| `seal0.seal_hash_sha2_256`, `seal_hash_keccak_256`, `seal_hash_blake2_256` | Funciones de hash.            |

> Nota: Substrate introduce variantes “lim” (`seal1.*`) con firmas actualizadas; deberían considerarse para compatibilidad futura.

#### Firmas y tipos observados

Basado en la documentación de [`pallet-contracts`](https://github.com/paritytech/substrate/tree/master/frame/contracts) y en el ABI descrito en la wiki de Substrate:

| Función (`seal0`) | Firma (valores de Wasm) | Detalles clave |
|-------------------|-------------------------|----------------|
| `seal_input` | `(out_ptr: i32, out_len_ptr: i32)` | Copia el payload de entrada; `out_len_ptr` escribe el tamaño devuelto. |
| `seal_return` | `(flags: i32, data_ptr: i32, data_len: i32)` | Devuelve bytes al caller y termina la ejecución. |
| `seal_set_storage` | `(key_ptr: i32, key_len: i32, value_ptr: i32, value_len: i32)` | Guarda una entrada en storage; claves y valores son buffers de memoria lineal. |
| `seal_get_storage` | `(key_ptr: i32, key_len: i32, out_ptr: i32, out_len_ptr: i32)` -> `i32` | Devuelve 0 si existe. Usa `out_len_ptr` para indicar el tamaño real; errores típicos: `ReturnCode::BufferTooSmall`. |
| `seal_clear_storage` | `(key_ptr: i32, key_len: i32)` | Elimina una clave. |
| `seal_contains_storage` | `(key_ptr: i32, key_len: i32)` -> `i32` | 1 si existe, 0 si no. |
| `seal_get_storage_origin` | `(key_ptr: i32, key_len: i32)` -> `i32` | Obtiene la clave “origin” (para iteraciones). |
| `seal_transfer` | `(callee_ptr: i32, callee_len: i32, value_ptr: i32, value_len: i32)` -> `i32` | `value_*` suele ser un balance de 128 bits. |
| `seal_call` | `(call_flags: i32, callee_ptr: i32, callee_len: i32, gas: i64, value_ptr: i32, value_len: i32, input_ptr: i32, input_len: i32, output_ptr: i32, output_len_ptr: i32)` -> `i32` | Realiza una llamada a otro contrato. `output_len_ptr` actúa igual que en `seal_get_storage`. |
| `seal_delegate_call` | `(call_flags: i32, code_hash_ptr: i32, code_hash_len: i32, input_ptr: i32, input_len: i32, output_ptr: i32, output_len_ptr: i32)` -> `i32` | Ejecuta otro código en el mismo contexto (sin cambiar storage). |
| `seal_instantiate` | `(code_hash_ptr: i32, code_hash_len: i32, gas: i64, endowment_ptr: i32, endowment_len: i32, input_ptr: i32, input_len: i32, salt_ptr: i32, salt_len: i32, output_ptr: i32, output_len_ptr: i32, address_ptr: i32, address_len_ptr: i32)` -> `i32` | Despliega otro contrato; devuelve la dirección en `address_ptr`. |
| `seal_terminate` | `(beneficiary_ptr: i32, beneficiary_len: i32)` | Finaliza el contrato y transfiere su saldo. No retorna. |
| `seal_random` | `(subject_ptr: i32, subject_len: i32, out_ptr: i32, out_len: i32)` -> `i32` | Obtiene entropía pseudoaleatoria. |
| `seal_address` | `(out_ptr: i32)` | Copia la dirección actual (32 bytes). |
| `seal_balance` | `(out_ptr: i32)` | Copia el balance actual (16 bytes). |
| `seal_value_transferred` | `(out_ptr: i32)` | Copia el valor transferido en la llamada. |
| `seal_minimum_balance` | `(out_ptr: i32)` | Copia el balance existencial del sistema. |
| `seal_now` | `()` -> `i64` | Devuelve el timestamp del bloque. |
| `seal_weight_to_fee` | `(weight_ptr: i32, out_ptr: i32)` | Convierte peso en tarifa. |
| `seal_gas_left` | `()` -> `i64` | Gas/weight restante. |
| `seal_debug_message` | `(ptr: i32, len: i32)` | Imprime un mensaje de depuración. |
| `seal_hash_sha2_256` | `(data_ptr: i32, data_len: i32, out_ptr: i32)` | Escribe el hash en `out_ptr` (32 bytes). Existen versiones para Keccak y Blake2. |

**Convenciones generales**
- Punteros y longitudes son `i32` (memoria lineal del contrato).  
- Valores monetarios (`Balance`) se pasan como buffers (`value_len = 16`).  
- Los códigos de retorno (`i32`) siguen `ReturnCode` (0 = éxito, negativos para error).  
- Gas/peso se maneja en `i64`.  
- Las funciones `seal1.*` introducen firmas ligeramente distintas (por ejemplo, estructuras `InstructionWeights`).

### Consideraciones para portarlo al backend

1. **Definir funciones `Func` en Wasmtime-Java** con las firmas correctas (punteros, longitudes, códigos de retorno).  
2. **Implementar la lógica** de cada host function:  
   - Storage persistente y replicado (mapeo clave/valor).  
   - Transfers / balances (modelo económico).  
   - Randomness, timestamps, addresses… (o stubs deterministas).  
   - Manejo de gas (weight/fees) y control de ejecución.  
3. **Compatibilidad con Substrate**: la semántica debe coincidir con la especificación del pallet `contracts` para que los contratos Solidity/WASM funcionen de manera idéntica a como lo hacen en una parachain.

### Próximos pasos sugeridos

- Listar firmas exactas de cada `seal_*` (tipos y convenciones de memoria).  
- Evaluar si existen bindings o ejemplos en Java/Rust para reutilizar.  
- Diseñar cómo mapear almacenamiento y balances a nuestras estructuras replicadas.  
- Definir qué funciones se pueden stubear inicialmente y cuáles son imprescindibles.

---

## Integración con `substrate-contracts-node`

### Paso 2: Cliente RPC en Java

- Se añadió `SubstrateRpcClient`, que envía llamadas JSON‑RPC al nodo (por defecto `http://127.0.0.1:9944`).  
- `SubstrateContractsClient` encapsula métodos de alto nivel (`system_health`, `state_getMetadata`, `contracts_call`, `contracts_instantiateDryRun`).  
- Los dry-runs esperan entradas SCALE ya codificadas (`__inputHex`), valor/gas opcional y origen (`//Alice` por defecto).  
- En modo Substrate (`substrate.enabled=true`), `WasmEngine.invoke` delega en `SubstrateContractsClient`. Se requiere registrar manualmente la dirección (`registerSubstrateAddress`) tras desplegar via CLI/extrinsic.  
- Configuración nueva en `application.yaml`: URLs y límites de gas por defecto.

### Paso 3: Ejecución conjunta

**Preparación**
- Compilar/descargar `substrate-contracts-node` y lanzarlo en modo dev:
  ```bash
  ./target/release/substrate-contracts-node --dev --tmp
  ```
  El RPC queda expuesto en `http://127.0.0.1:9944`.
- Habilitar la integración en `application.yaml`:
  ```yaml
  substrate:
    enabled: true
    rpc-url: http://127.0.0.1:9944
    default-origin: //Alice
    default-gas-limit: 500000000000
    default-storage-deposit-limit: 0
  ```

**Arranque**
- Desde la raíz del proyecto:
  ```bash
  ./scripts/cafe-raft.sh stop   # opcional, por si había nodos previos
  ./scripts/cafe-raft.sh start-rest
  ./scripts/cafe-raft.sh status # comprueba /actuator/health
  ```
- Asegúrate de que ambos procesos (nodo Substrate y clúster Raft) queden corriendo.

**Flujo de trabajo**
1. Con `substrate.auto-deploy=true`, basta con `POST /contracts/wasm/deploy`: se llama a `contracts_instantiateWithCode`, se obtiene la dirección y se guarda para futuras invocaciones.
2. Si prefieres un flujo manual (`substrate.auto-deploy=false`), despliega el WASM en `substrate-contracts-node` (CLI, `cargo-contract`, Polkadot-JS…) y registra la dirección:
   ```bash
   curl -X POST http://localhost:8080/contracts/wasm/register-address \
        -H "Content-Type: application/json" \
        -d '{"name":"miContrato","address":"0x..."}'
   ```
3. Invoca funciones usando payload SCALE (`__inputHex`) más parámetros opcionales (`__value`, `__gasLimit`, `__origin`). El backend hace un `contracts_call` (dry-run) y devuelve la respuesta del nodo Substrate.
- Para contratos Wasm standalone (sin imports `seal_*`) se puede seguir usando `deployWasm`, que ahora detecta fallos y cae a modo “stub” sin spamear logs.

### Próximos pasos
- Implementar construcción y firma de extrinsics (`contracts_upload_code`, `contracts_instantiate`, `contracts_call`).  
- Automatizar el mapeo nombre → dirección leyendo eventos/`contractsInstantiateDryRun`.  
- Encapsular la codificación SCALE (método → selector + args) para no depender de `__inputHex`.  
- Añadir comandos/scripts que sincronicen Café Raft con `substrate-contracts-node` (start/stop, health-checks).

---

## Próximos pasos (generales)

- Ampliar contratos JVM (paquete `contract.sc`) para cubrir casos de negocio.  
- Continuar la investigación de host functions `seal_*`.  
- Añadir tests automatizados y monitorización adicional (métricas, dashboards).

