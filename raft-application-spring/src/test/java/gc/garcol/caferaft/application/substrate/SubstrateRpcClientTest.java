package gc.garcol.caferaft.application.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SubstrateRpcClientTest {

    private SubstrateRpcClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new SubstrateRpcClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "rpcUrl", "http://localhost:18080");
        client.init();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("call envía la petición JSON-RPC y retorna el nodo result")
    void callReturnsResultNode() {
        server.expect(ExpectedCount.once(), requestTo("http://localhost:18080"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"jsonrpc":"2.0","method":"system_health","params":[],"id":1}
                        """))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"jsonrpc":"2.0","result":{"status":"ok"}}
                                """));

        JsonNode result = client.call("system_health", List.of());

        assertThat(result.get("status").asText()).isEqualTo("ok");
        server.verify();
    }

    @Test
    @DisplayName("call lanza excepción cuando el cuerpo incluye error")
    void callThrowsWhenErrorPresent() {
        server.expect(requestTo("http://localhost:18080"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"jsonrpc":"2.0","error":{"code":123,"message":"fail"}}
                                """));

        assertThatThrownBy(() -> client.call("contracts_call", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Error RPC");
        server.verify();
    }

    @Test
    @DisplayName("call lanza excepción cuando Substrate responde con estado no exitoso")
    void callThrowsOnNon2xx() {
        server.expect(requestTo("http://localhost:18080"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("gateway error"));

        assertThatThrownBy(() -> client.call("system_health", List.of()))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        server.verify();
    }
}

