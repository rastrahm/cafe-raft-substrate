package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gc.garcol.caferaft.application.wasm.WasmRuntime;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WasmEngine {

    private final ObjectMapper objectMapper;
    private final WasmAbi wasmAbi;
    private final Map<String, byte[]> nameToModule = new ConcurrentHashMap<>();
    private final Map<String, Object> nameToCompiled = new ConcurrentHashMap<>();
    private final Map<String, Object> nameToEngine = new ConcurrentHashMap<>();
    private final WasmRuntime wasmRuntime;
    @Value("${wasm.storage.dir:wasm-storage}")
    private String storageDir;
    private Path storagePath;

    @PostConstruct
    public void init() {
        storagePath = Paths.get(storageDir).toAbsolutePath();
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            log.warn("No se pudo crear el directorio de almacenamiento WASM {}: {}", storagePath, e.getMessage());
        }
        restorePersistedModules();
    }

    /**
     * Registra un módulo WASM sin intentar precompilarlo. Útil para despliegues stub.
     */
    public void registerModule(String name, String wasmBase64) {
        byte[] module = Base64.getDecoder().decode(wasmBase64);
        validateModule(module);
        nameToModule.put(name, module);
        nameToCompiled.remove(name);
        nameToEngine.remove(name);
        log.info("WASM registrado (sin precompilación): {} ({} bytes)", name, module.length);
    }

    public void deploy(String name, String wasmBase64) {
        byte[] module = Base64.getDecoder().decode(wasmBase64);
        registerModuleInternal(name, module, true);
    }

    public Object invoke(String name, String method, Map<String, Object> args) {
        if (!nameToModule.containsKey(name)) {
            throw new IllegalArgumentException("WASM no encontrado: " + name);
        }
        Object compiled = nameToCompiled.get(name);
        if (compiled == null) {
            throw new UnsupportedOperationException("Módulo no precompilado. Runtime WASM no disponible.");
        }
        try {
            // Cargar clases de Wasmtime via reflexión
            Class<?> engineClass = Class.forName("io.github.kawamuray.wasmtime.Engine");
            Class<?> storeClass = Class.forName("io.github.kawamuray.wasmtime.Store");
            Class<?> instanceClass = Class.forName("io.github.kawamuray.wasmtime.Instance");
            Class<?> funcClass = Class.forName("io.github.kawamuray.wasmtime.Func");
            Class<?> valClass = Class.forName("io.github.kawamuray.wasmtime.Val");
            Class<?> externClass = Class.forName("io.github.kawamuray.wasmtime.Extern");
            Class<?> collectionClass = Class.forName("java.util.Collection");
            Class<?> arrayListClass = Class.forName("java.util.ArrayList");
            
            // Obtener el Engine almacenado (debe ser el mismo que se usó para precompilar el Module)
            Object engine = nameToEngine.get(name);
            if (engine == null) {
                // Si no hay Engine almacenado, intentar obtenerlo del WasmRuntime
                try {
                    Method getEngine = wasmRuntime.getClass().getMethod("getEngine");
                    engine = getEngine.invoke(wasmRuntime);
                    log.warn("No se encontró Engine almacenado para {}, usando Engine del WasmRuntime", name);
                } catch (NoSuchMethodException e) {
                    // Si no tiene getEngine, crear uno nuevo (esto causará errores)
                    log.error("No se puede obtener el Engine para {} - esto causará errores", name);
                    engine = engineClass.getConstructor().newInstance();
                }
            } else {
                log.debug("Usando Engine almacenado para {}: {}", name, engine);
            }
            
            // Según la documentación: Store<Void> store = new Store<>(engine);
            // El constructor es Store(T, Engine) donde T es Void
            // El problema es que Store es genérico y necesitamos usar el tipo correcto con reflexión
            // Intentar usar el constructor Store(T, Engine) con null como T y el Engine
            // Usar getDeclaredConstructors() para encontrar el constructor correcto
            Object store = null;
            java.lang.reflect.Constructor<?>[] constructors = storeClass.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (paramTypes.length == 2 && paramTypes[1] == engineClass) {
                    // Este es el constructor Store(T, Engine)
                    try {
                        constructor.setAccessible(true);
                        store = constructor.newInstance(null, engine);
                        log.debug("Store creado usando constructor: {}", constructor);
                        break;
                    } catch (Exception e) {
                        log.debug("No se pudo usar constructor {}: {}", constructor, e.getMessage());
                    }
                }
            }
            
            if (store == null) {
                // Si no se pudo crear con el constructor, intentar con Store.withoutData()
                // pero esto creará un Store con un Engine diferente, lo cual causará errores
                log.error("No se puede crear Store con Engine usando reflexión");
                throw new RuntimeException("No se puede crear Store con Engine usando reflexión");
            }
            
            // Verificar que el Engine del Store es el mismo que se usó para precompilar
            Method storeEngine = storeClass.getMethod("engine");
            Object storeEngineObj = storeEngine.invoke(store);
            if (storeEngineObj != engine) {
                log.warn("El Engine del Store no coincide con el Engine usado para precompilar. Esto puede causar errores.");
            }
            
            // new ArrayList<Extern>() para imports vacíos
            Object emptyExterns = arrayListClass.getConstructor().newInstance();
            
            // new Instance(Store, Module, Collection<Extern>)
            Object instance = instanceClass.getConstructor(storeClass, compiled.getClass(), collectionClass)
                    .newInstance(store, compiled, emptyExterns);
            
            // instance.getFunc(Store, String) -> Optional<Func>
            Class<?> optionalClass = Class.forName("java.util.Optional");
            Method getFunc = instanceClass.getMethod("getFunc", storeClass, String.class);
            Object funcOptional = getFunc.invoke(instance, store, method);
            Method isPresent = optionalClass.getMethod("isPresent");
            if (!(Boolean) isPresent.invoke(funcOptional)) {
                throw new IllegalArgumentException("Función no encontrada: " + method);
            }
            Method get = optionalClass.getMethod("get");
            Object func = get.invoke(funcOptional);
            
            // Mapear argumentos a Val[]
            Object[] wasmArgs = mapArgsToVals(args, valClass);
            
            // func.call(Store, Val[])
            Method call = funcClass.getMethod("call", storeClass, valClass.arrayType());
            Object[] results = (Object[]) call.invoke(func, store, wasmArgs);
            
            // Extraer resultado
            if (results != null && results.length > 0) {
                Object result = results[0];
                Method getType = valClass.getMethod("getType");
                Object typeEnum = getType.invoke(result);
                Method nameMethod = typeEnum.getClass().getMethod("name");
                String typeName = (String) nameMethod.invoke(typeEnum);
                
                return switch (typeName) {
                    case "I32" -> valClass.getMethod("i32").invoke(result);
                    case "I64" -> valClass.getMethod("i64").invoke(result);
                    case "F32" -> valClass.getMethod("f32").invoke(result);
                    case "F64" -> valClass.getMethod("f64").invoke(result);
                    default -> null;
                };
            }
            return null;
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("Wasmtime no disponible en classpath: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error ejecutando WASM: {}::{}", name, method, e);
            throw new RuntimeException("Error ejecutando contrato WASM: " + e.getMessage(), e);
        }
    }
    
    private Object[] mapArgsToVals(Map<String, Object> args, Class<?> valClass) throws Exception {
        if (args == null || args.isEmpty()) {
            return (Object[]) java.lang.reflect.Array.newInstance(valClass, 0);
        }
        Object[] res = (Object[]) java.lang.reflect.Array.newInstance(valClass, args.size());
        int i = 0;
        Method fromI32 = valClass.getMethod("fromI32", int.class);
        Method fromI64 = valClass.getMethod("fromI64", long.class);
        Method fromF32 = valClass.getMethod("fromF32", float.class);
        Method fromF64 = valClass.getMethod("fromF64", double.class);
        
        for (Object v : args.values()) {
            if (v instanceof Integer n) {
                res[i++] = fromI32.invoke(null, n);
            } else if (v instanceof Long n) {
                res[i++] = fromI64.invoke(null, n);
            } else if (v instanceof Float n) {
                res[i++] = fromF32.invoke(null, n);
            } else if (v instanceof Double n) {
                res[i++] = fromF64.invoke(null, n);
            } else if (v instanceof Number n) {
                long lv = n.longValue();
                if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                    res[i++] = fromI32.invoke(null, (int) lv);
                } else {
                    res[i++] = fromI64.invoke(null, lv);
                }
            } else {
                res[i++] = fromI32.invoke(null, 0);
            }
        }
        return res;
    }

    private void registerModuleInternal(String name, byte[] module, boolean persist) {
        validateModule(module);
        nameToModule.put(name, module);
        try {
            Object compiled = wasmRuntime.precompile(module);
            if (compiled != null) {
                nameToCompiled.put(name, compiled);
                try {
                    Method getEngine = wasmRuntime.getClass().getMethod("getEngine");
                    Object engine = getEngine.invoke(wasmRuntime);
                    nameToEngine.put(name, engine);
                } catch (NoSuchMethodException e) {
                    log.warn("WasmRuntime no tiene getEngine(), no se puede almacenar el Engine para {}", name);
                }
                if (persist) {
                    persistModule(name, module);
                }
                log.info("WASM precompilado: {} ({} bytes)", name, module.length);
            } else {
                log.info("WASM desplegado (sin precompilación - runtime stub): {} ({} bytes)", name, module.length);
            }
        } catch (Exception e) {
            log.warn("Fallo precompilando WASM (continuando en stub): {} - {}", name, e.getMessage(), e);
        }
    }

    private void validateModule(byte[] module) {
        if (module.length < 4 || module[0] != 0x00 || module[1] != 0x61 || module[2] != 0x73 || module[3] != 0x6d) {
            throw new IllegalArgumentException("No es un módulo WASM válido");
        }
    }

    private void persistModule(String name, byte[] module) {
        if (storagePath == null) {
            return;
        }
        try {
            Path target = storagePath.resolve(sanitizeName(name) + ".wasm");
            Files.write(target, module, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("No se pudo persistir el módulo WASM {}: {}", name, e.getMessage());
        }
    }

    private void restorePersistedModules() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try (Stream<Path> stream = Files.list(storagePath)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".wasm"))
                    .forEach(this::restoreModuleFromFile);
        } catch (IOException e) {
            log.warn("No se pudieron listar módulos WASM persistidos: {}", e.getMessage());
        }
    }

    private void restoreModuleFromFile(Path path) {
        String fileName = path.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 5);
        try {
            byte[] module = Files.readAllBytes(path);
            registerModuleInternal(name, module, false);
            log.info("Módulo WASM restaurado desde disco: {}", name);
        } catch (Exception e) {
            log.warn("No se pudo restaurar el módulo WASM {}: {}", fileName, e.getMessage());
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
