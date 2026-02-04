package org.cade.rpc.provider;

public class ProviderApp {
    public static void main(String[] args) {
        new ProviderServer(10086).start();
    }
}
