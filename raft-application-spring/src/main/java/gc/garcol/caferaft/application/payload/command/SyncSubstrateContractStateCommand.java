package gc.garcol.caferaft.application.payload.command;

import gc.garcol.caferaft.core.client.Command;

/**
 * Comando para sincronizar el estado de un contrato desde Substrate a Café Raft.
 * Esto permite mantener una copia del estado en Raft para consultas rápidas
 * y garantizar consistencia entre ambos sistemas.
 */
public record SyncSubstrateContractStateCommand(
        String contractName,
        String method,      // Método a invocar para obtener estado (ej: "get")
        String inputHex     // Input SCALE-encoded para la query (puede ser vacío para métodos sin parámetros)
) implements Command {
}

