package demo;

import demo.api.Add;
import demo.api.User;
import demo.interceptor.CacheInterceptor;
import demo.interceptor.LoggingInterceptor;
import demo.interceptor.MetricsInterceptor;
import org.cade.rpc.comsumer.ConsumerProperties;
import org.cade.rpc.comsumer.ConsumerProxyFactory;
import org.cade.rpc.comsumer.GenericConsumer;
import org.cade.rpc.interceptor.InterceptorConfig;
import org.cade.rpc.serialize.JSONSerializer;
import org.cade.rpc.serialize.Serializer;

import java.nio.charset.StandardCharsets;

public class ConsuerApp  {
    public static void main(String[] args) throws Exception {
        ConsumerProperties properties = new ConsumerProperties();
        properties.getRegistryConfig().setRegistryType("zookeeper");
        properties.getRegistryConfig().setConnectString("192.168.139.120:2181");
        ConsumerProxyFactory factory = new ConsumerProxyFactory(properties);

        // 示例 1: 获取消费者代理但不使用拦截器（向后兼容）
        Add addConsumer = factory.getConsumerProxy(Add.class);
        System.out.println(addConsumer.add(12,2));
        System.out.println(addConsumer.mul(10,2));

        // 示例 2: 获取消费者代理并使用拦截器
        // 取消下面的注释以启用拦截器：
        InterceptorConfig config = new InterceptorConfig();

        // 添加接口级拦截器（应用于所有方法）
        config.addInterfaceInterceptor(new LoggingInterceptor());
        config.addInterfaceInterceptor(new MetricsInterceptor());
        config.addInterfaceInterceptor(new CacheInterceptor());

        // 使用拦截器获取代理
        Add addConsumerWithInterceptors = factory.getConsumerProxy(Add.class, config);
        System.out.println("第一次调用（将被缓存）: " + addConsumerWithInterceptors.add(12,2));
        System.out.println("第二次调用（来自缓存）: " + addConsumerWithInterceptors.add(12,2));

        User u1 = new User();
        u1.setName("c");
        u1.setAge(10);

        User u2 = new User();
        u2.setName("b");
        u2.setAge(20);

        System.out.println(addConsumer.addUser(u1,u2));

        GenericConsumer consumer = factory.getConsumerProxy(GenericConsumer.class);
        Object obj =  consumer.$invoke(Add.class.getName(),"add",new String[]{"int","int"},new Object[]{12,2});
        System.out.println(obj);

        Serializer serializer = new JSONSerializer();
        String u1str = new String(serializer.serialize(u1), StandardCharsets.UTF_8);
        String u2str = new String(serializer.serialize(u2), StandardCharsets.UTF_8);
        obj =  consumer.$invoke(Add.class.getName(),"addUser",new String[]{"demo.api.User","demo.api.User"},new Object[]{u1str,u2str});
        System.out.println(obj);



    }

}
