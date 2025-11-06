# Guía: Compilar Contratos Solidity a WASM con Solang

## Requisitos previos

1. **Instalar Solang** (compilador Solidity→WASM):
   ```bash
   # Opción A: Binario precompilado
   wget https://github.com/hyperledger/solang/releases/latest/download/solang-linux-x86_64
   chmod +x solang-linux-x86_64
   sudo mv solang-linux-x86_64 /usr/local/bin/solang
   
   # Opción B: Desde cargo (Rust)
   cargo install --locked --git https://github.com/hyperledger/solang solang-cli
   ```

2. **Verificar instalación**:
   ```bash
   solang --version
   ```

## Ejemplo: Contrato Simple

### 1. Crear contrato Solidity

Crea un archivo `SimpleContract.sol`:

```solidity
// SPDX-License-Identifier: MIT
pragma solidity >=0.8.0;

contract SimpleContract {
    uint256 public value;
    
    function setValue(uint256 _value) public {
        value = _value;
    }
    
    function getValue() public view returns (uint256) {
        return value;
    }
    
    function add(uint256 a, uint256 b) public pure returns (uint256) {
        return a + b;
    }
}
```

### 2. Compilar a WASM

```bash
solang compile --target substrate SimpleContract.sol
```

Esto genera:
- `SimpleContract.contract` (metadatos)
- `SimpleContract.wasm` (módulo WASM)

### 3. Convertir WASM a Base64

```bash
# Opción A: base64
base64 -w 0 SimpleContract.wasm > SimpleContract.wasm.base64

# Opción B: Python
python3 -c "import base64; print(base64.b64encode(open('SimpleContract.wasm', 'rb').read()).decode())" > SimpleContract.wasm.base64
```

### 4. Desplegar en Cafe Raft

```bash
curl -X POST http://localhost:8080/contracts/wasm/deploy \
  -H 'Content-Type: application/json' \
  -d "{
    \"name\": \"simple\",
    \"wasmBase64\": \"$(cat SimpleContract.wasm.base64)\"
  }"
```

### 5. Invocar funciones

```bash
# Invocar add(a, b)
curl -X POST http://localhost:8080/contracts/invoke \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "simple",
    "method": "add",
    "args": {"a": 10, "b": 25}
  }'
```

## Notas importantes

- **ABI**: El ABI actual espera funciones que acepten valores primitivos (i32/i64/f32/f64) o memoria compartida.
- **Métodos públicos**: Solo los métodos `public` o `external` son accesibles desde Java.
- **Tipos**: Solidity `uint256` se mapea a `i64` en WASM. Ajusta el ABI según necesites.
- **Memoria compartida**: Para argumentos complejos, el contrato debe exponer una función `memory` exportada.

## Ejemplo avanzado: Contrato con estado

```solidity
pragma solidity >=0.8.0;

contract Counter {
    uint256 private count;
    
    function increment() public {
        count++;
    }
    
    function getCount() public view returns (uint256) {
        return count;
    }
}
```

**Nota**: El estado del contrato se mantiene en la máquina de estado de Raft, no en WASM. Cada invocación es determinista y el estado se persiste en el log de Raft.

## Troubleshooting

- **Error "Función no encontrada"**: Verifica que el método sea `public` o `external`.
- **Error "No es un módulo WASM válido"**: Verifica que el Base64 sea correcto y que el archivo `.wasm` sea válido.
- **Error de tipos**: Ajusta el ABI en `WasmAbi.java` para mapear tipos Solidity ↔ WASM correctamente.

## Referencias

- [Solang Documentation](https://solang.readthedocs.io/)
- [Wasmtime-Java](https://github.com/kawamuray/wasmtime-java)
- [WebAssembly Specification](https://webassembly.org/)

