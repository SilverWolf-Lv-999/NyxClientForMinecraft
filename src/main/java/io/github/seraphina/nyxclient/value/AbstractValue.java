package io.github.seraphina.nyxclient.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.seraphina.nyxclient.utility.LanguageUtility;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class AbstractValue<T> {
    protected final String name;
    protected T value;
    protected final T defaultValue;
    protected final Dependency dependency;
    protected Consumer<T> onChanged;
    protected AbstractValue<?> parent;
    protected ValueGroup group;

    protected AbstractValue(String name, T defaultValue, Dependency dependency, Consumer<T> onChanged) {
        this.name = name;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.dependency = dependency == null ? Dependency.ALWAYS_TRUE : dependency;
        this.onChanged = onChanged;
    }

    protected AbstractValue(String name, T defaultValue, Dependency dependency) {
        this(name, defaultValue, dependency, null);
    }

    public AbstractValue(T value, T defaultValue, String name, AbstractValue<?> parent) {
        this.name = name;
        this.value = value;
        this.defaultValue = defaultValue;
        this.parent = parent;
        this.dependency = Dependency.ALWAYS_TRUE;
    }

    public AbstractValue(T value, T defaultValue, String name) {
        this(value, defaultValue, name, null);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return LanguageUtility.translate(name);
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
        if (onChanged != null) {
            onChanged.accept(value);
        }
    }

    public void setValueSilently(T value) {
        this.value = value;
    }

    public void reset() {
        setValueSilently(defaultValue);
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    public boolean isVisible() {
        boolean parentVisible = parent == null || (parent.isVisible() && parent.isTruthy());
        return parentVisible && dependency.check();
    }

    public boolean isAvailable() {
        return isVisible();
    }

    public AbstractValue<?> getParent() {
        return parent;
    }

    public Dependency getDependency() {
        return dependency;
    }

    public ValueGroup getGroup() {
        return group;
    }

    @SuppressWarnings("unchecked")
    public <S extends AbstractValue<T>> S group(ValueGroup group) {
        this.group = group;
        return (S) this;
    }

    public void setOnChanged(Consumer<T> onChanged) {
        this.onChanged = onChanged;
    }

    public Consumer<T> getOnChanged() {
        return onChanged;
    }

    public boolean isSerializable() {
        return true;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.addProperty("type", getType());
        object.add("value", writeValue());
        return object;
    }

    public void fromJson(JsonObject object) {
        if (object == null || !object.has("value") || object.get("value").isJsonNull()) {
            return;
        }

        try {
            setValueSilently(readValue(object.get("value")));
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isTruthy() {
        return !(value instanceof Boolean boolValue) || boolValue;
    }

    protected abstract JsonElement writeValue();

    protected abstract T readValue(JsonElement element);

    protected abstract String getType();

    @FunctionalInterface
    public interface Dependency {
        Dependency ALWAYS_TRUE = () -> true;

        boolean check();
    }
}
