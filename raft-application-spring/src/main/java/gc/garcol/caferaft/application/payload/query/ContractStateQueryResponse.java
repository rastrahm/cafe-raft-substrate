package gc.garcol.caferaft.application.payload.query;

import gc.garcol.caferaft.core.client.ClientResponse;

/**
 * Respuesta para la query de estado de contrato sincronizado.
 */
public record ContractStateQueryResponse(
        String contractName,
        String stateHex,
        long lastSyncTimestamp,
        long lastBlockNumber
) implements ClientResponse {
}

