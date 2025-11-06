package gc.garcol.caferaft.application.payload.command;

import gc.garcol.caferaft.core.client.Command;

/**
 * Despliegue de contrato WASM: nombre lógico + módulo en Base64.
 */
public record DeployWasmContractCommand(String name, String wasmBase64) implements Command { }


