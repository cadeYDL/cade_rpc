package org.cade.rpc.comsumer;

import lombok.Data;
import org.cade.rpc.register.RegistryConfig;

@Data
public class ConsumerProperties {
    private Integer workThreadNum = 4;
    private Integer connectTimeoutMS = 3000;
    private Integer requestTimeoutMS = 3000;
    private String loadBalancePolicy = "random";

    private RegistryConfig registryConfig = new RegistryConfig();
}
