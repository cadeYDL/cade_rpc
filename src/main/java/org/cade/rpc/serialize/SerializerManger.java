package org.cade.rpc.serialize;

import java.util.HashMap;
import java.util.Map;

public class SerializerManger {
    private final Map<Integer, Serializer> serializerMap = new HashMap<>();

    public SerializerManger(){
        init();
    }

    public Serializer getSerializer(int serializeType) {
        return serializerMap.get(serializeType);
    }

    private void init(){
        for(Serializer.SerializerType type:Serializer.SerializerType.values()){
            serializerMap.put(type.getTypeCode(),type.getSerializer());
        }
    }
}
