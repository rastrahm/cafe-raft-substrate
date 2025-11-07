package gc.garcol.caferaft.application.wasm;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Implementación real de WasmRuntime usando Wasmtime mediante reflexión.
 * - No requiere dependencia de compilación; cargará clases si están en classpath.
 * - Retorna el objeto Module de Wasmtime como handler opaco.
 */
@Primary
@Component
public class WasmtimeWasmRuntime implements WasmRuntime {
    private final Object engine;
    
    public WasmtimeWasmRuntime() throws Exception {
        // Crear un Engine compartido al inicializar el bean
        Class<?> engineClass = Class.forName("io.github.kawamuray.wasmtime.Engine");
        this.engine = engineClass.getConstructor().newInstance();
    }
    
    /**
     * Obtiene el Engine compartido usado para precompilar módulos.
     */
    public Object getEngine() {
        return engine;
    }
    
    @Override
    public Object precompile(byte[] moduleBytes) throws Exception {
        // Cargar clases de Wasmtime via reflexión
        Class<?> moduleClass = Class.forName("io.github.kawamuray.wasmtime.Module");

        // Module.fromBinary(Engine, byte[])
        Method fromBinary = moduleClass.getMethod("fromBinary", engine.getClass(), byte[].class);
        Object module = fromBinary.invoke(null, engine, moduleBytes);

        // Devolver el Module precompilado como handler opaco
        return module;
    }
}


