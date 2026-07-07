package io.github.seraphina.nyx.client.value.impl;

public class KeyBindValue extends IntValue {
    public KeyBindValue(String name, int defaultValue, Dependency dependency) {
        super(name, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, dependency, false);
    }

    public KeyBindValue(String name, int defaultValue) {
        this(name, defaultValue, Dependency.ALWAYS_TRUE);
    }

    @Override
    protected String getType() {
        return "keybind";
    }
}
