package org.cade.rpc.spi;

public interface Extension {
    String getName();

    default int code(){
        return -1;
    }

}
