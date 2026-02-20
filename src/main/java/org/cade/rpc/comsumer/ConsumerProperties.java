package org.cade.rpc.comsumer;

import lombok.Data;
import org.cade.rpc.register.RegistryConfig;

@Data
public class ConsumerProperties {
    private Integer workThreadNum = 4;
    private Integer connectTimeoutMS = 3000;
    private Integer requestTimeoutMS = 3000;
    private Integer functionTimeoutMS = 10000;
    private String loadBalancePolicy = "random";
    private String retryPolicy = "same";
    private int rpcPreSecond = 100000;
    private int rpcPreChannelSecond = 1000000;
    private double slowRequestBreakRatio = 0.5;
    private String serializer = "json";
    private String compress = "zstd";

    private RegistryConfig registryConfig = new RegistryConfig();
}
