// SPDX-License-Identifier: MIT
pragma solidity >=0.8.0;

/**
 * Contrato simple de ejemplo para compilar con Solang a WASM.
 * 
 * Para compilar:
 *   solang compile --target substrate SimpleCounter.sol
 * 
 * Esto generará SimpleCounter.wasm que puedes convertir a Base64:
 *   base64 -w 0 SimpleCounter.wasm > SimpleCounter.wasm.base64
 */
contract SimpleCounter {
    uint256 private count;
    
    /**
     * Incrementa el contador
     */
    function increment() public {
        count++;
    }
    
    /**
     * Obtiene el valor actual del contador
     */
    function getCount() public view returns (uint256) {
        return count;
    }
    
    /**
     * Suma dos números
     */
    function add(uint256 a, uint256 b) public pure returns (uint256) {
        return a + b;
    }
    
    /**
     * Multiplica dos números
     */
    function multiply(uint256 a, uint256 b) public pure returns (uint256) {
        return a * b;
    }
}

