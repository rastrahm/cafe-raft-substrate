package gc.garcol.caferaft.application.payload.query;

import gc.garcol.caferaft.core.client.Query;

/**
 * Query para consultar el estado sincronizado de un contrato Substrate.
 */
public record ContractStateQuery(String contractName) implements Query {
}

