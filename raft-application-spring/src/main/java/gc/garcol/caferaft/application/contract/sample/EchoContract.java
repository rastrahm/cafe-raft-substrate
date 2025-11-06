package gc.garcol.caferaft.application.contract.sample;

import gc.garcol.caferaft.application.contract.Contract;

import java.math.BigDecimal;
import java.util.Map;

public class EchoContract implements Contract {
    @Override
    public Object invoke(String method, Map<String, Object> args) {
        return switch (method) {
            case "echo" -> args.get("message");
            case "sum" -> sum(args);
            default -> "unknown-method:" + method;
        };
    }

    private BigDecimal sum(Map<String, Object> args) {
        BigDecimal a = toBigDecimal(args.get("a"));
        BigDecimal b = toBigDecimal(args.get("b"));
        return a.add(b);
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(o));
    }
}


