package gc.garcol.caferaft.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import gc.garcol.caferaft.application.substrate.SubstrateContractsClient;
import gc.garcol.caferaft.application.wasm.WasmRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WasmEngineSubstrateTest {

    private WasmEngine wasmEngine;

    @Mock
    private WasmRuntime wasmRuntime;
    @Mock
    private SubstrateContractsClient substrateContractsClient;

    @BeforeEach
    void setUp() {
        WasmAbi wasmAbi = new WasmAbi(new ObjectMapper());
        wasmEngine = new WasmEngine(new ObjectMapper(), wasmAbi, wasmRuntime, substrateContractsClient);
        ReflectionTestUtils.setField(wasmEngine, "substrateEnabled", true);
        ReflectionTestUtils.setField(wasmEngine, "defaultOrigin", "//Alice");
        ReflectionTestUtils.setField(wasmEngine, "substrateAutoDeploy", true);
    }

    @Test
    @DisplayName("deploy en modo Substrate auto-instancia y registra dirección")
    void deploySubstrateAutoRegistersAddress() throws Exception {
        JsonNode response = new ObjectMapper().readTree("{\"contract\":\"0xC0DE\"}");
        when(substrateContractsClient.instantiateWithCode(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(response);

        wasmEngine.deploy("adder", base64(0x00, 0x61, 0x73, 0x6d, 0x01));

        assertThat(ReflectionTestUtils.getField(wasmEngine, "nameToModule"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("adder");
        assertThat(ReflectionTestUtils.getField(wasmEngine, "nameToCompiled"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("adder");

        @SuppressWarnings("unchecked")
        Map<String, String> addrMap = (Map<String, String>) ReflectionTestUtils
                .getField(wasmEngine, "nameToSubstrateAddress");
        assertThat(addrMap).containsEntry("adder", "0xC0DE");

        verify(substrateContractsClient).instantiateWithCode(eq("//Alice"), any(byte[].class),
                isNull(), isNull(), eq("0x"), eq("0x"));
    }

    @Test
    @DisplayName("invoke en modo Substrate delega en SubstrateContractsClient")
    void invokeSubstrateDelegatesToClient() {
        ReflectionTestUtils.invokeMethod(wasmEngine, "registerModule", "adder", base64(0x00, 0x61, 0x73, 0x6d, 0x01));
        wasmEngine.registerSubstrateAddress("adder", "0xABC");
        JsonNode expected = TextNode.valueOf("result");
        when(substrateContractsClient.dryRunCall("//Alice", "0xABC", null, null, "0x0102")).thenReturn(expected);

        Object result = wasmEngine.invoke("adder", "add", Map.of("__inputHex", "0x0102"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("invoke requiere __inputHex")
    void invokeRequiresInputHex() {
        ReflectionTestUtils.invokeMethod(wasmEngine, "registerModule", "adder", base64(0x00, 0x61, 0x73, 0x6d, 0x01));
        wasmEngine.registerSubstrateAddress("adder", "0xABC");

        assertThatThrownBy(() -> wasmEngine.invoke("adder", "add", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("__inputHex");
    }

    @Test
    @DisplayName("invoke usa __value y __gasLimit si están presentes")
    void invokeUsesValueAndGas() {
        ReflectionTestUtils.invokeMethod(wasmEngine, "registerModule", "adder", base64(0x00, 0x61, 0x73, 0x6d, 0x01));
        wasmEngine.registerSubstrateAddress("adder", "0xABC");
        JsonNode expected = TextNode.valueOf("ok");
        when(substrateContractsClient.dryRunCall("//Bob", "0xABC",
                BigInteger.TEN, BigInteger.ONE, "0x0102")).thenReturn(expected);

        Object result = wasmEngine.invoke("adder", "add", Map.of(
                "__origin", "//Bob",
                "__value", BigInteger.TEN,
                "__gasLimit", "1",
                "__inputHex", "0x0102"
        ));

        assertThat(result).isEqualTo(expected);
    }

    private static String base64(int... bytes) {
        byte[] arr = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            arr[i] = (byte) bytes[i];
        }
        return java.util.Base64.getEncoder().encodeToString(arr);
    }
}

