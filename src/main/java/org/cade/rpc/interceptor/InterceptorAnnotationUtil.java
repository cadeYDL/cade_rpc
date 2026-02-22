package org.cade.rpc.interceptor;

import java.lang.reflect.Method;

/**
 * 拦截器注解工具类，用于解析服务接口或方法上的@InjectInterceptor注解，
 * 并将注解配置与编程式配置进行合并。
 */
public class InterceptorAnnotationUtil {

    /**
     * 解析给定接口上的@InjectInterceptor注解，构建InterceptorConfig。
     * 支持接口级别和方法级别的注解。
     *
     * @param interfaceClass 要解析的接口类。
     * @return 包含注解配置的InterceptorConfig实例。
     * @throws RuntimeException 如果拦截器实例化失败。
     */
    public static InterceptorConfig parseAnnotations(Class<?> interfaceClass) {
        InterceptorConfig config = new InterceptorConfig();

        // 1. 处理接口级别的注解
        InjectInterceptor interfaceAnnotation = interfaceClass.getAnnotation(InjectInterceptor.class);
        if (interfaceAnnotation != null) {
            for (Class<? extends Interceptor> interceptorClass : interfaceAnnotation.value()) {
                try {
                    config.addInterfaceInterceptor(interceptorClass.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("无法实例化拦截器: " + interceptorClass.getName(), e);
                }
            }
        }

        // 2. 处理方法级别的注解
        for (Method method : interfaceClass.getMethods()) {
            InjectInterceptor methodAnnotation = method.getAnnotation(InjectInterceptor.class);
            if (methodAnnotation != null) {
                for (Class<? extends Interceptor> interceptorClass : methodAnnotation.value()) {
                    try {
                        config.addMethodInterceptor(method.getName(), interceptorClass.getDeclaredConstructor().newInstance());
                    } catch (Exception e) {
                        throw new RuntimeException("无法实例化方法 " + method.getName() + " 的拦截器: " + interceptorClass.getName(), e);
                    }
                }
            }
        }
        return config;
    }

    /**
     * 合并编程式配置和注解配置的InterceptorConfig。
     * 如果任一配置为空，则返回另一个非空配置；如果都为空，则返回一个新的空配置。
     * 拦截器将按照添加顺序合并到新的InterceptorConfig中。
     *
     * @param programmaticConfig 编程式配置。
     * @param annotationConfig   注解配置。
     * @return 合并后的InterceptorConfig实例。
     */
    public static InterceptorConfig merge(InterceptorConfig programmaticConfig, InterceptorConfig annotationConfig) {
        if ((programmaticConfig == null || programmaticConfig.isEmpty()) && (annotationConfig == null || annotationConfig.isEmpty())) {
            return new InterceptorConfig();
        }
        if (programmaticConfig == null || programmaticConfig.isEmpty()) {
            return annotationConfig;
        }
        if (annotationConfig == null || annotationConfig.isEmpty()) {
            return programmaticConfig;
        }

        InterceptorConfig mergedConfig = new InterceptorConfig();

        // 添加编程式配置的拦截器
        programmaticConfig.getInterfaceInterceptors().forEach(mergedConfig::addInterfaceInterceptor);
        programmaticConfig.getMethodInterceptors().forEach((methodName, interceptors) ->
                interceptors.forEach(interceptor -> mergedConfig.addMethodInterceptor(methodName, interceptor))
        );

        // 添加注解配置的拦截器
        annotationConfig.getInterfaceInterceptors().forEach(mergedConfig::addInterfaceInterceptor);
        annotationConfig.getMethodInterceptors().forEach((methodName, interceptors) ->
                interceptors.forEach(interceptor -> mergedConfig.addMethodInterceptor(methodName, interceptor))
        );

        return mergedConfig;
    }

    /**
     * 仅解析给定类上的类级别@InjectInterceptor注解。
     *
     * @param clazz 要解析的类。
     * @return 包含类级别注解配置的InterceptorConfig实例。
     * @throws RuntimeException 如果拦截器实例化失败。
     */
    public static InterceptorConfig parseClassLevelAnnotations(Class<?> clazz) {
        InterceptorConfig config = new InterceptorConfig();
        InjectInterceptor classAnnotation = clazz.getAnnotation(InjectInterceptor.class);
        if (classAnnotation != null) {
            for (Class<? extends Interceptor> interceptorClass : classAnnotation.value()) {
                try {
                    config.addInterfaceInterceptor(interceptorClass.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("无法实例化拦截器: " + interceptorClass.getName(), e);
                }
            }
        }
        return config;
    }
}
