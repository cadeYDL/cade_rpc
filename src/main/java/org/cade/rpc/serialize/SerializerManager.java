package org.cade.rpc.serialize;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

public class SerializerManager {
    private final Map<Integer, Serializer> serializerMap = new HashMap<>();
    private final Map<String, Serializer> serializerNameMap = new HashMap<>();

    public SerializerManager(){
        init();
    }

    public Serializer getSerializer(int serializeType) {
        return serializerMap.get(serializeType);
    }

    public Serializer getSerializer(String name){
        return serializerNameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init(){
        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);
        for(Serializer serializer:loader){

            if(serializer.code()>=16){
                throw new IllegalArgumentException("serializeType must be less than 16");
            }
            if(serializerMap.put(serializer.code(),serializer)!=null){
                throw new IllegalArgumentException("serializeType must be unique");
            }
            if(serializerNameMap.put(serializer.getName().toUpperCase(Locale.ROOT),serializer)!=null){
                throw new IllegalArgumentException("serializeName must be unique");
            }

        }
    }
}
