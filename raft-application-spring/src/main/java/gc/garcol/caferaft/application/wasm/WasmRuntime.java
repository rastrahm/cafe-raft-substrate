package gc.garcol.caferaft.application.wasm;

/**
 * Abstracción de runtime WASM.
 * Implementaciones reales pueden usar Wasmtime/Wasmer/etc.
 */
public interface WasmRuntime {
    /**
     * Pre-compila el módulo y devuelve un manejador opaco reutilizable.
     */
    Object precompile(byte[] moduleBytes) throws Exception;
}


