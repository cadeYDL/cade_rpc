package org.cade.rpc.limit;

import java.util.concurrent.Semaphore;

public class ConcurrencyLimiter implements Limiter{
    private final Semaphore semaphore;
    public ConcurrencyLimiter(int LimitNum){
         this.semaphore = new Semaphore(LimitNum);
    }

    @Override
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    @Override
    public void release() {
        this.semaphore.release();
    }

    @Override
    public void release(int remain) {
        this.semaphore.release(remain);
    }
}
