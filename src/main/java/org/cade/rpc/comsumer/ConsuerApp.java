package org.cade.rpc.comsumer;

import org.cade.rpc.api.Add;
import org.cade.rpc.register.RegistryConfig;
import org.cade.rpc.register.impl.ZookeeperServiceRegister;

public class ConsuerApp  {
    public static void main(String[] args) throws Exception {
        ConsumerProperties properties = new ConsumerProperties();
        properties.getRegistryConfig().setRegistryType("zookeeper");
        properties.getRegistryConfig().setConnectString("192.168.139.120:2181");
        ConsumerProxyFactory factory = new ConsumerProxyFactory(properties);
        Add addConsumer = factory.getConsumerProxy(Add.class);
        for(int i = 0; i < 100; i++){
            try{
                System.out.println(addConsumer.add(12,2));
                System.out.println(addConsumer.mul(10,2));
            }catch (Exception e){
                System.out.println(e);
            }

        }

    }

}
