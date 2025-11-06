package gc.garcol.caferaft.application.payload.command;

import gc.garcol.caferaft.core.client.Command;

/**
 * Despliegue de contrato: registra una clase que implementa Contract con un nombre lógico.
 */
public record DeployContractCommand(String name, String className) implements Command { }


