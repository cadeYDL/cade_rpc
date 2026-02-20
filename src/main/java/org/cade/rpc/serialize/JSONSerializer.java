package org.cade.rpc.serialize;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;

import java.nio.charset.StandardCharsets;

public class JSONSerializer implements Serializer{
    @Override
    public byte[] serialize(Object obj) {
        return JSONObject.toJSONString(obj).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        String jsonStr = new String(bytes, StandardCharsets.UTF_8);
        return JSONObject.parseObject(jsonStr, clazz, JSONReader.Feature.SupportClassForName);
    }
}
