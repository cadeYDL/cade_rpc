package org.cade.rpc.serialize;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianOutput;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j(topic ="hesson_serializer")
public class HessonSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) {
        try{
            ByteArrayOutputStream oos = new ByteArrayOutputStream();
            Hessian2Output hessianOutput = new Hessian2Output();
            hessianOutput.writeObject(obj);
            hessianOutput.flush();
            return oos.toByteArray();
        }catch (Exception e){
            log.error("hesson serializer fail {}",obj.getClass().getName(),e);
            return new byte[0];
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try(ByteArrayInputStream ois = new ByteArrayInputStream(bytes)){
            Hessian2Input hessianOutput = new Hessian2Input(ois);
            return (T)hessianOutput.readObject();
        }catch (Exception e){
            log.error("hesson deserialize fail {}",clazz.getName(),e);
            return null;
        }

    }

    @Override
    public int code() {
        return 1;
    }

    @Override
    public String getName() {
        return "hesson";
    }
}
