package io.github.seraphina.nyx.client.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.seraphina.nyx.client.value.AbstractValue;

import java.awt.Color;

public class ColorValue extends AbstractValue<Color> {
    private final boolean allowAlpha;

    public ColorValue(String name, Color defaultValue, boolean allowAlpha, Dependency dependency) {
        super(name, normalize(defaultValue, allowAlpha), dependency);
        this.allowAlpha = allowAlpha;
    }

    public ColorValue(String name, Color defaultValue, Dependency dependency) {
        this(name, defaultValue, true, dependency);
    }

    public ColorValue(String name, Color defaultValue) {
        this(name, defaultValue, true, Dependency.ALWAYS_TRUE);
    }

    @Override
    public void setValue(Color value) {
        super.setValue(normalize(value, allowAlpha));
    }

    @Override
    public void setValueSilently(Color value) {
        super.setValueSilently(normalize(value, allowAlpha));
    }

    public boolean isAllowAlpha() {
        return allowAlpha;
    }

    private static Color normalize(Color color, boolean allowAlpha) {
        if (color == null) {
            return new Color(255, 255, 255, allowAlpha ? 255 : 255);
        }
        return allowAlpha ? color : new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    protected JsonElement writeValue() {
        JsonObject object = new JsonObject();
        object.addProperty("red", value.getRed());
        object.addProperty("green", value.getGreen());
        object.addProperty("blue", value.getBlue());
        object.addProperty("alpha", value.getAlpha());
        return object;
    }

    @Override
    protected Color readValue(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return normalize(new Color(element.getAsInt(), allowAlpha), allowAlpha);
        }

        JsonObject object = element.getAsJsonObject();
        int red = object.has("red") ? clampChannel(object.get("red").getAsInt()) : defaultValue.getRed();
        int green = object.has("green") ? clampChannel(object.get("green").getAsInt()) : defaultValue.getGreen();
        int blue = object.has("blue") ? clampChannel(object.get("blue").getAsInt()) : defaultValue.getBlue();
        int alpha = allowAlpha && object.has("alpha") ? clampChannel(object.get("alpha").getAsInt()) : 255;
        return new Color(red, green, blue, alpha);
    }

    @Override
    protected String getType() {
        return "color";
    }
}
