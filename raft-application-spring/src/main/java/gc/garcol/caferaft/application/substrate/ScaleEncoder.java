package gc.garcol.caferaft.application.substrate;

import java.math.BigInteger;
import java.util.HexFormat;

/**
 * Helper básico para codificación SCALE simple.
 * Para casos complejos, se recomienda usar una librería completa como scale-codec-java.
 */
public class ScaleEncoder {
    private static final HexFormat HEX = HexFormat.of();
    private final StringBuilder hex = new StringBuilder();

    public ScaleEncoder encodeCompactU32(int value) {
        if (value < 64) {
            // Single byte mode: bits 0-5 = value, bit 6 = 0
            hex.append(String.format("%02x", value << 1));
        } else if (value < 16384) {
            // Two byte mode: bits 0-5 = lower 6 bits, bits 6-13 = upper 8 bits, bit 14 = 1
            int low = value & 0x3F;
            int high = (value >> 6) & 0xFF;
            hex.append(String.format("%02x%02x", (low << 1) | 0x40, high));
        } else {
            // Four byte mode (simplificado)
            hex.append(String.format("%02x", 0x80));
            hex.append(String.format("%08x", value));
        }
        return this;
    }

    public ScaleEncoder encodeU8(int value) {
        hex.append(String.format("%02x", value & 0xFF));
        return this;
    }

    public ScaleEncoder encodeU128(BigInteger value) {
        // Little-endian, 16 bytes
        byte[] bytes = new byte[16];
        byte[] valueBytes = value.toByteArray();
        // BigInteger.toByteArray() devuelve big-endian, necesitamos little-endian
        int srcLen = valueBytes.length;
        int dstStart = Math.max(0, 16 - srcLen);
        // Copiar bytes en orden inverso para little-endian
        for (int i = 0; i < Math.min(srcLen, 16); i++) {
            bytes[dstStart + i] = valueBytes[srcLen - 1 - i];
        }
        hex.append(HEX.formatHex(bytes));
        return this;
    }

    public ScaleEncoder encodeBytes(byte[] data) {
        encodeCompactU32(data.length);
        hex.append(HEX.formatHex(data));
        return this;
    }

    public ScaleEncoder encodeHex(String hexData) {
        String clean = hexData.startsWith("0x") ? hexData.substring(2) : hexData;
        hex.append(clean.toLowerCase());
        return this;
    }

    public String toHex() {
        return "0x" + hex.toString();
    }

    public String toString() {
        return toHex();
    }
}

