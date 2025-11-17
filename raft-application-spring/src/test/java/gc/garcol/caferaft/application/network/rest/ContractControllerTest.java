package gc.garcol.caferaft.application.network.rest;

import gc.garcol.caferaft.application.payload.command.DeployContractCommand;
import gc.garcol.caferaft.application.payload.command.DeployWasmContractCommand;
import gc.garcol.caferaft.application.payload.command.InvokeContractCommand;
import gc.garcol.caferaft.application.payload.command.RegisterWasmSubstrateAddressCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ContractController.class)
class ContractControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RequestDispatcher requestDispatcher;

    @Test
    @DisplayName("POST /contracts/deploy despacha DeployContractCommand")
    void deployContractDispatchesCommand() {
        DeployContractCommand command = new DeployContractCommand("counter", "gc.garcol.Counter");
        when(requestDispatcher.dispatch(any(ServerWebExchange.class), any(DeployContractCommand.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture("ok"));

        webTestClient.post()
                .uri("/contracts/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<DeployContractCommand> captor = ArgumentCaptor.forClass(DeployContractCommand.class);
        verify(requestDispatcher).dispatch(any(ServerWebExchange.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(command);
    }

    @Test
    @DisplayName("POST /contracts/invoke despacha InvokeContractCommand con args")
    void invokeContractDispatchesCommand() {
        InvokeContractCommand command = new InvokeContractCommand("counter", "increment", Map.of("amount", 5));
        when(requestDispatcher.dispatch(any(ServerWebExchange.class), any(InvokeContractCommand.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(6));

        webTestClient.post()
                .uri("/contracts/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<InvokeContractCommand> captor = ArgumentCaptor.forClass(InvokeContractCommand.class);
        verify(requestDispatcher).dispatch(any(ServerWebExchange.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(command);
    }

    @Test
    @DisplayName("POST /contracts/wasm/deploy despacha DeployWasmContractCommand")
    void deployWasmDispatchesCommand() {
        DeployWasmContractCommand command = new DeployWasmContractCommand("add", "dGVzdA==");
        when(requestDispatcher.dispatch(any(ServerWebExchange.class), any(DeployWasmContractCommand.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(200));

        webTestClient.post()
                .uri("/contracts/wasm/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<DeployWasmContractCommand> captor = ArgumentCaptor.forClass(DeployWasmContractCommand.class);
        verify(requestDispatcher).dispatch(any(ServerWebExchange.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(command);
    }

    @Test
    @DisplayName("POST /contracts/wasm/register-address despacha RegisterWasmSubstrateAddressCommand")
    void registerWasmAddressDispatchesCommand() {
        RegisterWasmSubstrateAddressCommand command =
                new RegisterWasmSubstrateAddressCommand("voting", "0x1234abcd");
        when(requestDispatcher.dispatch(any(ServerWebExchange.class),
                any(RegisterWasmSubstrateAddressCommand.class)))
                .thenAnswer(inv -> CompletableFuture.completedFuture(200));

        webTestClient.post()
                .uri("/contracts/wasm/register-address")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<RegisterWasmSubstrateAddressCommand> captor =
                ArgumentCaptor.forClass(RegisterWasmSubstrateAddressCommand.class);
        verify(requestDispatcher).dispatch(any(ServerWebExchange.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(command);
    }
}

