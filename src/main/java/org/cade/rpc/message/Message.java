package org.cade.rpc.message;

import lombok.Data;
import lombok.Getter;

@Data
public class Message {
    public static final byte[] Magic = "cade".getBytes();
    public static final byte[] Version = "10.00.00".getBytes();

    private byte[] magic;

    private byte[] version;

    private byte messageType;

    private byte[] payload;

    public enum MessageType {
        REQUEST(1), RESPONSE(2);
        @Getter
        private final byte code;
        MessageType(int code) {
            this.code = (byte) code;
        }

    }
}
