package org.cade.rpc.loadbalance;

import org.cade.rpc.register.Metadata;

import java.util.List;
import java.util.Random;

public class RandomLoadBalancer implements LoadBalancer {
    private final Random random = new Random();
    @Override
    public Metadata select(List<Metadata> metadataList) {
        if(metadataList.isEmpty()){
            return null;
        }
        int inx = random.nextInt(0,metadataList.size());
        return metadataList.get(inx);
    }
}
