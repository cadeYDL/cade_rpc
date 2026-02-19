package org.cade.rpc.fallback;

import lombok.Data;
import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.metrics.RPCCallMetrics;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CacheFallback implements Fallback{
    private static final Object NULL_OBJ = new Object();

    private final Map<InvokeKey,Object> rpcResultCache = new ConcurrentHashMap<>();

    @Override
    public Object fallback(RPCCallMetrics metrics) {
        InvokeKey key = new InvokeKey(metrics.getMethod(),metrics.getArgs());
        if (!rpcResultCache.containsKey(key)){
            throw new RPCException("result not exits");
        }
        Object res = rpcResultCache.get(key);
        if(res==NULL_OBJ){
            res = null;
        }
        return res;
    }

    @Override
    public void recordMetrics(RPCCallMetrics metrics) {
        if (!metrics.isComplete()||metrics.getThrowable()!=null){
            return;
        }
        InvokeKey key = new InvokeKey(metrics.getMethod(),metrics.getArgs());
        Object result = metrics.getResult();
        if(result==null){
            result = NULL_OBJ;
        }
        rpcResultCache.put(key,result);
    }

    @Data
    private class InvokeKey{
        final Method method;
        final  Object[] args;

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            InvokeKey key = (InvokeKey) object;
            return Objects.equals(method, key.method) && Objects.deepEquals(args, key.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(args));
        }
    }
}
