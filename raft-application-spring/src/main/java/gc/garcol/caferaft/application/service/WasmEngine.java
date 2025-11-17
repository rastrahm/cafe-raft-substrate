package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gc.garcol.caferaft.application.wasm.WasmRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import gc.garcol.caferaft.application.substrate.SubstrateContractsClient;
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
import java.util.Set;
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
    private final Map<String, String> nameToSubstrateAddress = new ConcurrentHashMap<>();
    private final Map<String, ContractState> contractStates = new ConcurrentHashMap<>();
    private final Set<String> failedModules = ConcurrentHashMap.newKeySet();
    private final WasmRuntime wasmRuntime;
    private final SubstrateContractsClient substrateContractsClient;
    private final gc.garcol.caferaft.application.substrate.SubstrateCapabilities substrateCapabilities;
    @Value("${wasm.storage.dir:wasm-storage}")
    private String storageDir;
    @Value("${substrate.enabled:false}")
    private boolean substrateEnabled;
    @Value("${substrate.default-origin://Alice}")
    private String defaultOrigin;
    @Value("${substrate.auto-deploy:true}")
    private boolean substrateAutoDeploy;
    @Value("${substrate.auto-sync-after-invoke:false}")
    private boolean autoSyncAfterInvoke;
    @Value("${substrate.sync-method:get}")
    private String defaultSyncMethod;
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
        if (substrateEnabled) {
            registerModuleInternal(name, module, true);
            if (substrateAutoDeploy) {
                deployViaSubstrate(name, module);
            } else {
                log.info("Modo Substrate manual: registra la dirección con /contracts/wasm/register-address para {}",
                        name);
            }
            return;
        }
        registerModuleInternal(name, module, true);
    }

    public Object invoke(String name, String method, Map<String, Object> args) {
        if (!nameToModule.containsKey(name)) {
            throw new IllegalArgumentException("WASM no encontrado: " + name);
        }
        if (substrateEnabled) {
            return invokeViaSubstrate(name, method, args);
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

    private Object invokeViaSubstrate(String name, String method, Map<String, Object> args) {
        String address = nameToSubstrateAddress.get(name);
        if (address == null) {
            throw new IllegalStateException("No se ha registrado la dirección del contrato en Substrate para " + name);
        }
        String origin = args != null && args.containsKey("__origin") ? String.valueOf(args.get("__origin")) : defaultOrigin;
        String inputHex = args != null && args.containsKey("__inputHex")
                ? String.valueOf(args.get("__inputHex"))
                : "";
        if (inputHex.isBlank()) {
            throw new IllegalArgumentException("Se requiere __inputHex con la llamada SCALE-encoded al contrato.");
        }
        java.math.BigInteger value = extractBigInteger(args, "__value");
        java.math.BigInteger gas = extractBigInteger(args, "__gasLimit");
        java.math.BigInteger storageDeposit = extractBigInteger(args, "__storageDepositLimit");
        
        Object result;
        
        // Estrategia: usar RPC directo si está disponible y origin es SS58/SUri
        // Si origin es AccountId hex o no hay RPC directo, usar state_call
        if (substrateCapabilities.hasContractsCall() && !isAccountIdHex(origin)) {
            // RPC directo acepta SS58/SUri, más simple
            try {
                log.debug("Usando contracts_call RPC directo para invocar {}::{}", name, method);
                result = substrateContractsClient.dryRunCall(origin, address, value, gas, inputHex);
                log.info("Resultado contracts_call RPC ({}::{}) => {}", name, method, result);
            } catch (Exception e) {
                log.warn("Fallo usando contracts_call RPC, intentando state_call: {}", e.getMessage());
                // Continuar con state_call como fallback
                try {
                    String originAccountId = convertToAccountIdHex(origin);
                    String destAccountId = convertToAccountIdHex(address);
                    log.debug("Usando state_call (ContractsApi_call) para invocar {}::{}", name, method);
                    result = substrateContractsClient.callViaStateCall(
                            originAccountId, destAccountId, value, gas, storageDeposit, inputHex);
                    log.info("Resultado state_call Substrate ({}::{}) => {}", name, method, result);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                        "No se puede invocar el contrato: origin debe ser AccountId hex (32 bytes) " +
                        "o el nodo debe tener contracts_call RPC disponible. Origin recibido: " + origin, ex);
                }
            }
        } else {
            // Usar state_call (requiere AccountId hex)
            try {
                String originAccountId = convertToAccountIdHex(origin);
                String destAccountId = convertToAccountIdHex(address);
                log.debug("Usando state_call (ContractsApi_call) para invocar {}::{}", name, method);
                result = substrateContractsClient.callViaStateCall(
                        originAccountId, destAccountId, value, gas, storageDeposit, inputHex);
                log.info("Resultado state_call Substrate ({}::{}) => {}", name, method, result);
            } catch (IllegalArgumentException e) {
                // Si no se puede convertir a AccountId hex y no hay RPC directo, error
                throw new IllegalArgumentException(
                    "No se puede invocar el contrato: origin debe ser AccountId hex (32 bytes) " +
                    "o el nodo debe tener contracts_call RPC disponible. Origin recibido: " + origin, e);
            }
        }
        
        // Sincronización automática después de la invocación (si está habilitada)
        if (autoSyncAfterInvoke) {
            syncStateAfterInvoke(name, method);
        }
        
        return result;
    }
    
    /**
     * Sincroniza el estado automáticamente después de una invocación.
     * Este método maneja errores silenciosamente para no afectar la respuesta de la invocación.
     */
    private void syncStateAfterInvoke(String contractName, String invokedMethod) {
        try {
            // Determinar el método de sincronización
            // Si el método invocado es de lectura (ej: "get"), usarlo; si no, usar el método por defecto
            String syncMethod = isReadMethod(invokedMethod) ? invokedMethod : defaultSyncMethod;
            
            log.debug("Sincronizando automáticamente estado de {}::{} después de invocación", contractName, syncMethod);
            
            // Usar input vacío por defecto (el método de lectura puede no requerir parámetros)
            // Si se necesita un input específico, se puede extender en el futuro
            syncContractState(contractName, syncMethod, "0x");
            
            log.debug("Estado sincronizado automáticamente para {}", contractName);
        } catch (Exception e) {
            // No lanzar excepción para no afectar la respuesta de la invocación
            // Solo loguear el error
            log.warn("Error en sincronización automática de estado para {}: {}", contractName, e.getMessage());
        }
    }
    
    /**
     * Determina si un método es de lectura (no modifica estado).
     * Por defecto, métodos comunes de lectura: get, read, query, view
     */
    private boolean isReadMethod(String method) {
        if (method == null || method.isBlank()) {
            return false;
        }
        String lowerMethod = method.toLowerCase();
        return lowerMethod.startsWith("get") || 
               lowerMethod.startsWith("read") || 
               lowerMethod.startsWith("query") || 
               lowerMethod.startsWith("view") ||
               lowerMethod.equals("get");
    }
    
    private boolean isAccountIdHex(String account) {
        if (account == null || account.isBlank()) {
            return false;
        }
        String clean = account.startsWith("0x") ? account.substring(2) : account;
        if (clean.length() == 64) {
            try {
                java.util.HexFormat.of().parseHex(clean);
                return true;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return false;
    }
    
    private String convertToAccountIdHex(String account) {
        // Si ya es hex de 64 caracteres (32 bytes), devolverlo
        String clean = account.startsWith("0x") ? account.substring(2) : account;
        if (clean.length() == 64) {
            try {
                java.util.HexFormat.of().parseHex(clean);
                return "0x" + clean.toLowerCase();
            } catch (IllegalArgumentException ignored) {
                // Continuar
            }
        }
        // Si es SS58 o SURI, necesitamos convertirlo
        // Por ahora, lanzamos error para que use el método antiguo
        throw new IllegalArgumentException("Se requiere AccountId en formato hex (32 bytes) para state_call");
    }

    private java.math.BigInteger extractBigInteger(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        Object val = args.get(key);
        if (val instanceof Number n) {
            return java.math.BigInteger.valueOf(n.longValue());
        }
        if (val instanceof String s && !s.isBlank()) {
            if (s.startsWith("0x")) {
                return new java.math.BigInteger(s.substring(2), 16);
            }
            return new java.math.BigInteger(s);
        }
        throw new IllegalArgumentException("No se pudo parsear " + key + " como BigInteger: " + val);
    }

    /**
     * Permite registrar manualmente la dirección del contrato desplegado en Substrate
     * (por ejemplo, tras ejecutar author_submitExtrinsic desde una herramienta externa).
     */
    public void registerSubstrateAddress(String name, String address) {
        nameToSubstrateAddress.put(name, address);
        log.info("Asociado contrato {} a dirección Substrate {}", name, address);
    }

    /**
     * Sincroniza el estado de un contrato desde Substrate a Café Raft.
     * Invoca un método de lectura en Substrate y almacena el resultado.
     */
    public void syncContractState(String contractName, String method, String inputHex) {
        String address = nameToSubstrateAddress.get(contractName);
        if (address == null) {
            throw new IllegalStateException("No se ha registrado la dirección del contrato en Substrate para " + contractName);
        }
        
        try {
            // Convertir origin a AccountId hex
            String originAccountId = convertToAccountIdHex(defaultOrigin);
            String destAccountId = convertToAccountIdHex(address);
            
            // Preparar input (si está vacío, usar método para obtener estado)
            String queryInput = inputHex != null && !inputHex.isBlank() 
                ? inputHex 
                : "0x"; // Por defecto, sin parámetros
            
            log.debug("Sincronizando estado de {}::{} desde Substrate", contractName, method);
            
            // Invocar método de lectura en Substrate (state_call es read-only, perfecto para queries)
            // Usar un gas limit por defecto para queries (500M)
            java.math.BigInteger gasLimit = new java.math.BigInteger("500000000000");
            JsonNode result = substrateContractsClient.callViaStateCall(
                    originAccountId, destAccountId, 
                    java.math.BigInteger.ZERO, // Sin transferencia de valor
                    gasLimit, 
                    null, // Sin storage deposit limit
                    queryInput
            );
            
            // Extraer estado del resultado
            String stateHex = extractStateFromResult(result);
            
            // Obtener block number actual (opcional, para trazabilidad)
            long blockNumber = getCurrentBlockNumber();
            
            // Almacenar estado sincronizado
            ContractState state = new ContractState(
                    contractName, 
                    stateHex, 
                    System.currentTimeMillis(), 
                    blockNumber
            );
            contractStates.put(contractName, state);
            
            log.info("Estado sincronizado para {}: {} (block: {})", contractName, stateHex, blockNumber);
        } catch (Exception e) {
            log.error("Error sincronizando estado de {}: {}", contractName, e.getMessage(), e);
            throw new RuntimeException("No se pudo sincronizar el estado del contrato: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta el estado sincronizado de un contrato.
     */
    public ContractState getContractState(String contractName) {
        return contractStates.get(contractName);
    }

    /**
     * Extrae el estado del resultado de una llamada a Substrate.
     */
    private String extractStateFromResult(JsonNode result) {
        if (result == null || result.isNull()) {
            return "0x";
        }
        // El resultado de ContractsApi_call tiene estructura:
        // { "Ok": { "data": "0x..." } } o { "Err": ... }
        JsonNode okNode = result.path("Ok");
        if (!okNode.isMissingNode() && !okNode.isNull()) {
            JsonNode dataNode = okNode.path("data");
            if (!dataNode.isMissingNode() && dataNode.isTextual()) {
                return dataNode.asText();
            }
        }
        // Si no tiene estructura esperada, devolver el resultado completo como JSON
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("No se pudo serializar resultado: {}", result, e);
            return "0x";
        }
    }

    /**
     * Obtiene el número de bloque actual de Substrate.
     */
    private long getCurrentBlockNumber() {
        try {
            JsonNode blockHash = substrateContractsClient.chainGetBlockHash();
            if (blockHash != null && blockHash.isTextual()) {
                // Por ahora, retornamos timestamp como aproximación
                // TODO: Obtener block number real desde Substrate
                return System.currentTimeMillis();
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener block number: {}", e.getMessage());
        }
        return System.currentTimeMillis();
    }

    /**
     * Representa el estado sincronizado de un contrato.
     */
    public record ContractState(
            String contractName,
            String stateHex,           // Estado SCALE-encoded desde Substrate
            long lastSyncTimestamp,    // Timestamp de última sincronización
            long lastBlockNumber       // Block number de Substrate cuando se sincronizó
    ) {}

    private void registerModuleInternal(String name, byte[] module, boolean persist) {
        validateModule(module);
        nameToModule.put(name, module);
        if (substrateEnabled) {
            if (persist) {
                persistModule(name, module);
            }
            log.info("WASM almacenado en modo Substrate (sin precompilación): {} ({} bytes)", name, module.length);
            return;
        }
        if (failedModules.contains(name)) {
            log.debug("Omitiendo reintento de precompilación para {} (marcado como stub).", name);
            return;
        }
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
                failedModules.remove(name);
            } else {
                log.info("WASM desplegado (sin precompilación - runtime stub): {} ({} bytes)", name, module.length);
                failedModules.add(name);
            }
        } catch (Exception e) {
            failedModules.add(name);
            log.warn("Fallo precompilando WASM (continuando en stub): {} - {}", name, e.getMessage());
            log.debug("Detalle de error al precompilar {}:", name, e);
        }
    }

    private void deployViaSubstrate(String name, byte[] module) {
        // Intentar usar contracts_instantiateWithCode si está disponible (más simple)
        if (substrateCapabilities.hasContractsInstantiateWithCode()) {
            try {
                log.debug("Usando contracts_instantiateWithCode RPC para desplegar {}", name);
                JsonNode response = substrateContractsClient.instantiateWithCode(
                        defaultOrigin,
                        module,
                        null,
                        null,
                        "0x",
                        "0x");
                String address = extractContractAddress(response);
                if (address == null || address.isBlank()) {
                    log.warn("El nodo Substrate no devolvió dirección para {}. Usa register-address manualmente.", name);
                    return;
                }
                registerSubstrateAddress(name, address);
                persistModule(name, module);
                log.info("Contrato {} desplegado automáticamente en Substrate con dirección {} (vía RPC directo)", name, address);
                return;
            } catch (Exception e) {
                log.warn("Fallo usando contracts_instantiateWithCode, intentando state_call: {}", e.getMessage());
                // Continuar con state_call como fallback
            }
        }
        
        // Fallback: usar state_call + author_submitExtrinsic (más complejo, requiere implementación)
        log.warn("contracts_instantiateWithCode no disponible. Despliegue automático no soportado.");
        log.info("Por favor, despliega el contrato manualmente y registra la dirección con POST /contracts/wasm/register-address");
    }

    private String extractContractAddress(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode contract = response.path("contract");
        if (contract.isMissingNode() || contract.isNull() || contract.asText().isBlank()) {
            contract = response.path("result").path("contract");
        }
        if (contract.isMissingNode() || contract.isNull()) {
            return null;
        }
        return contract.asText();
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
