package io.github.seraphina.nyx.client.utility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ClassUtility {
    private static final Map<String, Class<?>> CLASS_CAGE = new HashMap<>();
    private static final Map<String, Method> METHOD_CAGE = new HashMap<>();
    private static final Map<String, Field> FIELD_CAGE = new HashMap<>();

    public static Class<?> getClass(String className) {
        if (CLASS_CAGE.containsKey(className)) {
            return CLASS_CAGE.get(className);
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
            CLASS_CAGE.put(className, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method getMethod(String methodName, Class<?> clazz) {
        if (METHOD_CAGE.containsKey(methodName)) {
            return METHOD_CAGE.get(methodName);
        }
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                METHOD_CAGE.put(methodName, method);
                return method;
            }
        }
        return null;
    }

    public static Field getField(String fieldName, Class<?> clazz) {
        if (FIELD_CAGE.containsKey(fieldName)) {
            return FIELD_CAGE.get(fieldName);
        }
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().equals(fieldName)) {
                FIELD_CAGE.put(fieldName, field);
                return field;
            }
        }
        return null;
    }
}
