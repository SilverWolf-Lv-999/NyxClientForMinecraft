package io.github.seraphina.nyx.client.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.seraphina.nyx.client.value.AbstractValue;

public class DoubleValue extends AbstractValue<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final boolean percentageMode;

    public DoubleValue(String name, double defaultValue, double min, double max, double step, Dependency dependency, boolean percentageMode) {
        super(name, defaultValue, dependency);
        this.min = min;
        this.max = max;
        this.step = step;
        this.percentageMode = percentageMode;
        this.value = clamp(defaultValue);
    }

    @Override
    public void setValue(Double value) {
        super.setValue(clamp(value));
    }

    @Override
    public void setValueSilently(Double value) {
        super.setValueSilently(clamp(value));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public boolean isPercentageMode() {
        return percentageMode;
    }

    private double clamp(Double value) {
        if (value == null || value.isNaN()) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected JsonElement writeValue() {
        return new JsonPrimitive(value);
    }

    @Override
    protected Double readValue(JsonElement element) {
        return element.getAsDouble();
    }

    @Override
    protected String getType() {
        return "double";
    }
}
