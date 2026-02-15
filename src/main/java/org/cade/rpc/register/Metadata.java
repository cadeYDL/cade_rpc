package org.cade.rpc.register;

import lombok.Data;

@Data
public class Metadata {
    private String serviceName;
    private String host;
    private int port;
}
