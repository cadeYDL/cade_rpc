package org.cade.rpc.retry;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.spi.SPI;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j(topic = "retry_manager")
public class RetryManager {
    private final Map<String, RetryPolicy> retryMap = new HashMap<>();

    public RetryManager(){
        init();
    }

    private void init(){
        ServiceLoader<RetryPolicy> retryPolicyLoader = ServiceLoader.load(RetryPolicy.class);
        for (RetryPolicy retryPolicy : retryPolicyLoader){
            SPI spi = retryPolicy.getClass().getDeclaredAnnotation(SPI.class);
            if(spi==null){
                log.warn("spi is null {}",retryPolicy.getClass().getName());
                continue;
            }
            String name = spi.value();
            if(retryMap.put(name.toUpperCase(Locale.ROOT),retryPolicy)!=null){
                throw new IllegalArgumentException("retry name must be unique");
            }
        }
    }

    public RetryPolicy getRetryPolicy(String name) {
        return retryMap.get(name.toUpperCase(Locale.ROOT));
    }
}
