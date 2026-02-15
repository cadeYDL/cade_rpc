package org.cade.rpc.provider;

import org.cade.rpc.api.Add;
import org.cade.rpc.register.RegistryConfig;


public class ProviderApp {
    public static void main(String[] args) throws Exception {
        ProviderProperties providerProperties = new ProviderProperties();
        providerProperties.setHost("127.0.0.1");
        providerProperties.setPort(10086);
        providerProperties.getRegistryConfig().setConnectString("192.168.139.120:2181");
        providerProperties.getRegistryConfig().setRegistryType("zookeeper");
        ProviderServer p  = new ProviderServer(providerProperties);
        p.register(Add.class, new AddImpl());
        p.start();
    }
}
