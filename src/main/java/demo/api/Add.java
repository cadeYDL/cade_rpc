package demo.api;

import demo.interceptor.LoggingInterceptor;
import demo.interceptor.PermissionInterceptor;
import org.cade.rpc.fallback.RPCFallback;
import org.cade.rpc.interceptor.InjectInterceptor;

@RPCFallback(implement = ConsumerAdd.class)
@InjectInterceptor({LoggingInterceptor.class})
public interface Add {
    int add(int a, int b);
    int mul(int a, int b);
    @InjectInterceptor(PermissionInterceptor.class)
    User addUser(User user1,User user2);

}
