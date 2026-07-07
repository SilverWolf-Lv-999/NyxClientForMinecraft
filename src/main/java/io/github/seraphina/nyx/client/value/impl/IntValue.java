package io.github.seraphina.nyx.client.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.seraphina.nyx.client.value.AbstractValue;

public class IntValue extends AbstractValue<Integer> {
    private final int min;
    private final int max;
    private final int step;
    private final boolean percentageMode;

    public IntValue(String name, int defaultValue, int min, int max, int step, Dependency dependency, boolean percentageMode) {
        super(name, defaultValue, dependency);
        this.min = min;
        this.max = max;
        this.step = step;
        this.percentageMode = percentageMode;
        this.value = clamp(defaultValue);
    }

    @Override
    public void setValue(Integer value) {
        super.setValue(clamp(value));
    }

    @Override
    public void setValueSilently(Integer value) {
        super.setValueSilently(clamp(value));
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public int getStep() {
        return step;
    }

    public boolean isPercentageMode() {
        return percentageMode;
    }

    private int clamp(Integer value) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected JsonElement writeValue() {
        return new JsonPrimitive(value);
    }

    @Override
    protected Integer readValue(JsonElement element) {
        return element.getAsInt();
    }

    @Override
    protected String getType() {
        return "int";
    }
}
