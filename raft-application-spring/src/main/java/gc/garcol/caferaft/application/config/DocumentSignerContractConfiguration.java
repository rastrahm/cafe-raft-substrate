package gc.garcol.caferaft.application.config;

import gc.garcol.caferaft.application.contract.ContractRegistry;
import gc.garcol.caferaft.application.contract.document.DocumentSignerContract;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DocumentSignerContractConfiguration {

    private final ContractRegistry contractRegistry;

    @PostConstruct
    public void register() {
        log.info("Registrando contrato documentSigner en ContractRegistry");
        contractRegistry.register("documentSigner", new DocumentSignerContract());
    }
}
