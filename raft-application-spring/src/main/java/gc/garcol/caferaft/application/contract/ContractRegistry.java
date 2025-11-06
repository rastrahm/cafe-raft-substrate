package gc.garcol.caferaft.application.contract;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ContractRegistry {

    private final Map<String, Contract> nameToContract = new ConcurrentHashMap<>();

    public void register(String name, Contract contract) {
        nameToContract.put(name, contract);
    }

    public Contract get(String name) {
        return nameToContract.get(name);
    }
}


