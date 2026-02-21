package org.cade.rpc.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 基础类型工具类
 * <p>
 * 用于判断和处理 Java 基础类型及其包装类型
 */
public class BaseType {

    /**
     * 基础类型及其包装类型集合
     */
    private static final Set<Class<?>> BASE_TYPES = new HashSet<>();

    /**
     * 类型名称到 Class 对象的映射
     */
    private static final Map<String, Class<?>> TYPE_NAME_MAP = new HashMap<>();

    static {
        // 基本类型
        BASE_TYPES.add(int.class);
        BASE_TYPES.add(long.class);
        BASE_TYPES.add(short.class);
        BASE_TYPES.add(byte.class);
        BASE_TYPES.add(boolean.class);
        BASE_TYPES.add(char.class);
        BASE_TYPES.add(float.class);
        BASE_TYPES.add(double.class);

        // 包装类型
        BASE_TYPES.add(Integer.class);
        BASE_TYPES.add(Long.class);
        BASE_TYPES.add(Short.class);
        BASE_TYPES.add(Byte.class);
        BASE_TYPES.add(Boolean.class);
        BASE_TYPES.add(Character.class);
        BASE_TYPES.add(Float.class);
        BASE_TYPES.add(Double.class);

        // String 也视为基础类型
        BASE_TYPES.add(String.class);

        // 基本类型名称映射
        TYPE_NAME_MAP.put("int", int.class);
        TYPE_NAME_MAP.put("long", long.class);
        TYPE_NAME_MAP.put("short", short.class);
        TYPE_NAME_MAP.put("byte", byte.class);
        TYPE_NAME_MAP.put("boolean", boolean.class);
        TYPE_NAME_MAP.put("char", char.class);
        TYPE_NAME_MAP.put("float", float.class);
        TYPE_NAME_MAP.put("double", double.class);

        // 包装类型名称映射
        TYPE_NAME_MAP.put("Integer", Integer.class);
        TYPE_NAME_MAP.put("Long", Long.class);
        TYPE_NAME_MAP.put("Short", Short.class);
        TYPE_NAME_MAP.put("Byte", Byte.class);
        TYPE_NAME_MAP.put("Boolean", Boolean.class);
        TYPE_NAME_MAP.put("Character", Character.class);
        TYPE_NAME_MAP.put("Float", Float.class);
        TYPE_NAME_MAP.put("Double", Double.class);

        // String 类型映射
        TYPE_NAME_MAP.put("String", String.class);
        TYPE_NAME_MAP.put("java.lang.String", String.class);
    }

    /**
     * 判断给定的类是否为基础类型（包括包装类型和 String）
     *
     * @param clazz 待判断的类
     * @return true 表示是基础类型，false 表示不是
     */
    public static boolean is(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return BASE_TYPES.contains(clazz);
    }

    /**
     * 根据类型名称获取对应的 Class 对象
     * <p>
     * 支持的类型名称：
     * <ul>
     *   <li>基本类型：int, long, short, byte, boolean, char, float, double</li>
     *   <li>包装类型：Integer, Long, Short, Byte, Boolean, Character, Float, Double</li>
     *   <li>String 类型：String, java.lang.String</li>
     * </ul>
     *
     * @param typeName 类型名称
     * @return 对应的 Class 对象，如果不是基础类型则返回 null
     */
    public static Class<?> getClass(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        return TYPE_NAME_MAP.get(typeName);
    }

    /**
     * 判断给定的类型名称是否为基础类型
     *
     * @param typeName 类型名称
     * @return true 表示是基础类型，false 表示不是
     */
    public static boolean is(String typeName) {
        return getClass(typeName) != null;
    }
}
