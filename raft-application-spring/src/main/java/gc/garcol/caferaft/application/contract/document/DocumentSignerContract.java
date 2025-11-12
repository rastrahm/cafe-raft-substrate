package gc.garcol.caferaft.application.contract.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gc.garcol.caferaft.application.contract.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrato determinista para registrar y verificar firmas de documentos.
 */
public class DocumentSignerContract implements Contract {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, SignatureRecord> signatureByCompositeKey = new LinkedHashMap<>();
    private final Map<String, List<SignatureRecord>> signaturesByDocument = new ConcurrentHashMap<>();
    private final List<SignatureRecord> signatureLog = new ArrayList<>();

    @Override
    public synchronized Object invoke(String method, Map<String, Object> args) {
        return switch (method) {
            case "signDocument" -> signDocument(args);
            case "verifyDocument" -> verifyDocument(args);
            case "listSignatures" -> listSignatures(args);
            case "getSignature" -> getSignature(args);
            default -> error("unknown_method", "Método no soportado: " + method);
        };
    }

    private String signDocument(Map<String, Object> args) {
        String documentHash = normalizeHash(args.get("documentHash"));
        String signer = normalizeString(args.get("signer"));
        String signature = normalizeString(args.get("signature"));
        long signedAt = parseLong(args.get("signedAt"));

        if (documentHash == null || documentHash.isBlank()) {
            return error("invalid_document_hash", "documentHash es requerido");
        }
        if (signer == null || signer.isBlank()) {
            return error("invalid_signer", "signer es requerido");
        }
        if (signature == null || signature.isBlank()) {
            return error("invalid_signature", "signature es requerido");
        }
        if (signedAt <= 0L) {
            return error("invalid_timestamp", "signedAt debe ser un timestamp unix válido");
        }

        String compositeKey = compositeKey(documentHash, signer);
        if (signatureByCompositeKey.containsKey(compositeKey)) {
            return error("already_signed", "Este firmante ya registró una firma para este documento");
        }

        SignatureRecord record = new SignatureRecord(documentHash, signer, signature, signedAt);
        signatureByCompositeKey.put(compositeKey, record);
        signatureLog.add(record);
        signaturesByDocument.computeIfAbsent(documentHash, key -> new ArrayList<>()).add(record);
        sortByTimestamp(signaturesByDocument.get(documentHash));

        return success(Map.of(
                "message", "Documento firmado exitosamente",
                "signature", record
        ));
    }

    private String verifyDocument(Map<String, Object> args) {
        String documentHash = normalizeHash(args.get("documentHash"));
        String signer = normalizeString(args.get("signer"));

        if (documentHash == null || documentHash.isBlank()) {
            return error("invalid_document_hash", "documentHash es requerido");
        }
        if (signer == null || signer.isBlank()) {
            return error("invalid_signer", "signer es requerido");
        }

        String compositeKey = compositeKey(documentHash, signer);
        SignatureRecord record = signatureByCompositeKey.get(compositeKey);
        boolean isVerified = record != null;

        return success(Map.of(
                "isVerified", isVerified,
                "signature", Optional.ofNullable(record).orElse(null)
        ));
    }

    private String listSignatures(Map<String, Object> args) {
        String documentHashFilter = null;
        if (args != null) {
            documentHashFilter = normalizeHash(args.get("documentHash"));
        }

        List<SignatureRecord> result;
        if (documentHashFilter != null && !documentHashFilter.isBlank()) {
            result = new ArrayList<>(signaturesByDocument.getOrDefault(documentHashFilter, Collections.emptyList()));
        } else {
            result = new ArrayList<>(signatureLog);
        }

        sortByTimestamp(result);
        return success(Map.of(
                "count", result.size(),
                "items", result
        ));
    }

    private String getSignature(Map<String, Object> args) {
        String documentHash = normalizeHash(args.get("documentHash"));
        String signer = normalizeString(args.get("signer"));
        if (documentHash == null || signer == null) {
            return error("invalid_arguments", "documentHash y signer son requeridos");
        }

        String compositeKey = compositeKey(documentHash, signer);
        SignatureRecord record = signatureByCompositeKey.get(compositeKey);
        if (record == null) {
            return error("not_found", "No se encontró la firma para el documento y firmante indicados");
        }
        return success(Map.of("signature", record));
    }

    private static void sortByTimestamp(List<SignatureRecord> records) {
        records.sort(Comparator.comparingLong(SignatureRecord::signedAt));
    }

    private static String compositeKey(String documentHash, String signer) {
        return documentHash + "::" + signer.toLowerCase(Locale.ROOT);
    }

    private static String normalizeString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.trim();
        }
        return String.valueOf(value).trim();
    }

    private static String normalizeHash(Object value) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String normalized = normalizeString(value);
        if (normalized == null || normalized.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String success(Object payload) {
        return toJson(Map.of(
                "status", "ok",
                "data", payload
        ));
    }

    private static String error(String code, String message) {
        return toJson(Map.of(
                "status", "error",
                "error", Map.of(
                        "code", code,
                        "message", message
                )
        ));
    }

    private static String toJson(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"error\":{\"code\":\"serialization_error\",\"message\":\"No se pudo serializar la respuesta\"}}";
        }
    }

    /**
     * Registro inmutable que describe una firma almacenada.
     */
    public record SignatureRecord(String documentHash, String signer, String signature, long signedAt) {
        public SignatureRecord {
            Objects.requireNonNull(documentHash, "documentHash");
            Objects.requireNonNull(signer, "signer");
            Objects.requireNonNull(signature, "signature");
        }
    }
}
