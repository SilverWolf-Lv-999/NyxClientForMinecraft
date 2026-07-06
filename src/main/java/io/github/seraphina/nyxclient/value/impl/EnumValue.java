package io.github.seraphina.nyxclient.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.seraphina.nyxclient.value.AbstractValue;

import java.util.Objects;
import java.util.function.Consumer;

public class EnumValue<E extends Enum<E>> extends AbstractValue<E> {
    private final E[] constants;

    public EnumValue(String name, E defaultValue, Dependency dependency, Consumer<E> onChanged) {
        super(name, Objects.requireNonNull(defaultValue), dependency, onChanged);
        constants = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public EnumValue(String name, E defaultValue, Dependency dependency) {
        this(name, defaultValue, dependency, null);
    }

    public EnumValue(String name, E defaultValue) {
        this(name, defaultValue, Dependency.ALWAYS_TRUE, null);
    }

    public boolean is(E enumValue) {
        return value == enumValue;
    }

    public boolean is(String name) {
        return value.name().equalsIgnoreCase(name) || value.toString().equalsIgnoreCase(name);
    }

    public void setMode(String mode) {
        setValue(parseMode(mode));
    }

    public void setModeSilently(String mode) {
        setValueSilently(parseMode(mode));
    }

    public void setMode(E mode) {
        setValue(mode);
    }

    public int getModeIndex() {
        for (int i = 0; i < constants.length; i++) {
            if (constants[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public E[] getModes() {
        return constants;
    }

    private E parseMode(String mode) {
        for (E constant : constants) {
            if (constant.name().equalsIgnoreCase(mode) || constant.toString().equalsIgnoreCase(mode)) {
                return constant;
            }
        }
        return defaultValue;
    }

    @Override
    protected JsonElement writeValue() {
        return new JsonPrimitive(value.name());
    }

    @Override
    protected E readValue(JsonElement element) {
        return parseMode(element.getAsString());
    }

    @Override
    protected String getType() {
        return "enum";
    }
}
