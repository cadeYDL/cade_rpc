package org.cade.rpc.provider;

import lombok.Data;
import org.cade.rpc.register.RegistryConfig;

@Data
public class ProviderProperties {
    private String host;
    private int port;
    private RegistryConfig registryConfig=new RegistryConfig();
    private Integer workerThreadNumber = 4;
    private int globelMaxRequest = 1000000;
    private int preConsumerMax = 1000000;
    private String serializer = "json";
    private String compress = "gzip";
}
