package io.github.seraphina.nyx.client.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.seraphina.nyx.client.value.AbstractValue;

public class StringValue extends AbstractValue<String> {
    public StringValue(String name, String defaultValue, Dependency dependency) {
        super(name, defaultValue, dependency);
    }

    public StringValue(String name, String defaultValue) {
        this(name, defaultValue, Dependency.ALWAYS_TRUE);
    }

    @Override
    protected JsonElement writeValue() {
        return new JsonPrimitive(value);
    }

    @Override
    protected String readValue(JsonElement element) {
        return element.getAsString();
    }

    @Override
    protected String getType() {
        return "string";
    }
}
