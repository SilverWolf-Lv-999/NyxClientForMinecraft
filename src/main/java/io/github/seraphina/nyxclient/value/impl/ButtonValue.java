package io.github.seraphina.nyxclient.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import io.github.seraphina.nyxclient.events.api.EventManager;
import io.github.seraphina.nyxclient.events.impl.OnNyxButtonClickEvent;
import io.github.seraphina.nyxclient.value.AbstractValue;

public class ButtonValue extends AbstractValue<Runnable> {
    public ButtonValue(String name, Runnable runnable, Dependency dependency) {
        super(name, runnable, dependency);
    }

    public ButtonValue(String name, Runnable runnable) {
        this(name, runnable, Dependency.ALWAYS_TRUE);
    }

    public void press() {
        leftClick();
    }

    public OnNyxButtonClickEvent leftClick() {
        return click(OnNyxButtonClickEvent.Button.LEFT, OnNyxButtonClickEvent.Action.CLICK, 0, true);
    }

    public OnNyxButtonClickEvent leftSingleClick() {
        return leftClick();
    }

    public OnNyxButtonClickEvent rightClick() {
        return click(OnNyxButtonClickEvent.Button.RIGHT, OnNyxButtonClickEvent.Action.CLICK, 0, false);
    }

    public OnNyxButtonClickEvent rightSingleClick() {
        return rightClick();
    }

    public OnNyxButtonClickEvent leftDoubleClick() {
        return click(OnNyxButtonClickEvent.Button.LEFT, OnNyxButtonClickEvent.Action.DOUBLE_CLICK, 0, false);
    }

    public OnNyxButtonClickEvent rightDoubleClick() {
        return click(OnNyxButtonClickEvent.Button.RIGHT, OnNyxButtonClickEvent.Action.DOUBLE_CLICK, 0, false);
    }

    public OnNyxButtonClickEvent leftLongPress(final int time) {
        return click(OnNyxButtonClickEvent.Button.LEFT, OnNyxButtonClickEvent.Action.LONG_PRESS, time, false);
    }

    public OnNyxButtonClickEvent rightLongPress(final int time) {
        return click(OnNyxButtonClickEvent.Button.RIGHT, OnNyxButtonClickEvent.Action.LONG_PRESS, time, false);
    }

    public OnNyxButtonClickEvent click(OnNyxButtonClickEvent.Button button, OnNyxButtonClickEvent.Action action, final int time) {
        return click(button, action, time, action == OnNyxButtonClickEvent.Action.CLICK && button == OnNyxButtonClickEvent.Button.LEFT);
    }

    private OnNyxButtonClickEvent click(OnNyxButtonClickEvent.Button button, OnNyxButtonClickEvent.Action action, final int time, boolean runAction) {
        OnNyxButtonClickEvent event = EventManager.post(new OnNyxButtonClickEvent(this, button, action, time));
        if (runAction && value != null) {
            value.run();
        }
        return event;
    }

    @Override
    public boolean isSerializable() {
        return false;
    }

    @Override
    protected JsonElement writeValue() {
        return JsonNull.INSTANCE;
    }

    @Override
    protected Runnable readValue(JsonElement element) {
        return value;
    }

    @Override
    protected String getType() {
        return "button";
    }
}
