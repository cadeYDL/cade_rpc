package org.cade.rpc.excpetion;

public class RPCException extends RuntimeException {
    public RPCException(String message) {
        super(message);
    }

    public RPCException(String createMockObjectFail, Exception e) {
        super(createMockObjectFail, e);
    }

    public boolean retry() {
        return false;
    }
}
