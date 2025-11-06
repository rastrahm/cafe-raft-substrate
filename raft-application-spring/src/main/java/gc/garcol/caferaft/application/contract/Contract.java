package gc.garcol.caferaft.application.contract;

import java.util.Map;

/**
 * MVP de contratos: cada contrato debe implementar este método.
 * Se espera determinismo: misma entrada → misma salida.
 */
public interface Contract {
    Object invoke(String method, Map<String, Object> args);
}


