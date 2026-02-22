package org.cade.rpc.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于将拦截器注入到服务接口或方法上的注解。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface InjectInterceptor {
    /**
     * 要注入的拦截器类数组。
     * @return 拦截器类。
     */
    Class<? extends Interceptor>[] value();
}
