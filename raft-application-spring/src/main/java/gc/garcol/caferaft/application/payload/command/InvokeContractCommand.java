package gc.garcol.caferaft.application.payload.command;

import gc.garcol.caferaft.core.client.Command;

import java.util.Map;

/**
 * Invocación de contrato: método + args (clave/valor) deterministas.
 */
public record InvokeContractCommand(String name, String method, Map<String, Object> args) implements Command { }


