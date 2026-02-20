package org.cade.rpc.serialize;

import lombok.Getter;

public interface Serializer {
    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> clazz);

    enum SerializerType {
        JSON(0,new JSONSerializer()),
        HESSIAN(1,new HessonSerializer());

        @Getter
        private final int typeCode;
        @Getter
        private final Serializer serializer;
        SerializerType(int typeCode,Serializer serializer) {
            this.typeCode = typeCode;
            this.serializer = serializer;
        }

        static SerializerType getSerializerTypeFromCode(int code){
            switch (code){
            case 0:
                return JSON;
            case 1:
                return HESSIAN;
            default:
                throw new IllegalArgumentException("No such serializer type");
            }
        }

    }
}
