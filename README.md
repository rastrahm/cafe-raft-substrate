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
   - WebAssembly vía **Solidity → Solang → Wasmtime-Java**.

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
4. **Invocación** (curl):
   ```bash
   curl -X POST http://localhost:8080/contracts/invoke \
     -H "Content-Type: application/json" \
     -d '{"name":"contrato","method":"add","args":{"a":10,"b":25}}'
   ```

---

## Ejecución local

### Iniciar nodos
```bash
cd scripts
./cafe-raft.sh start-rest
```
El script lanza tres nodos (8080,8081,8082) y espera a que respondan.

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
| `/contracts/wasm/deploy` | POST | Desplegar módulo WASM |
| `/contracts/invoke` | POST | Invocar contrato (Java o WASM) |

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
- `examples/deploy-wasm-example.sh`: despliegue rápido.
- `arquitecture.md`: descripción detallada del sistema.
- `deployment.md`: procedimientos extendidos.

---

## Próximos pasos sugeridos

1. Integrar nuevos contratos Solidity → WASM.
2. Añadir tests automáticos para despliegues WASM.
3. Exponer endpoints complementarios (logs filtrados, métricas).
4. Documentar front end / flujos UI.

