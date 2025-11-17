package gc.garcol.caferaft.application.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubstrateContractsClient {

    private static final HexFormat HEX = HexFormat.of();

    private final SubstrateRpcClient rpcClient;
    private final ObjectMapper objectMapper;

    @Value("${substrate.default-origin://Alice}")
    private String defaultSuri;

    @Value("${substrate.default-gas-limit:500000000000}")
    private BigInteger defaultGasLimit;

    @Value("${substrate.default-storage-deposit-limit:0}")
    private BigInteger defaultStorageDepositLimit;

    public JsonNode systemHealth() {
        return rpcClient.call("system_health");
    }

    public JsonNode chainGetBlockHash() {
        return rpcClient.call("chain_getBlockHash");
    }

    public JsonNode stateMetadata() {
        return rpcClient.call("state_getMetadata");
    }

    /**
     * Realiza un {@code contracts_call} (dry-run) con parámetros ya SCALE-encodeds.
     *
     * @param origin  Cuenta que invoca (SS58 o hex prefijado en 0x).
     * @param dest    Dirección del contrato desplegado (SS58 o hex 0x...).
     * @param value   Valor a transferir (Plancks) como {@link BigInteger}.
     * @param gas     Límite de gas (weight) como {@link BigInteger}. Si es null se usa el por defecto.
     * @param input   Datos SCALE-encoded del método (hex prefijado en 0x).
     * @return Resultado JSON del nodo.
     */
    public JsonNode dryRunCall(String origin, String dest, BigInteger value, BigInteger gas, String input) {
        ContractsCallRequest request = new ContractsCallRequest(
                normalizeAccount(origin),
                normalizeAccount(dest),
                value != null ? value : BigInteger.ZERO,
                gas != null ? gas : defaultGasLimit,
                defaultStorageDepositLimit,
                ensureHex(input)
        );
        log.debug("Ejecutando contracts_call dry-run: {}", request);
        return rpcClient.call("contracts_call", List.of(request));
    }

    /**
     * Ejecuta {@code contracts_instantiateDryRun}. El {@code salt} e {@code input} deben venir en hex (0x...).
     */
    public JsonNode dryRunInstantiate(String origin,
                                      String codeHashHex,
                                      BigInteger value,
                                      BigInteger gas,
                                      String input,
                                      String salt) {
        ContractsInstantiateRequest request = new ContractsInstantiateRequest(
                normalizeAccount(origin),
                ensureHex(codeHashHex),
                value != null ? value : BigInteger.ZERO,
                gas != null ? gas : defaultGasLimit,
                defaultStorageDepositLimit,
                ensureHex(input),
                ensureHex(salt)
        );
        log.debug("Ejecutando contracts_instantiateDryRun: {}", request);
        return rpcClient.call("contracts_instantiateDryRun", List.of(request));
    }

    /**
     * Ejecuta {@code contracts_instantiateWithCode}, que sube el código WASM y crea una nueva instancia.
     * DEPRECADO: Usa {@link #instantiateWithCodeViaStateCall} en su lugar.
     *
     * @param origin Cuenta que firma la operación.
     * @param wasm   Código WASM bruto.
     * @param value  Endowment transferido.
     * @param gas    Límite de gas.
     * @param input  Datos SCALE del constructor.
     * @param salt   Salt opcional para address determinística.
     */
    @Deprecated
    public JsonNode instantiateWithCode(String origin,
                                        byte[] wasm,
                                        BigInteger value,
                                        BigInteger gas,
                                        String input,
                                        String salt) {
        ContractsInstantiateWithCodeRequest request = new ContractsInstantiateWithCodeRequest(
                normalizeAccount(origin),
                value != null ? value : BigInteger.ZERO,
                gas != null ? gas : defaultGasLimit,
                defaultStorageDepositLimit,
                hexFromBytes(wasm),
                ensureHex(input),
                ensureHex(salt)
        );
        log.info("Ejecutando contracts_instantiateWithCode para {}", origin);
        return rpcClient.call("contracts_instantiateWithCode", List.of(request));
    }

    /**
     * Usa state_call con ContractsApi_instantiate para instanciar un contrato.
     * Los parámetros deben estar SCALE-encoded.
     * 
     * @param originAccountId AccountId del origin (32 bytes hex)
     * @param codeHash Hash del código del contrato (32 bytes hex)
     * @param value Endowment (u128)
     * @param gasLimit Límite de gas (u64)
     * @param storageDepositLimit Límite de depósito de almacenamiento (u128, opcional)
     * @param data Datos del constructor SCALE-encoded (hex)
     * @param salt Salt para address determinística (bytes, hex)
     * @return Resultado JSON del nodo
     */
    public JsonNode instantiateViaStateCall(String originAccountId,
                                            String codeHash,
                                            BigInteger value,
                                            BigInteger gasLimit,
                                            BigInteger storageDepositLimit,
                                            String data,
                                            String salt) {
        // Codificar parámetros en SCALE para ContractsApi_instantiate
        // Estructura: (origin: AccountId, value: Balance, gas_limit: Weight, storage_deposit_limit: Option<Balance>, 
        //              code_hash: CodeHash, data: Vec<u8>, salt: Vec<u8>)
        ScaleEncoder encoder = new ScaleEncoder();
        
        // Origin (AccountId - 32 bytes)
        encoder.encodeHex(ensureHex(originAccountId).substring(2));
        
        // Value (Balance - u128)
        encoder.encodeU128(value != null ? value : BigInteger.ZERO);
        
        // Gas limit (Weight - u64, simplificado como u128)
        encoder.encodeU128(gasLimit != null ? gasLimit : defaultGasLimit);
        
        // Storage deposit limit (Option<Balance> - u128)
        if (storageDepositLimit != null && storageDepositLimit.compareTo(BigInteger.ZERO) > 0) {
            encoder.encodeU8(1); // Some
            encoder.encodeU128(storageDepositLimit);
        } else {
            encoder.encodeU8(0); // None
        }
        
        // Code hash (CodeHash - 32 bytes)
        encoder.encodeHex(ensureHex(codeHash).substring(2));
        
        // Data (Vec<u8>)
        String cleanData = ensureHex(data);
        byte[] dataBytes = HEX.parseHex(cleanData.startsWith("0x") ? cleanData.substring(2) : cleanData);
        encoder.encodeBytes(dataBytes);
        
        // Salt (Vec<u8>)
        String cleanSalt = ensureHex(salt);
        byte[] saltBytes = HEX.parseHex(cleanSalt.startsWith("0x") ? cleanSalt.substring(2) : cleanSalt);
        encoder.encodeBytes(saltBytes);
        
        String encodedParams = encoder.toHex();
        log.debug("Llamando ContractsApi_instantiate con params: {}", encodedParams);
        return rpcClient.stateCall("ContractsApi_instantiate", encodedParams);
    }

    /**
     * Usa state_call con ContractsApi_call para invocar un contrato.
     * 
     * @param originAccountId AccountId del origin (32 bytes hex)
     * @param destAccountId AccountId del contrato (32 bytes hex)
     * @param value Valor a transferir (u128)
     * @param gasLimit Límite de gas (u64)
     * @param storageDepositLimit Límite de depósito de almacenamiento (u128, opcional)
     * @param input Datos de la llamada SCALE-encoded (hex)
     * @return Resultado JSON del nodo
     */
    public JsonNode callViaStateCall(String originAccountId,
                                    String destAccountId,
                                    BigInteger value,
                                    BigInteger gasLimit,
                                    BigInteger storageDepositLimit,
                                    String input) {
        // Codificar parámetros en SCALE para ContractsApi_call
        // Estructura: (origin: AccountId, dest: AccountId, value: Balance, gas_limit: Weight, 
        //              storage_deposit_limit: Option<Balance>, input_data: Vec<u8>)
        ScaleEncoder encoder = new ScaleEncoder();
        
        // Origin (AccountId - 32 bytes)
        encoder.encodeHex(ensureHex(originAccountId).substring(2));
        
        // Dest (AccountId - 32 bytes)
        encoder.encodeHex(ensureHex(destAccountId).substring(2));
        
        // Value (Balance - u128)
        encoder.encodeU128(value != null ? value : BigInteger.ZERO);
        
        // Gas limit (Weight - u64, simplificado como u128)
        encoder.encodeU128(gasLimit != null ? gasLimit : defaultGasLimit);
        
        // Storage deposit limit (Option<Balance> - u128)
        if (storageDepositLimit != null && storageDepositLimit.compareTo(BigInteger.ZERO) > 0) {
            encoder.encodeU8(1); // Some
            encoder.encodeU128(storageDepositLimit);
        } else {
            encoder.encodeU8(0); // None
        }
        
        // Input data (Vec<u8>)
        String cleanInput = ensureHex(input);
        byte[] inputBytes = HEX.parseHex(cleanInput.startsWith("0x") ? cleanInput.substring(2) : cleanInput);
        encoder.encodeBytes(inputBytes);
        
        String encodedParams = encoder.toHex();
        log.debug("Llamando ContractsApi_call con params: {}", encodedParams);
        return rpcClient.stateCall("ContractsApi_call", encodedParams);
    }

    private String normalizeAccount(String account) {
        if (account == null || account.isBlank()) {
            return defaultSuri;
        }
        if (account.startsWith("0x") || account.startsWith("//")) {
            return account;
        }
        if (account.length() == 48 || account.length() == 47) { // SS58 typical lengths
            return account;
        }
        try {
            HEX.parseHex(account);
            return "0x" + account.toLowerCase();
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Cuenta/dirección no soportada: " + account);
        }
    }

    /**
     * Convierte una cuenta SS58 o SURI a AccountId (32 bytes hex).
     * Usa state_call con AccountIdApi_account_id si es necesario.
     * Por ahora, asume que si ya es hex de 32 bytes, lo devuelve tal cual.
     */
    private String accountToAccountIdHex(String account) {
        if (account == null || account.isBlank()) {
            account = defaultSuri;
        }
        
        // Si ya es hex de 64 caracteres (32 bytes), devolverlo
        String clean = account.startsWith("0x") ? account.substring(2) : account;
        if (clean.length() == 64) {
            try {
                HEX.parseHex(clean);
                return "0x" + clean.toLowerCase();
            } catch (IllegalArgumentException ignored) {
                // Continuar con la conversión
            }
        }
        
        // Si es SS58 o SURI, necesitamos convertirlo
        // Por ahora, lanzamos error indicando que se necesita AccountId hex
        // TODO: Implementar conversión SS58/SUri a AccountId usando state_call o librería
        throw new IllegalArgumentException(
            "Se requiere AccountId en formato hex (32 bytes). " +
            "Para convertir SS58/SUri, usa una herramienta externa o implementa la conversión."
        );
    }

    private String ensureHex(String data) {
        if (data == null) {
            return "0x";
        }
        String trimmed = data.trim();
        if (trimmed.isEmpty()) {
            return "0x";
        }
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return trimmed.toLowerCase();
        }
        try {
            HEX.parseHex(trimmed);
            return "0x" + trimmed.toLowerCase();
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Datos esperados en hex (con o sin 0x): " + data);
        }
    }

    private String hexFromBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return "0x";
        }
        return "0x" + HEX.formatHex(data);
    }

    private record ContractsCallRequest(
            String origin,
            String dest,
            BigInteger value,
            BigInteger gasLimit,
            BigInteger storageDepositLimit,
            String input) {
    }

    private record ContractsInstantiateRequest(
            String origin,
            String codeHash,
            BigInteger value,
            BigInteger gasLimit,
            BigInteger storageDepositLimit,
            String input,
            String salt) {
    }

    private record ContractsInstantiateWithCodeRequest(
            String origin,
            BigInteger value,
            BigInteger gasLimit,
            BigInteger storageDepositLimit,
            String code,
            String data,
            String salt) {
    }
}

