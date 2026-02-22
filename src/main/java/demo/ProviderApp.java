package demo;

import demo.api.Add;
import demo.api.AddImpl;
import demo.interceptor.LoggingInterceptor;
import demo.interceptor.MetricsInterceptor;
import demo.interceptor.PermissionInterceptor;
import org.cade.rpc.interceptor.InterceptorConfig;
import org.cade.rpc.provider.ProviderProperties;
import org.cade.rpc.provider.ProviderServer;


public class ProviderApp {
    public static void main(String[] args) throws Exception {
        ProviderProperties providerProperties = new ProviderProperties();
        providerProperties.setHost("127.0.0.1");
        providerProperties.setPort(10088);
        providerProperties.getRegistryConfig().setConnectString("192.168.139.120:2181");
        providerProperties.getRegistryConfig().setRegistryType("zookeeper");
        ProviderServer p  = new ProviderServer(providerProperties);
//
//        // 示例 1: 注册服务但不使用拦截器（向后兼容）
//        p.register(Add.class, new AddImpl());

        // 示例 2: 注册服务并使用拦截器
        // 取消下面的注释以启用拦截器：
        InterceptorConfig config = new InterceptorConfig();

        // 添加接口级拦截器（应用于所有方法）
//        config.addInterfaceInterceptor(new LoggingInterceptor());

        // 添加方法级拦截器（仅应用于特定方法）
//        config.addMethodInterceptor("add", new PermissionInterceptor());

        // 使用拦截器注册
        p.register(Add.class, new AddImpl(), config);

        p.start();
    }
}
