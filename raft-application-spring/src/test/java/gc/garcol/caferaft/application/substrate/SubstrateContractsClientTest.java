package gc.garcol.caferaft.application.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubstrateContractsClientTest {

    @Mock
    private SubstrateRpcClient rpcClient;

    private SubstrateContractsClient contractsClient;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        contractsClient = new SubstrateContractsClient(rpcClient, mapper);
        ReflectionTestUtils.setField(contractsClient, "defaultSuri", "//Alice");
        ReflectionTestUtils.setField(contractsClient, "defaultGasLimit", new BigInteger("123"));
        ReflectionTestUtils.setField(contractsClient, "defaultStorageDepositLimit", BigInteger.ZERO);
    }

    @Test
    @DisplayName("dryRunCall usa valores por defecto y normaliza entradas")
    void dryRunCallUsesDefaults() {
        JsonNode expected = mapper.createObjectNode().put("ok", true);
        when(rpcClient.call(eq("contracts_call"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(expected);

        JsonNode result = contractsClient.dryRunCall(null, "0xAbC", null, null, "0102");

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<List<?>> captor = ArgumentCaptor.forClass(List.class);
        verify(rpcClient).call(eq("contracts_call"), captor.capture());
        Object request = captor.getValue().get(0);

        String origin = (String) ReflectionTestUtils.invokeMethod(request, "origin");
        String dest = (String) ReflectionTestUtils.invokeMethod(request, "dest");
        BigInteger value = (BigInteger) ReflectionTestUtils.invokeMethod(request, "value");
        BigInteger gasLimit = (BigInteger) ReflectionTestUtils.invokeMethod(request, "gasLimit");
        BigInteger deposit = (BigInteger) ReflectionTestUtils.invokeMethod(request, "storageDepositLimit");
        String input = (String) ReflectionTestUtils.invokeMethod(request, "input");

        assertThat(origin).isEqualTo("//Alice");
        assertThat(dest).isEqualTo("0xAbC");
        assertThat(value).isEqualTo(BigInteger.ZERO);
        assertThat(gasLimit).isEqualTo(new BigInteger("123"));
        assertThat(deposit).isEqualTo(BigInteger.ZERO);
        assertThat(input).isEqualTo("0x0102");
    }

    @Test
    @DisplayName("dryRunInstantiate construye la petición con hex normalizado")
    void dryRunInstantiateBuildsRequest() {
        JsonNode expected = mapper.createObjectNode().put("hash", "0x01");
        when(rpcClient.call(eq("contracts_instantiateDryRun"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(expected);

        JsonNode result = contractsClient.dryRunInstantiate("//Bob", "abcd", BigInteger.ONE,
                new BigInteger("500"), "0x10", "20");

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<List<?>> captor = ArgumentCaptor.forClass(List.class);
        verify(rpcClient).call(eq("contracts_instantiateDryRun"), captor.capture());
        Object request = captor.getValue().get(0);

        String origin = (String) ReflectionTestUtils.invokeMethod(request, "origin");
        String codeHash = (String) ReflectionTestUtils.invokeMethod(request, "codeHash");
        BigInteger value = (BigInteger) ReflectionTestUtils.invokeMethod(request, "value");
        BigInteger gasLimit = (BigInteger) ReflectionTestUtils.invokeMethod(request, "gasLimit");
        String input = (String) ReflectionTestUtils.invokeMethod(request, "input");
        String salt = (String) ReflectionTestUtils.invokeMethod(request, "salt");

        assertThat(origin).isEqualTo("//Bob");
        assertThat(codeHash).isEqualTo("0xabcd");
        assertThat(value).isEqualTo(BigInteger.ONE);
        assertThat(gasLimit).isEqualTo(new BigInteger("500"));
        assertThat(input).isEqualTo("0x10");
        assertThat(salt).isEqualTo("0x20");
    }

    @Test
    @DisplayName("dryRunCall lanza excepción para cuentas no soportadas")
    void dryRunCallRejectsInvalidAccount() {
        assertThatThrownBy(() -> contractsClient.dryRunCall("??", "0xabc", null, null, "0x01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cuenta/dirección no soportada");
    }

    @Test
    @DisplayName("ensureHex lanza excepción cuando la entrada no es hex")
    void ensureHexRejectsInvalidData() {
        assertThatThrownBy(() -> contractsClient.dryRunCall("//Alice", "0xabc", null, null, "ZZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Datos esperados en hex");
    }

    @Test
    @DisplayName("instantiateWithCode envía el código en hex y aplica defaults")
    void instantiateWithCodeBuildsRequest() {
        byte[] wasm = new byte[]{0x00, 0x61};
        JsonNode expected = mapper.createObjectNode().put("contract", "0xC0DE");
        when(rpcClient.call(eq("contracts_instantiateWithCode"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(expected);

        JsonNode result = contractsClient.instantiateWithCode(null, wasm, null, null, null, null);

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<List<?>> captor = ArgumentCaptor.forClass(List.class);
        verify(rpcClient).call(eq("contracts_instantiateWithCode"), captor.capture());
        Object request = captor.getValue().get(0);

        String origin = (String) ReflectionTestUtils.invokeMethod(request, "origin");
        BigInteger value = (BigInteger) ReflectionTestUtils.invokeMethod(request, "value");
        BigInteger gasLimit = (BigInteger) ReflectionTestUtils.invokeMethod(request, "gasLimit");
        String code = (String) ReflectionTestUtils.invokeMethod(request, "code");
        String data = (String) ReflectionTestUtils.invokeMethod(request, "data");
        String salt = (String) ReflectionTestUtils.invokeMethod(request, "salt");

        assertThat(origin).isEqualTo("//Alice");
        assertThat(value).isEqualTo(BigInteger.ZERO);
        assertThat(gasLimit).isEqualTo(new BigInteger("123"));
        assertThat(code).isEqualTo("0x0061");
        assertThat(data).isEqualTo("0x");
        assertThat(salt).isEqualTo("0x");
    }
}

