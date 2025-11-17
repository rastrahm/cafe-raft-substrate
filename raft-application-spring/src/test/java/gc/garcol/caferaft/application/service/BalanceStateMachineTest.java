package gc.garcol.caferaft.application.service;

import gc.garcol.caferaft.application.model.Balance;
import gc.garcol.caferaft.application.payload.command.BatchBalanceCommand;
import gc.garcol.caferaft.application.payload.command.ModifyBalanceCommand;
import gc.garcol.caferaft.application.payload.command.CreateBalanceCommand;
import gc.garcol.caferaft.application.payload.command.DepositCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceStateMachineTest {

    private BalanceStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new BalanceStateMachine();
    }

    @Test
    @DisplayName("createBalance crea un balance nuevo con monto cero")
    void createBalanceCreatesNewAccount() {
        Balance balance = stateMachine.createBalance(1L);

        assertThat(balance.getId()).isEqualTo(1L);
        assertThat(balance.getAmount()).isEqualByComparingTo(BigInteger.ZERO);
        assertThat(stateMachine.exists(1L)).isTrue();
    }

    @Test
    @DisplayName("createBalance falla si la cuenta ya existe")
    void createBalanceFailsWhenExists() {
        stateMachine.createBalance(1L);

        assertThatThrownBy(() -> stateMachine.createBalance(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("getBalance devuelve la cuenta existente o falla si no existe")
    void getBalance() {
        stateMachine.createBalance(1L);

        assertThat(stateMachine.getBalance(1L)).isNotNull();
        assertThatThrownBy(() -> stateMachine.getBalance(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("deposit incrementa el saldo")
    void depositIncreasesAmount() {
        stateMachine.createBalance(1L);

        Balance balance = stateMachine.deposit(1L, BigInteger.TEN);

        assertThat(balance.getAmount()).isEqualByComparingTo(BigInteger.TEN);
    }

    @Test
    @DisplayName("deposit rechaza montos negativos")
    void depositRejectsNegativeAmounts() {
        stateMachine.createBalance(1L);

        assertThatThrownBy(() -> stateMachine.deposit(1L, BigInteger.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("withdraw decrementa el saldo")
    void withdrawDecreasesAmount() {
        stateMachine.createBalance(1L);
        stateMachine.deposit(1L, BigInteger.valueOf(20));

        Balance balance = stateMachine.withdraw(1L, BigInteger.valueOf(7));

        assertThat(balance.getAmount()).isEqualByComparingTo(BigInteger.valueOf(13));
    }

    @Test
    @DisplayName("withdraw falla si no hay fondos suficientes")
    void withdrawFailsWhenInsufficientFunds() {
        stateMachine.createBalance(1L);
        stateMachine.deposit(1L, BigInteger.TEN);

        assertThatThrownBy(() -> stateMachine.withdraw(1L, BigInteger.valueOf(20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("transfer mueve fondos entre cuentas")
    void transferMovesFunds() {
        stateMachine.createBalance(1L);
        stateMachine.createBalance(2L);
        stateMachine.deposit(1L, BigInteger.valueOf(30));

        stateMachine.transfer(1L, 2L, BigInteger.valueOf(20));

        assertThat(stateMachine.getBalance(1L).getAmount())
                .isEqualByComparingTo(BigInteger.TEN);
        assertThat(stateMachine.getBalance(2L).getAmount())
                .isEqualByComparingTo(BigInteger.valueOf(20));
    }

    @Test
    @DisplayName("transfer falla cuando cuentas son iguales")
    void transferFailsWithSameAccount() {
        stateMachine.createBalance(1L);
        stateMachine.deposit(1L, BigInteger.TEN);

        assertThatThrownBy(() -> stateMachine.transfer(1L, 1L, BigInteger.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be the same");
    }

    @Test
    @DisplayName("setActive cambia el estado activo")
    void setActiveUpdatesFlag() {
        stateMachine.createBalance(1L);

        Balance balance = stateMachine.setActive(1L, true);

        assertThat(balance.isActive()).isTrue();
    }

    @Test
    @DisplayName("delete elimina la cuenta")
    void deleteRemovesAccount() {
        stateMachine.createBalance(1L);

        boolean removed = stateMachine.delete(1L);

        assertThat(removed).isTrue();
        assertThat(stateMachine.exists(1L)).isFalse();
    }

    @Test
    @DisplayName("batch ejecuta comandos create/deposit exitosamente")
    void batchProcessesCommands() {
        BatchBalanceCommand batch = new BatchBalanceCommand();
        batch.setCommands(List.of(
                modify(createCommand(1L)),
                modify(depositCommand(1L, BigInteger.valueOf(15))),
                modify(createCommand(2L))
        ));

        var response = stateMachine.batch(batch);

        assertThat(response.getResult()).allMatch(result -> result.status() == 200);
        assertThat(stateMachine.getBalance(1L).getAmount()).isEqualByComparingTo(BigInteger.valueOf(15));
        assertThat(stateMachine.exists(2L)).isTrue();
    }

    private static ModifyBalanceCommand modify(CreateBalanceCommand command) {
        ModifyBalanceCommand modify = new ModifyBalanceCommand();
        modify.setCorrelationId(UUID.randomUUID());
        modify.setCreateBalanceCommand(command);
        return modify;
    }

    private static ModifyBalanceCommand modify(DepositCommand command) {
        ModifyBalanceCommand modify = new ModifyBalanceCommand();
        modify.setCorrelationId(UUID.randomUUID());
        modify.setDepositCommand(command);
        return modify;
    }

    private static CreateBalanceCommand createCommand(long id) {
        return new CreateBalanceCommand(id);
    }

    private static DepositCommand depositCommand(long id, BigInteger amount) {
        return new DepositCommand(id, amount);
    }
}

