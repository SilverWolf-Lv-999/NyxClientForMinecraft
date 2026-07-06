package io.github.seraphina.nyxclient.module;

import io.github.seraphina.nyxclient.events.api.EventManager;
import io.github.seraphina.nyxclient.utility.LanguageUtility;
import io.github.seraphina.nyxclient.value.AbstractValue;
import io.github.seraphina.nyxclient.value.ValueGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final List<AbstractValue<?>> values = new ArrayList<>();
    private final List<ValueGroup> valueGroups = new ArrayList<>();
    boolean enabled;

    int key = -1;

    public Module() {
    }

    public void registerValue(AbstractValue<?>... values) {
        this.values.addAll(Arrays.asList(values));
    }

    public void registerValueGroup(ValueGroup group) {
        this.valueGroups.add(group);
    }

    public List<AbstractValue<?>> getValues() {
        return Collections.unmodifiableList(values);
    }

    public List<ValueGroup> getValueGroups() {
        return Collections.unmodifiableList(valueGroups);
    }

    public void toggle() {
        this.setEnabled(!this.enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        if (enabled) {
            this.enabled = true;
            EventManager.register(this);
            this.onEnable();
        } else  {
            this.enabled = false;
            EventManager.unregister(this);
            this.onDisable();
        }
    }

    public void onEnable() {

    }

    public void onDisable() {}

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasKey() {
        return key != -1;
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public String getConfigName() {
        return this.getClass().getSimpleName();
    }

    public String getName() {
        return LanguageUtility.translate(this.getModuleInfo().name());
    }

    public String getDescription() {
        return LanguageUtility.translate(this.getModuleInfo().description());
    }

    public Category getCategory() {
        return this.getModuleInfo().category();
    }

    ModuleInfo getModuleInfo() {
        return this.getClass().getAnnotation(ModuleInfo.class);
    }
}
