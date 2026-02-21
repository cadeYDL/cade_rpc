package org.cade.rpc.comsumer;

import org.cade.rpc.api.Add;
import org.cade.rpc.api.User;
import org.cade.rpc.register.RegistryConfig;
import org.cade.rpc.register.impl.ZookeeperServiceRegister;
import org.cade.rpc.serialize.JSONSerializer;
import org.cade.rpc.serialize.Serializer;

import java.nio.charset.StandardCharsets;

public class ConsuerApp  {
    public static void main(String[] args) throws Exception {
        ConsumerProperties properties = new ConsumerProperties();
        properties.getRegistryConfig().setRegistryType("zookeeper");
        properties.getRegistryConfig().setConnectString("192.168.139.120:2181");
        ConsumerProxyFactory factory = new ConsumerProxyFactory(properties);
        Add addConsumer = factory.getConsumerProxy(Add.class);
        System.out.println(addConsumer.add(12,2));
        System.out.println(addConsumer.mul(10,2));

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
        obj =  consumer.$invoke(Add.class.getName(),"addUser",new String[]{"org.cade.rpc.api.User","org.cade.rpc.api.User"},new Object[]{u1str,u2str});
        System.out.println(obj);

    }

}
