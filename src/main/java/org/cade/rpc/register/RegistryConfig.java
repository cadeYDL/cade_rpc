package org.cade.rpc.register;

import lombok.Data;

@Data
public class RegistryConfig {
    private String ConnectString;
    private String RegistryType;
}
