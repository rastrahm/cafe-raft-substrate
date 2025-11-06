package gc.garcol.caferaft.application.contract;

import gc.garcol.caferaft.application.service.WasmEngine;

import java.util.Map;

public class WasmContract implements Contract {

    private final String     name;
    private final WasmEngine wasmEngine;

    public WasmContract(String name, WasmEngine wasmEngine) {
        this.name = name;
        this.wasmEngine = wasmEngine;
    }

    @Override
    public Object invoke(String method, Map<String, Object> args) {
        return wasmEngine.invoke(name, method, args);
    }
}


