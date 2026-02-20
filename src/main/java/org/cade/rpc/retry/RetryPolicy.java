package org.cade.rpc.retry;

import org.cade.rpc.message.Response;

public interface RetryPolicy {
    Response retry(RetryContext context) throws Exception;
}
