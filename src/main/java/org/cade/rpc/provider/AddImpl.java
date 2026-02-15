package org.cade.rpc.provider;

import org.cade.rpc.api.Add;

import java.util.concurrent.TimeUnit;

public class AddImpl implements Add {
    @Override
    public int add(int a, int b) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return a+b;
    }

    @Override
    public int mul(int a, int b) {
        return a-b;
    }
}
