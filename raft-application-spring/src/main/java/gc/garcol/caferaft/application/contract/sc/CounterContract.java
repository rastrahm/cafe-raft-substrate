package gc.garcol.caferaft.application.contract.sc;

import gc.garcol.caferaft.application.contract.Contract;

import java.util.Map;

/**
 * Contrato de ejemplo 100% JVM que mantiene un contador en memoria.
 *
 * Métodos expuestos:
 *  - increment -> incrementa en 1 y devuelve el valor actual.
 *  - decrement -> decrementa en 1 y devuelve el valor actual.
 *  - add       -> suma la cantidad indicada (argumento "amount") y devuelve el valor actual.
 *  - value     -> devuelve el valor actual sin modificarlo.
 *  - reset     -> reinicia el contador a 0.
 */
public class CounterContract implements Contract {

    private int counter = 0;

    @Override
    public synchronized Object invoke(String method, Map<String, Object> args) {
        return switch (method) {
            case "increment" -> ++counter;
            case "decrement" -> --counter;
            case "add" -> {
                int amount = extractInt(args, "amount", 0);
                counter += amount;
                yield counter;
            }
            case "value" -> counter;
            case "reset" -> {
                counter = 0;
                yield counter;
            }
            default -> throw new IllegalArgumentException("Método no soportado: " + method);
        };
    }

    private int extractInt(Map<String, Object> args, String name, int defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        Object value = args.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        throw new IllegalArgumentException("El argumento '" + name + "' debe ser numérico.");
    }
}


