package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ABI simplificado para comunicación Java ↔ WASM.
 * Stub funcional: se implementará cuando se integre el runtime WASM.
 */
@Component
@RequiredArgsConstructor
public class WasmAbi {

    private final ObjectMapper objectMapper;

    /**
     * ABI simplificado: pasar argumentos como valores primitivos.
     * Útil para funciones simples.
     * Stub: se implementará cuando se integre el runtime WASM.
     */
    public Object[] argsToObjects(Map<String, Object> args) {
        // Simplificado: convierte valores numéricos encontrados
        // En producción, usar un esquema de tipos más robusto
        Object[] result = new Object[args.size()];
        int i = 0;
        for (Object v : args.values()) {
            if (v instanceof Number) {
                result[i++] = v;
            } else {
                result[i++] = String.valueOf(v);
            }
        }
        return result;
    }
}
