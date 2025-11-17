package gc.garcol.caferaft.application.contract.sc;

import gc.garcol.caferaft.application.contract.Contract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CounterContractTest {

    private Contract contract;

    @BeforeEach
    void setUp() {
        contract = new CounterContract();
    }

    @Test
    @DisplayName("Incrementa y decrementa correctamente")
    void incrementAndDecrement() {
        assertEquals(1, contract.invoke("increment", Map.of()));
        assertEquals(2, contract.invoke("increment", Map.of()));
        assertEquals(1, contract.invoke("decrement", Map.of()));
    }

    @Test
    @DisplayName("Suma cantidades arbitrarias mediante add")
    void addWithAmount() {
        assertEquals(5, contract.invoke("add", Map.of("amount", 5)));
        assertEquals(8, contract.invoke("add", Map.of("amount", 3)));
    }

    @Test
    @DisplayName("Reset deja el contador en cero")
    void resetResetsToZero() {
        contract.invoke("add", Map.of("amount", 10));
        assertEquals(0, contract.invoke("reset", Map.of()));
        assertEquals(0, contract.invoke("value", Map.of()));
    }

    @Test
    @DisplayName("Lanza excepción ante métodos desconocidos")
    void unknownMethodThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> contract.invoke("unknown", Map.of()));
    }

    @Test
    @DisplayName("Lanza excepción si amount no es numérico")
    void nonNumericAmountThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> contract.invoke("add", Map.of("amount", "abc")));
    }
}

