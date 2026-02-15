package org.cade.rpc.register.impl;

import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.RegistryConfig;
import org.cade.rpc.register.ServiceRegister;

import java.util.List;

public class EtcdServiceRegister implements ServiceRegister {
    public EtcdServiceRegister(RegistryConfig config){}


    @Override
    public void register(Metadata metadata) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregister(Metadata metadata) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Metadata> fetchServicelist(String serviceName) throws Exception {
        throw new UnsupportedOperationException();
    }
}
