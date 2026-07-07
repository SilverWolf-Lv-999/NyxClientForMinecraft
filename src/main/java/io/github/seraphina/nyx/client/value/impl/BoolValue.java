package io.github.seraphina.nyx.client.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.seraphina.nyx.client.value.AbstractValue;

import java.util.function.Consumer;

public class BoolValue extends AbstractValue<Boolean> {
    public BoolValue(String name, boolean defaultValue, Dependency dependency, Consumer<Boolean> onChanged) {
        super(name, defaultValue, dependency, onChanged);
    }

    public BoolValue(String name, boolean defaultValue, Dependency dependency) {
        this(name, defaultValue, dependency, null);
    }

    public BoolValue(String name, boolean defaultValue) {
        this(name, defaultValue, Dependency.ALWAYS_TRUE, null);
    }

    @Override
    protected JsonElement writeValue() {
        return new JsonPrimitive(value);
    }

    @Override
    protected Boolean readValue(JsonElement element) {
        return element.getAsBoolean();
    }

    @Override
    protected String getType() {
        return "bool";
    }
}
