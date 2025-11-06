package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gc.garcol.caferaft.application.contract.Contract;
import gc.garcol.caferaft.application.contract.ContractRegistry;
import lombok.RequiredArgsConstructor;
import gc.garcol.caferaft.application.contract.WasmContract;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractEngine {

    private final ContractRegistry contractRegistry;
    private final ObjectMapper     objectMapper;
    private final WasmEngine       wasmEngine;

    public void deploy(String name, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Contract contract)) {
                throw new IllegalArgumentException("La clase no implementa Contract: " + className);
            }
            contractRegistry.register(name, contract);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo instanciar contrato: " + className, e);
        }
    }

    public Object invoke(String name, String method, Map<String, Object> args) {
        Contract c = contractRegistry.get(name);
        if (c == null) {
            throw new IllegalArgumentException("Contrato no encontrado: " + name);
        }
        return c.invoke(method, args);
    }

    public void deployWasm(String name, String wasmBase64) {
        // Registrar el módulo en el WasmEngine y envolverlo como Contract
        // Nota: no ejecuta nada todavía (stub)
        // Guardamos un contrato que delega al motor WASM para futuras invocaciones
        wasmEngine.deploy(name, wasmBase64);
        contractRegistry.register(name, new WasmContract(name, wasmEngine));
    }
}


