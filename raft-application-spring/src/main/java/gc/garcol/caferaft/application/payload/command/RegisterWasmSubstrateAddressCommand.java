package gc.garcol.caferaft.application.payload.command;

import gc.garcol.caferaft.core.client.Command;

/**
 * Registra la dirección Substrate asociada a un contrato WASM conocido por Raft.
 */
public record RegisterWasmSubstrateAddressCommand(String name, String address) implements Command { }


