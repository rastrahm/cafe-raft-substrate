package gc.garcol.caferaft.application.network.rest;

import gc.garcol.caferaft.application.payload.command.DeployContractCommand;
import gc.garcol.caferaft.application.payload.command.InvokeContractCommand;
import lombok.RequiredArgsConstructor;
import gc.garcol.caferaft.application.payload.command.DeployWasmContractCommand;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class ContractController {

    private final RequestDispatcher requestDispatcher;

    @PostMapping("/contracts/deploy")
    Mono<?> deploy(ServerWebExchange request, @RequestBody DeployContractCommand command) {
        return Mono.fromFuture(requestDispatcher.dispatch(request, command));
    }

    @PostMapping("/contracts/invoke")
    Mono<?> invoke(ServerWebExchange request, @RequestBody InvokeContractCommand command) {
        return Mono.fromFuture(requestDispatcher.dispatch(request, command));
    }

    @PostMapping("/contracts/wasm/deploy")
    Mono<?> deployWasm(ServerWebExchange request, @RequestBody DeployWasmContractCommand command) {
        return Mono.fromFuture(requestDispatcher.dispatch(request, command));
    }
}


