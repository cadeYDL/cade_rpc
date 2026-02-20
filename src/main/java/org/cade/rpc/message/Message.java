package org.cade.rpc.message;

import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Data
public class Message {
    public static final byte[] Magic = "cade".getBytes();
    public static final byte[] Version = "10.00.00".getBytes();

    private byte[] magic;

    private byte[] version;

    private byte messageType;

    private byte serializerAndCompress;

    private byte[] payload;

    private static final Map<Class<?>, MessageType> CLASS_CACHE = new HashMap<>();

    private static final Map<Integer, MessageType> CODE_CACHE = new HashMap<>();

    static {
        for (MessageType type : MessageType.values()) {
            if (CLASS_CACHE.put(type.getMessageClass(), type) != null) {
                throw new IllegalArgumentException(type + " messageType already exists in CLASS_CACHE");
            }
            if (CODE_CACHE.put((int) type.getCode(), type) != null) {
                throw new IllegalArgumentException(type + " messageType already exists in CODE_CACHE");
            }
        }
    }

    public static MessageType getMessageType(Class<?> messageClass) {
        return CLASS_CACHE.get(messageClass);
    }

    public static MessageType getMessageTypeFromCode(int code) {
        return CODE_CACHE.get(code);
    }

    public enum MessageType {
        REQUEST(1, Request.class),
        RESPONSE(2, Response.class),
        HEARTBEAT_REQUEST(3, HeartbeatRequest.class),
        HEARTBEAT_RESPONSE(4, HeartbeatResponse.class);

        @Getter
        private final Class<?> messageClass;

        @Getter
        private final byte code;

        MessageType(int code, Class<?> messageClass) {
            this.code = (byte) code;
            this.messageClass = messageClass;
        }
    }
}
