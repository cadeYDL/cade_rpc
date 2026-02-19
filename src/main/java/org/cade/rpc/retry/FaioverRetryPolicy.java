package org.cade.rpc.retry;

import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.register.Metadata;

import java.util.ArrayList;
import java.util.List;

public class FaioverRetryPolicy extends RetrySame {
    private List<Metadata> metadataList = null;
    @Override
    protected Metadata getService(RetryContext retryContext,Metadata failService) {
        if (metadataList==null){
            metadataList = new ArrayList<>(retryContext.getAllService());
        }
        metadataList.remove(failService);
        if (metadataList.isEmpty()){
            throw new RPCException("no service");
        }
        return retryContext.getLoadBalancer().select(metadataList);
    }
}
