package gc.garcol.caferaft.application.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubstrateRpcClient {

    private final ObjectMapper objectMapper;
    private final AtomicLong idGenerator = new AtomicLong(1);
    private RestTemplate restTemplate;

    @Value("${substrate.rpc-url:http://127.0.0.1:9944}")
    private String rpcUrl;

    @PostConstruct
    void init() {
        this.restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory());
    }

    public JsonNode call(String method, List<?> params) {
        long id = idGenerator.getAndIncrement();
        JsonRpcRequest request = new JsonRpcRequest("2.0", method, params, id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            String payload = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(rpcUrl, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Respuesta inválida del nodo Substrate: " + response.getStatusCode());
            }
            JsonNode body = objectMapper.readTree(response.getBody());
            if (body.has("error")) {
                JsonNode error = body.get("error");
                throw new IllegalStateException("Error RPC (" + method + "): " + error.toString());
            }
            return body.get("result");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo serializar la petición RPC", e);
        }
    }

    public JsonNode call(String method) {
        return call(method, List.of());
    }

    /**
     * Llama a state_call para invocar métodos del runtime API.
     * 
     * @param method Nombre del método del runtime API (ej: "ContractsApi_call", "ContractsApi_instantiate")
     * @param data Parámetros SCALE-encoded en formato hex (0x...)
     * @param atBlockHash Hash del bloque (opcional, null para el más reciente)
     * @return Resultado JSON del nodo
     */
    public JsonNode stateCall(String method, String data, String atBlockHash) {
        List<Object> params = atBlockHash != null 
            ? List.of(method, data, atBlockHash)
            : List.of(method, data);
        return call("state_call", params);
    }

    public JsonNode stateCall(String method, String data) {
        return stateCall(method, data, null);
    }

    private record JsonRpcRequest(String jsonrpc, String method, List<?> params, long id) {
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return factory;
    }
}

