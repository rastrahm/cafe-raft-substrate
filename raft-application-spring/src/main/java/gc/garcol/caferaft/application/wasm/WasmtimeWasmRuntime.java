package gc.garcol.caferaft.application.wasm;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Implementación real de WasmRuntime usando Wasmtime mediante reflexión.
 * - No requiere dependencia de compilación; cargará clases si están en classpath.
 * - Retorna el objeto Module de Wasmtime como handler opaco.
 */
@Component
public class WasmtimeWasmRuntime implements WasmRuntime {
    @Override
    public Object precompile(byte[] moduleBytes) throws Exception {
        // Cargar clases de Wasmtime via reflexión
        Class<?> engineClass = Class.forName("wasmtime.Engine");
        Class<?> moduleClass = Class.forName("wasmtime.Module");

        // new Engine()
        Object engine = engineClass.getConstructor().newInstance();

        // Module.fromBinary(Engine, byte[])
        Method fromBinary = moduleClass.getMethod("fromBinary", engineClass, byte[].class);
        Object module = fromBinary.invoke(null, engine, moduleBytes);

        // Devolver el Module precompilado como handler opaco
        return module;
    }
}


