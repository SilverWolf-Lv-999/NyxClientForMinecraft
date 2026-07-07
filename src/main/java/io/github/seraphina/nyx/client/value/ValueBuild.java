package io.github.seraphina.nyx.client.value;

import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ButtonValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.KeyBindValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;

import java.awt.Color;
import java.util.function.Consumer;

public final class ValueBuild {
    private ValueBuild() {
    }

    private static <T extends AbstractValue<?>> T register(T value, Module owner) {
        owner.registerValue(value);
        return value;
    }

    public static ValueGroup settingGroup(String name, Module owner) {
        ValueGroup group = new ValueGroup(name);
        owner.registerValueGroup(group);
        return group;
    }

    public static ValueGroup valueGroup(String name, Module owner) {
        return settingGroup(name, owner);
    }

    public static BoolValue boolSetting(String name, boolean defaultValue, Module owner) {
        return boolSetting(name, defaultValue, AbstractValue.Dependency.ALWAYS_TRUE, null, owner);
    }

    public static BoolValue boolSetting(String name, boolean defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return boolSetting(name, defaultValue, dependency, null, owner);
    }

    public static BoolValue boolSetting(String name, boolean defaultValue, Consumer<Boolean> onChanged, Module owner) {
        return boolSetting(name, defaultValue, AbstractValue.Dependency.ALWAYS_TRUE, onChanged, owner);
    }

    public static BoolValue boolSetting(String name, boolean defaultValue, AbstractValue.Dependency dependency, Consumer<Boolean> onChanged, Module owner) {
        return register(new BoolValue(name, defaultValue, dependency, onChanged), owner);
    }

    public static BoolValue boolValue(String name, boolean defaultValue, Module owner) {
        return boolSetting(name, defaultValue, owner);
    }

    public static BoolValue boolValue(String name, boolean defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return boolSetting(name, defaultValue, dependency, owner);
    }

    public static BoolValue boolValue(String name, boolean defaultValue, Consumer<Boolean> onChanged, Module owner) {
        return boolSetting(name, defaultValue, onChanged, owner);
    }

    public static BoolValue boolValue(String name, boolean defaultValue, AbstractValue.Dependency dependency, Consumer<Boolean> onChanged, Module owner) {
        return boolSetting(name, defaultValue, dependency, onChanged, owner);
    }

    public static IntValue intSetting(String name, int defaultValue, int min, int max, int step, Module owner) {
        return intSetting(name, defaultValue, min, max, step, AbstractValue.Dependency.ALWAYS_TRUE, false, owner);
    }

    public static IntValue intSetting(String name, int defaultValue, int min, int max, int step, AbstractValue.Dependency dependency, Module owner) {
        return intSetting(name, defaultValue, min, max, step, dependency, false, owner);
    }

    public static IntValue intSetting(String name, int defaultValue, int min, int max, int step, AbstractValue.Dependency dependency, boolean percentageMode, Module owner) {
        return register(new IntValue(name, defaultValue, min, max, step, dependency, percentageMode), owner);
    }

    public static IntValue intValue(String name, int defaultValue, int min, int max, int step, Module owner) {
        return intSetting(name, defaultValue, min, max, step, owner);
    }

    public static IntValue intValue(String name, int defaultValue, int min, int max, int step, AbstractValue.Dependency dependency, Module owner) {
        return intSetting(name, defaultValue, min, max, step, dependency, owner);
    }

    public static IntValue intValue(String name, int defaultValue, int min, int max, int step, AbstractValue.Dependency dependency, boolean percentageMode, Module owner) {
        return intSetting(name, defaultValue, min, max, step, dependency, percentageMode, owner);
    }

    public static DoubleValue doubleSetting(String name, double defaultValue, double min, double max, double step, Module owner) {
        return doubleSetting(name, defaultValue, min, max, step, AbstractValue.Dependency.ALWAYS_TRUE, false, owner);
    }

    public static DoubleValue doubleSetting(String name, double defaultValue, double min, double max, double step, AbstractValue.Dependency dependency, Module owner) {
        return doubleSetting(name, defaultValue, min, max, step, dependency, false, owner);
    }

    public static DoubleValue doubleSetting(String name, double defaultValue, double min, double max, double step, AbstractValue.Dependency dependency, boolean percentageMode, Module owner) {
        return register(new DoubleValue(name, defaultValue, min, max, step, dependency, percentageMode), owner);
    }

    public static DoubleValue doubleValue(String name, double defaultValue, double min, double max, double step, Module owner) {
        return doubleSetting(name, defaultValue, min, max, step, owner);
    }

    public static DoubleValue doubleValue(String name, double defaultValue, double min, double max, double step, AbstractValue.Dependency dependency, Module owner) {
        return doubleSetting(name, defaultValue, min, max, step, dependency, owner);
    }

    public static DoubleValue doubleValue(String name, double defaultValue, double min, double max, double step, AbstractValue.Dependency dependency, boolean percentageMode, Module owner) {
        return doubleSetting(name, defaultValue, min, max, step, dependency, percentageMode, owner);
    }

    public static StringValue stringSetting(String name, String defaultValue, Module owner) {
        return stringSetting(name, defaultValue, AbstractValue.Dependency.ALWAYS_TRUE, owner);
    }

    public static StringValue stringSetting(String name, String defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return register(new StringValue(name, defaultValue, dependency), owner);
    }

    public static StringValue stringValue(String name, String defaultValue, Module owner) {
        return stringSetting(name, defaultValue, owner);
    }

    public static StringValue stringValue(String name, String defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return stringSetting(name, defaultValue, dependency, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumSetting(String name, E defaultValue, Module owner) {
        return enumSetting(name, defaultValue, AbstractValue.Dependency.ALWAYS_TRUE, null, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumSetting(String name, E defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return enumSetting(name, defaultValue, dependency, null, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumSetting(String name, E defaultValue, Consumer<E> onChanged, Module owner) {
        return enumSetting(name, defaultValue, AbstractValue.Dependency.ALWAYS_TRUE, onChanged, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumSetting(String name, E defaultValue, AbstractValue.Dependency dependency, Consumer<E> onChanged, Module owner) {
        return register(new EnumValue<>(name, defaultValue, dependency, onChanged), owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumValue(String name, E defaultValue, Module owner) {
        return enumSetting(name, defaultValue, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumValue(String name, E defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return enumSetting(name, defaultValue, dependency, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumValue(String name, E defaultValue, Consumer<E> onChanged, Module owner) {
        return enumSetting(name, defaultValue, onChanged, owner);
    }

    public static <E extends Enum<E>> EnumValue<E> enumValue(String name, E defaultValue, AbstractValue.Dependency dependency, Consumer<E> onChanged, Module owner) {
        return enumSetting(name, defaultValue, dependency, onChanged, owner);
    }

    public static ColorValue colorSetting(String name, Color defaultValue, Module owner) {
        return colorSetting(name, defaultValue, true, AbstractValue.Dependency.ALWAYS_TRUE, owner);
    }

    public static ColorValue colorSetting(String name, Color defaultValue, boolean allowAlpha, Module owner) {
        return colorSetting(name, defaultValue, allowAlpha, AbstractValue.Dependency.ALWAYS_TRUE, owner);
    }

    public static ColorValue colorSetting(String name, Color defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return colorSetting(name, defaultValue, true, dependency, owner);
    }

    public static ColorValue colorSetting(String name, Color defaultValue, boolean allowAlpha, AbstractValue.Dependency dependency, Module owner) {
        return register(new ColorValue(name, defaultValue, allowAlpha, dependency), owner);
    }

    public static ColorValue colorValue(String name, Color defaultValue, Module owner) {
        return colorSetting(name, defaultValue, owner);
    }

    public static ColorValue colorValue(String name, Color defaultValue, boolean allowAlpha, Module owner) {
        return colorSetting(name, defaultValue, allowAlpha, owner);
    }

    public static ColorValue colorValue(String name, Color defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return colorSetting(name, defaultValue, dependency, owner);
    }

    public static ColorValue colorValue(String name, Color defaultValue, boolean allowAlpha, AbstractValue.Dependency dependency, Module owner) {
        return colorSetting(name, defaultValue, allowAlpha, dependency, owner);
    }

    public static KeyBindValue keybindSetting(String name, int defaultValue, Module owner) {
        return keybindSetting(name, defaultValue, AbstractValue.Dependency.ALWAYS_TRUE, owner);
    }

    public static KeyBindValue keybindSetting(String name, int defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return register(new KeyBindValue(name, defaultValue, dependency), owner);
    }

    public static KeyBindValue keyBindSetting(String name, int defaultValue, Module owner) {
        return keybindSetting(name, defaultValue, owner);
    }

    public static KeyBindValue keyBindSetting(String name, int defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return keybindSetting(name, defaultValue, dependency, owner);
    }

    public static KeyBindValue keybindValue(String name, int defaultValue, Module owner) {
        return keybindSetting(name, defaultValue, owner);
    }

    public static KeyBindValue keybindValue(String name, int defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return keybindSetting(name, defaultValue, dependency, owner);
    }

    public static KeyBindValue keyBindValue(String name, int defaultValue, Module owner) {
        return keybindSetting(name, defaultValue, owner);
    }

    public static KeyBindValue keyBindValue(String name, int defaultValue, AbstractValue.Dependency dependency, Module owner) {
        return keybindSetting(name, defaultValue, dependency, owner);
    }

    public static ButtonValue buttonSetting(String name, Runnable runnable, Module owner) {
        return buttonSetting(name, runnable, AbstractValue.Dependency.ALWAYS_TRUE, owner);
    }

    public static ButtonValue buttonSetting(String name, Runnable runnable, AbstractValue.Dependency dependency, Module owner) {
        return register(new ButtonValue(name, runnable, dependency), owner);
    }

    public static ButtonValue buttonValue(String name, Runnable runnable, Module owner) {
        return buttonSetting(name, runnable, owner);
    }

    public static ButtonValue buttonValue(String name, Runnable runnable, AbstractValue.Dependency dependency, Module owner) {
        return buttonSetting(name, runnable, dependency, owner);
    }
}
