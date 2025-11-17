package gc.garcol.caferaft.application.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.HashSet;

/**
 * Detecta automáticamente las capacidades del nodo Substrate al inicio.
 * Esto permite usar métodos RPC directos cuando están disponibles,
 * o fallback a state_call cuando no lo están.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Getter
public class SubstrateCapabilities {

    private final SubstrateRpcClient rpcClient;
    
    private boolean hasContractsRpc = false;
    private boolean hasContractsInstantiateWithCode = false;
    private boolean hasContractsCall = false;
    private boolean hasContractsUploadCode = false;
    private Set<String> availableMethods = new HashSet<>();

    @PostConstruct
    void detectCapabilities() {
        try {
            log.info("Detectando capacidades del nodo Substrate...");
            JsonNode response = rpcClient.call("rpc_methods");
            
            if (response != null && response.has("methods")) {
                JsonNode methods = response.get("methods");
                if (methods.isArray()) {
                    for (JsonNode method : methods) {
                        String methodName = method.asText();
                        availableMethods.add(methodName);
                        
                        if (methodName.startsWith("contracts_")) {
                            hasContractsRpc = true;
                            switch (methodName) {
                                case "contracts_instantiateWithCode" -> hasContractsInstantiateWithCode = true;
                                case "contracts_call" -> hasContractsCall = true;
                                case "contracts_uploadCode" -> hasContractsUploadCode = true;
                            }
                        }
                    }
                }
            }
            
            log.info("Capacidades Substrate detectadas:");
            log.info("  - Métodos RPC contracts_*: {}", hasContractsRpc);
            log.info("  - contracts_instantiateWithCode: {}", hasContractsInstantiateWithCode);
            log.info("  - contracts_call: {}", hasContractsCall);
            log.info("  - contracts_uploadCode: {}", hasContractsUploadCode);
            log.info("  - Total métodos disponibles: {}", availableMethods.size());
            
        } catch (Exception e) {
            log.warn("No se pudo detectar capacidades Substrate (usando fallback a state_call): {}", e.getMessage());
            log.debug("Detalle del error al detectar capacidades:", e);
            // Por defecto, asumimos que no hay métodos RPC directos
            hasContractsRpc = false;
        }
    }

    /**
     * Verifica si un método RPC específico está disponible.
     */
    public boolean hasMethod(String methodName) {
        return availableMethods.contains(methodName);
    }
    
    // Métodos de acceso explícitos (Lombok @Getter genera getHasXxx, no hasXxx)
    public boolean hasContractsInstantiateWithCode() {
        return hasContractsInstantiateWithCode;
    }
    
    public boolean hasContractsCall() {
        return hasContractsCall;
    }
    
    public boolean hasContractsUploadCode() {
        return hasContractsUploadCode;
    }
    
    public boolean hasContractsRpc() {
        return hasContractsRpc;
    }
}

