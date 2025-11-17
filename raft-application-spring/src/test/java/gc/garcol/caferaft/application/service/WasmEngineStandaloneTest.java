package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import gc.garcol.caferaft.application.substrate.SubstrateContractsClient;
import gc.garcol.caferaft.application.wasm.WasmRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WasmEngineStandaloneTest {

    private static final byte[] VALID_WASM = new byte[]{0x00, 0x61, 0x73, 0x6d, 0x01};
    private static final String VALID_BASE64 = java.util.Base64.getEncoder().encodeToString(VALID_WASM);

    @Mock
    private WasmRuntime wasmRuntime;
    @Mock
    private SubstrateContractsClient substrateContractsClient;

    private WasmEngine wasmEngine;

    @BeforeEach
    void setUp() {
        WasmAbi wasmAbi = new WasmAbi(new ObjectMapper());
        wasmEngine = new WasmEngine(new ObjectMapper(), wasmAbi, wasmRuntime, substrateContractsClient);
        ReflectionTestUtils.setField(wasmEngine, "substrateEnabled", false);
    }

    @Test
    @DisplayName("deploy almacena y precompila módulo en modo standalone")
    void deployPrecompilesModule() throws Exception {
        Object compiledModule = new Object();
        when(wasmRuntime.precompile(VALID_WASM)).thenReturn(compiledModule);

        wasmEngine.deploy("adder", VALID_BASE64);

        assertThat(ReflectionTestUtils.getField(wasmEngine, "nameToModule"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("adder", VALID_WASM);
        assertThat(ReflectionTestUtils.getField(wasmEngine, "nameToCompiled"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("adder", compiledModule);
    }

    @Test
    @DisplayName("deploy marca módulo como stub si precompilación falla")
    void deployMarksModuleAsStubOnFailure() throws Exception {
        when(wasmRuntime.precompile(VALID_WASM)).thenThrow(new RuntimeException("fail"));

        wasmEngine.deploy("broken", VALID_BASE64);

        assertThat(ReflectionTestUtils.getField(wasmEngine, "failedModules"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION)
                .contains("broken");
    }

    @Test
    @DisplayName("invoke lanza excepción si el módulo no fue precompilado")
    void invokeFailsWhenModuleNotCompiled() {
        wasmEngine.registerModule("stub", VALID_BASE64);

        assertThatThrownBy(() -> wasmEngine.invoke("stub", "add", Map.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Módulo no precompilado");
    }
}

