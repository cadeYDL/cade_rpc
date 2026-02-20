package org.cade.rpc.loadbalance;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.spi.SPI;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j(topic = "load_balancer_manager")
public class LoadBalancerManager {
    private final Map<String, LoadBalancer> loadBalancerMap = new HashMap<>();

    public LoadBalancerManager() {
        init();
    }

    private void init() {
        ServiceLoader<LoadBalancer> loadBalancerLoader = ServiceLoader.load(LoadBalancer.class);
        for (LoadBalancer loadBalancer : loadBalancerLoader) {
            SPI spi = loadBalancer.getClass().getDeclaredAnnotation(SPI.class);
            if (spi == null) {
                log.warn("spi is null {}", loadBalancer.getClass().getName());
                continue;
            }
            String name = spi.value();
            if (loadBalancerMap.put(name.toUpperCase(Locale.ROOT), loadBalancer) != null) {
                throw new IllegalArgumentException("load balancer name must be unique");
            }
        }
    }

    public LoadBalancer getLoadBalancer(String name) {
        return loadBalancerMap.get(name.toUpperCase(Locale.ROOT));
    }
}
