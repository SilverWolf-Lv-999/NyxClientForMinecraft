package io.github.seraphina.nyxclient.value;

import io.github.seraphina.nyxclient.utility.LanguageUtility;

public class ValueGroup {
    private final String name;
    private boolean collapsed = true;

    public ValueGroup(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return LanguageUtility.translate(name);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
    }
}
