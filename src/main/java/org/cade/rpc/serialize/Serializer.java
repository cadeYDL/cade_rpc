package org.cade.rpc.serialize;

import org.cade.rpc.spi.Extension;

public interface Serializer extends Extension {
    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
