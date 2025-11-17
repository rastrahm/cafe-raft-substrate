# Café Raft

Plataforma de experimentación con Raft que soporta contratos en Java y WebAssembly.

---

## Contenido

- Resumen general
- Dependencias
- Flujo de contratos WASM
- Ejecución local
- Endpoints REST
- Wasmtime-Java
- Guías útiles

---

## Resumen general

1. **Raft Core**: implementación base de Raft.
2. **Raft Application (Spring Boot)**: expone API REST y orquesta despliegues/invocaciones.
3. **Soporte de contratos**:
   - Java (sigue disponible).
   - WebAssembly:
     - **Standalone**: módulos sin imports (Wasmtime-Java).
     - **Modo Substrate**: contratos desplegados en `substrate-contracts-node` (auto o manualmente) e invocados vía RPC.

---

## Dependencias

| Herramienta | Versión | Uso |
|-------------|---------|-----|
| JDK | 21 | Ejecutar Spring Boot con Wasmtime |
| Solang | ≥0.3.3 | Compilar Solidity → WASM |
| curl / jq | - | Invocar API REST |
| Wasmtime-Java | 0.18.0 | Runtime WASM dentro de la JVM |

Instala JDK 21:
```bash
sudo apt update && sudo apt install -y openjdk-21-jdk
```

Instala Solang (Linux x86_64):
```bash
curl -L -o solang-linux-x86-64 https://github.com/hyperledger/solang/releases/latest/download/solang-linux-x86-64
chmod +x solang-linux-x86-64
sudo mv solang-linux-x86-64 /usr/local/bin/solang
solang --version
```
(revisa la sección *Assets* de la release si necesitas otra plataforma o una versión concreta).

---

## Flujo de contratos WASM

1. **Desarrollo / pruebas**: usa Forge/Foundry normal (`forge test`).
2. **Compilación a WASM** (Solang):
   ```bash
   solang compile --target polkadot src/Contrato.sol -o out/
   ```
   *(Targets disponibles: `polkadot`, `solana`, `evm`, `soroban`. Usa el que convenga a tu contrato; `polkadot` genera un `.wasm` y un `.contract` aptos para Wasmtime.)*
3. **Despliegue** (`examples/deploy-wasm-example.sh`):
   ```bash
   ./examples/deploy-wasm-example.sh http://localhost:8080 out/Contrato.wasm
   ```
   El script:
   - Convierte a Base64.
   - Llama a `POST /contracts/wasm/deploy`.
   - Verifica en `/logs`.
   - Si `substrate.auto-deploy=true` y el nodo Substrate está activo, este paso llama internamente a `contracts_instantiateWithCode` y persiste la dirección resultante.
4. **Invocación (standalone)**:
   ```bash
   curl -X POST http://localhost:8080/contracts/invoke \
     -H "Content-Type: application/json" \
     -d '{"name":"contrato","method":"add","args":{"a":10,"b":25}}'
   ```

5. **Modo Substrate (contratos "reales")**
   - Activa `substrate.enabled=true` y ejecuta `substrate-contracts-node --dev --tmp` (o usa `./scripts/cafe-raft.sh start-rest` que lo inicia automáticamente).
   - Con `substrate.auto-deploy=true` (valor por defecto) basta con `POST /contracts/wasm/deploy`: el backend intenta subir el WASM con `contracts_instantiateWithCode` si está disponible, o guía al despliegue manual.
   - Si necesitas control manual (constructor con args específicos, cuentas distintas, etc.), pon `substrate.auto-deploy=false`, despliega con `cargo contract` / Polkadot-JS y registra la dirección:
     ```bash
     curl -X POST http://localhost:8080/contracts/wasm/register-address \
       -H "Content-Type: application/json" \
       -d '{"name":"voting","address":"0x...."}'
     ```
   - Las invocaciones requieren el payload SCALE (`__inputHex`):
     ```bash
     curl -X POST http://localhost:8080/contracts/invoke \
       -H "Content-Type: application/json" \
       -d '{"name":"voting","method":"call","args":{"__inputHex":"0x...", "__origin":"//Alice","__value":0}}'
     ```
   - El backend delega en `contracts_call` o `state_call` según disponibilidad; el estado del contrato permanece en el nodo Substrate.

6. **Sincronización de Estado (Modo Substrate)**
   - Con `substrate.auto-sync-after-invoke=true` (habilitado por defecto), después de cada invocación se sincroniza automáticamente el estado desde Substrate a Café Raft.
   - Consulta el estado sincronizado:
     ```bash
     curl -X GET http://localhost:8080/contracts/wasm/state?name=voting
     ```
   - Sincronización manual:
     ```bash
     curl -X POST http://localhost:8080/contracts/wasm/sync-state \
       -H "Content-Type: application/json" \
       -d '{"contractName":"voting","method":"get","inputHex":"0x"}'
     ```
   - Ver documentación completa en `docs/substrate-state-sync.md`.

---

## Ejecución local

### Iniciar nodos
```bash
cd scripts
./cafe-raft.sh start-rest
```
El script lanza:
- **Substrate contracts node** (puerto 9944) - se inicia automáticamente
- **Tres nodos Café Raft** (puertos 8080, 8081, 8082) - espera a que respondan

Para omitir Substrate: `START_SUBSTRATE=false ./cafe-raft.sh start-rest`

### Estado
```bash
./cafe-raft.sh status
```

### Detener
```bash
./cafe-raft.sh stop
```

> Por defecto el script fuerza `--spring.aot.enabled=false` para evitar conflictos AOT con múltiples nodos.

---

## Endpoints REST (nodo 0 por defecto)

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/` | GET | Dashboard simple |
| `/swagger-ui/index.html` | GET | Documentación OpenAPI |
| `/actuator/health` | GET | Health check |
| `/logs` | GET | Log Raft |
| `/contracts/wasm/deploy` | POST | Desplegar módulo WASM (local o Substrate) |
| `/contracts/wasm/register-address` | POST | Asociar un contrato Raft con la dirección Substrate (si `substrate.auto-deploy=false`) |
| `/contracts/wasm/sync-state` | POST | Sincronizar estado de contrato desde Substrate a Café Raft |
| `/contracts/wasm/state` | GET | Consultar estado sincronizado de un contrato |
| `/contracts/invoke` | POST | Invocar contrato (Java, WASM standalone o Substrate `contracts_call`) |

Ejemplo de despliegue manual:
```bash
curl -X POST http://localhost:8080/contracts/wasm/deploy \
  -H "Content-Type: application/json" \
  -d '{"name":"simple","wasmBase64":"BASE64..."}'
```

---

## Wasmtime-Java

**¿Qué es?**
Bindings oficiales en Java para el runtime Wasmtime (Bytecode Alliance). Permite ejecutar módulos WebAssembly desde la JVM con clases tipo `Engine`, `Module`, `Store`, `Instance`, `Func`, `Val`.

**Qué incluye (0.18.0)**
```
raft-application-spring/
 └─ libs/
     └─ wasmtime-java-0.18.0.jar
```

**Características clave**
- Compilación y caching de módulos (`Engine`).
- Instanciación controlada (`Store`, `Instance`).
- Acceso a funciones exportadas (`Func`) y memoria (`Memory`).
- Soporte multi‐plataforma (Linux/macOS/Windows x86_64).

**Por qué se usa aquí**
- Podemos ejecutar contratos Solidity compilados a WASM (Solang).
- Evitamos integrar runtimes nativos complejos fuera del ecosistema JVM.
- Permite sandboxing y ejecución determinista.

**No es la EVM**
 Wasmtime-Java ejecuta WebAssembly genérico; la EVM procesa bytecode específico de Ethereum con reglas de gas, estado global, etc.

---

## Guías útiles

- `examples/add.wat` y `examples/add.wasm`: contrato WASM mínimo.
- `examples/test-wasm-contract.sh`: flujo end-to-end de prueba.
- `examples/deploy-wasm-example.sh`: despliegue rápido (detecta modo Substrate).
- `arquitecture.md`: descripción detallada del sistema.
- `deployment.md`: procedimientos extendidos.
- `system_capabilities.md`: estado actualizado de la integración Substrate (`substrate.auto-deploy`, flujo RPC).

---

## Próximos pasos sugeridos

1. Integrar nuevos contratos Solidity → WASM.
2. Añadir tests automáticos para despliegues WASM.
3. Exponer endpoints complementarios (logs filtrados, métricas).
4. Documentar front end / flujos UI.

---

## Agradecimientos

### Café Raft

Este proyecto está basado en **Café Raft**, una implementación en Java del algoritmo de consenso Raft desarrollada por **thaivc**.

- **Repositorio**: [Café Raft](https://github.com/gc-garcol/cafe-raft)
- **Autor**: thaivc
- **Licencia**: Apache-2.0

Café Raft proporciona la base sólida de consenso distribuido que permite a este proyecto gestionar comandos de forma consistente y tolerante a fallos.

### Substrate Contracts Node

La integración con contratos WebAssembly utiliza **substrate-contracts-node**, un nodo de Substrate configurado para ejecutar contratos inteligentes desarrollado por **Parity Technologies**.

- **Repositorio**: [substrate-contracts-node](https://github.com/paritytech/substrate-contracts-node)
- **Organización**: [Parity Technologies](https://www.parity.io/)
- **Licencia**: Apache 2.0 / GPL 3.0 (dual license)

Substrate-contracts-node permite ejecutar contratos WASM compilados con Solang que requieren las funciones host de Substrate (`seal_*`), proporcionando un entorno de ejecución completo para contratos de Polkadot/Substrate.

### Otras tecnologías utilizadas

- **Wasmtime-Java**: Bindings Java para Wasmtime (Bytecode Alliance)
- **Solang**: Compilador Solidity → WebAssembly (Hyperledger)
- **Spring Boot**: Framework de aplicación Java (VMware / Pivotal)

