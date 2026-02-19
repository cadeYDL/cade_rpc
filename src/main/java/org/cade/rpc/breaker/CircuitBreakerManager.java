package org.cade.rpc.breaker;

import org.cade.rpc.comsumer.ConsumerProperties;
import org.cade.rpc.register.Metadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerManager {
    private final Map<Metadata,CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final ConsumerProperties properties;

    public CircuitBreakerManager(ConsumerProperties properties) {
        this.properties = properties;
    }

    public CircuitBreaker getCircuitBreaker(Metadata metadata){
        return circuitBreakerMap.computeIfAbsent(metadata,this::createBreaker);
    }

    public CircuitBreaker createBreaker(Metadata metadata){
        return new ResponseTimeCircuitBreaker(5,properties.getSlowRequestBreakRatio());
    }
}
