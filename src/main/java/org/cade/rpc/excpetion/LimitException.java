package org.cade.rpc.excpetion;

public class LimitException extends RPCException {
    public LimitException(String message) {
        super(message);
    }
}
