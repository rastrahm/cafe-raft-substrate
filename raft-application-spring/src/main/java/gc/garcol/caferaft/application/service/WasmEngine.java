package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import gc.garcol.caferaft.application.wasm.WasmRuntime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WasmEngine {

    private final ObjectMapper objectMapper;
    private final WasmAbi wasmAbi;
    private final Map<String, byte[]> nameToModule = new ConcurrentHashMap<>();
    private final Map<String, Object> nameToCompiled = new ConcurrentHashMap<>();
    private final WasmRuntime wasmRuntime;

    public void deploy(String name, String wasmBase64) {
        byte[] module = Base64.getDecoder().decode(wasmBase64);
        // Validar módulo WASM (mágico: 0x00 0x61 0x73 0x6d)
        if (module.length < 4 || module[0] != 0x00 || module[1] != 0x61 || module[2] != 0x73 || module[3] != 0x6d) {
            throw new IllegalArgumentException("No es un módulo WASM válido");
        }
        nameToModule.put(name, module);
        try {
            Object compiled = wasmRuntime.precompile(module);
            if (compiled != null) {
                nameToCompiled.put(name, compiled);
                log.info("WASM precompilado: {}", name);
            } else {
                log.info("WASM desplegado (sin precompilación - runtime stub): {} ({} bytes)", name, module.length);
            }
        } catch (Exception e) {
            log.warn("Fallo precompilando WASM (continuando en stub): {} - {}", name, e.getMessage());
        }
    }

    public Object invoke(String name, String method, Map<String, Object> args) {
        if (!nameToModule.containsKey(name)) {
            throw new IllegalArgumentException("WASM no encontrado: " + name);
        }
        // Implementa WasmRuntime concreto: aquí debería ejecutarse el módulo precompilado
        throw new UnsupportedOperationException("Implementa WasmRuntime concreto para ejecución WASM");
    }
}
