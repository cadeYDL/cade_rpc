package org.cade.rpc.excpetion;

public class RPCException extends RuntimeException {
    public RPCException(String message) {
        super(message);
    }

    public boolean retry() {
        return false;
    }
}
