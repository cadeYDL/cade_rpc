package org.cade.rpc.register;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.register.impl.EtcdServiceRegister;
import org.cade.rpc.register.impl.ZookeeperServiceRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "default_service_register")
public class DefaultServiceRegister implements ServiceRegister{
    private final Map<String,List<Metadata>> cache;
    private final ServiceRegister delegate;
    public DefaultServiceRegister(RegistryConfig config) throws Exception {
        this.cache = new ConcurrentHashMap<>();
        this.delegate = getServiceRegistery(config);
    }
    public static ServiceRegister getServiceRegistery(RegistryConfig config) throws Exception {
        switch (config.getRegistryType()){
            case "zookeeper":
                return new ZookeeperServiceRegister(config);
            case "redis":
                return new EtcdServiceRegister(config);
            default:
                throw new RuntimeException("not support registry type:"+config.getRegistryType());
        }
    }

    @Override
    public void register(Metadata metadata) {
        this.delegate.register(metadata);
    }

    @Override
    public void unregister(Metadata metadata) {
        this.delegate.unregister(metadata);
    }

    @Override
    public List<Metadata> fetchServicelist(String serviceName) throws Exception {
        try {
            List<Metadata> list = this.delegate.fetchServicelist(serviceName);
            cache.put(serviceName,list);
            return list;
        }catch (Exception e){
            log.error("{} register search {} error:{}",delegate.getClass().getSimpleName(),serviceName,e);
            return cache.getOrDefault(serviceName,new ArrayList<>());
        }
    }
}
