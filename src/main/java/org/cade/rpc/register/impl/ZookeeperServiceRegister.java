package org.cade.rpc.register.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.RegistryConfig;
import org.cade.rpc.register.ServiceRegister;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Slf4j(topic = "zookeeper_service_register")
public class ZookeeperServiceRegister implements Closeable, ServiceRegister {
    private static final String BASE_PATH = "/cade_rpc";
    private final CuratorFramework curatorFramework;
    private final ServiceDiscovery<Metadata> discovery;

    public ZookeeperServiceRegister(RegistryConfig config) throws Exception {
        curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(config.getConnectString())
                .sessionTimeoutMs(30000)
                .connectionTimeoutMs(3000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        curatorFramework.start();

        discovery = ServiceDiscoveryBuilder.builder(Metadata.class)
                .client(curatorFramework)
                .basePath(BASE_PATH)
                .serializer(new JsonInstanceSerializer<>(Metadata.class))
                .build();
        discovery.start();
    }

    public Collection<String> getAllServiceNames() throws Exception {
        return discovery.queryForNames();
    }

    @Override
    public void close() throws IOException {
        try {
            if (discovery != null) {
                discovery.close();
            }
        } catch (IOException e) {
            log.error("Failed to close discovery", e);
        }
        if (curatorFramework != null) {
            curatorFramework.close();
        }
    }

    public static void main(String[] args) throws Exception {
        RegistryConfig config = new RegistryConfig();
        config.setConnectString("192.168.139.120:2181");
        try (ZookeeperServiceRegister zk = new ZookeeperServiceRegister(config)) {
            // 注册服务
            Metadata metadata = new Metadata();
            metadata.setServiceName("org.cade.rpc.api.Add");
            metadata.setHost("localhost");
            metadata.setPort(10086);
            zk.register(metadata);

            // 发现服务
            List<Metadata> services = zk.fetchServicelist("org.cade.rpc.api.Add");
            for (Metadata service : services) {
                log.info("Found service: {} at {}:{}",
                        service.getServiceName(), service.getHost(), service.getPort());
            }

            // 获取所有服务名
            Collection<String> allServices = zk.getAllServiceNames();
            log.info("All registered services: {}", allServices);
        }
    }

    @Override
    public void register(org.cade.rpc.register.Metadata metadata) {
        ServiceInstance<Metadata> instance = null;
        try {
            instance = ServiceInstance.<Metadata>builder()
                    .name(metadata.getServiceName())
                    .address(metadata.getHost())
                    .port(metadata.getPort())
                    .payload(metadata)
                    .build();
            discovery.registerService(instance);
            log.info("Registered service: {} at {}:{}", metadata.getServiceName(), metadata.getHost(), metadata.getPort());
        } catch (Exception e) {
            log.error("Failed to register service: {} at {}:{}", metadata.getServiceName(), metadata.getHost(), metadata.getPort());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unregister(Metadata metadata) {
        try {
            ServiceInstance<Metadata> instance = ServiceInstance.<Metadata>builder()
                    .name(metadata.getServiceName())
                    .address(metadata.getHost())
                    .port(metadata.getPort())
                    .build();
            discovery.unregisterService(instance);
            log.info("Unregistered service: {} at {}:{}",metadata.getServiceName(), metadata.getHost(), metadata.getPort());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public List<org.cade.rpc.register.Metadata> fetchServicelist(String serviceName) throws Exception {
        return discovery.queryForInstances(serviceName).
                stream().map(ServiceInstance::getPayload).toList();

    }
}
