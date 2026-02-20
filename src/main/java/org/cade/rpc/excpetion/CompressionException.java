package org.cade.rpc.excpetion;

/**
 * 压缩/解压过程中发生的异常。
 */
public class CompressionException extends RPCException {

    public CompressionException(String message) {
        super(message);
    }

    public CompressionException(String message, Exception cause) {
        super(message, cause);
    }
}
