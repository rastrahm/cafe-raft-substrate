package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gc.garcol.caferaft.application.contract.Contract;
import gc.garcol.caferaft.application.contract.ContractRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractEngineTest {

    @Mock
    private WasmEngine wasmEngine;

    private ContractRegistry contractRegistry;

    private ContractEngine contractEngine;

    @BeforeEach
    void setUp() {
        contractRegistry = new ContractRegistry();
        contractEngine = new ContractEngine(contractRegistry, new ObjectMapper(), wasmEngine);
    }

    @Test
    @DisplayName("deploy registra una instancia de contrato JVM")
    void deployRegistersJvmContract() {
        contractEngine.deploy("counter", SampleContract.class.getName());

        Contract registered = contractRegistry.get("counter");
        assertThat(registered).isInstanceOf(SampleContract.class);
        assertThat(registered.invoke("value", Map.of())).isEqualTo(0);
    }

    @Test
    @DisplayName("deploy lanza excepción si la clase no implementa Contract")
    void deployRejectsNonContractClass() {
        assertThatThrownBy(() -> contractEngine.deploy("invalid", String.class.getName()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se pudo instanciar contrato");
    }

    @Test
    @DisplayName("invoke delega en el contrato registrado")
    void invokeCallsRegisteredContract() {
        contractEngine.deploy("counter", SampleContract.class.getName());

        Object result = contractEngine.invoke("counter", "increment", Map.of("amount", 7));

        assertThat(result).isEqualTo(7);
    }

    @Test
    @DisplayName("invoke lanza excepción si el contrato no existe")
    void invokeFailsWhenContractMissing() {
        assertThatThrownBy(() -> contractEngine.invoke("missing", "method", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Contrato no encontrado");
    }

    @Test
    @DisplayName("deployWasm invoca WasmEngine y registra WasmContract")
    void deployWasmRegistersWasmContract() {
        String wasmBase64 = "dGVzdA==";

        contractEngine.deployWasm("adder", wasmBase64);

        verify(wasmEngine).deploy("adder", wasmBase64);
        assertThat(contractRegistry.get("adder"))
                .isNotNull()
                .isInstanceOf(gc.garcol.caferaft.application.contract.WasmContract.class);
    }

    @Test
    @DisplayName("registerWasmSubstrateAddress delega en WasmEngine")
    void registerWasmSubstrateAddressDelegates() {
        contractEngine.registerWasmSubstrateAddress("adder", "0x1234");

        verify(wasmEngine).registerSubstrateAddress("adder", "0x1234");
    }

    public static class SampleContract implements Contract {
        private int value = 0;

        @Override
        public Object invoke(String method, Map<String, Object> args) {
            if ("increment".equals(method)) {
                int amount = ((Number) args.getOrDefault("amount", 1)).intValue();
                value += amount;
                return value;
            }
            if ("value".equals(method)) {
                return value;
            }
            throw new IllegalArgumentException("Método desconocido: " + method);
        }
    }
}

