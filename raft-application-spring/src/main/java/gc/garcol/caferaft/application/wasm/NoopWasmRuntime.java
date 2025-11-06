package gc.garcol.caferaft.application.wasm;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Implementación por defecto que no hace nada.
 * Permite compilar el proyecto sin dependencias nativas; cuando se
 * integre un runtime real, se reemplaza este bean.
 */
@Primary
@Component
public class NoopWasmRuntime implements WasmRuntime {
    @Override
    public Object precompile(byte[] moduleBytes) {
        return null; // sin precompilación en modo stub
    }
}


