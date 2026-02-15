package org.cade.rpc.register;

import java.util.List;

public interface ServiceRegister {
    void register(Metadata metadata);
    void unregister(Metadata metadata);
    List<Metadata> fetchServicelist(String serviceName)throws Exception;
}
