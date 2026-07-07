package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import io.github.seraphina.nyx.client.value.impl.ButtonValue;

public class OnNyxButtonClickEvent implements Event {
    private final ButtonValue value;
    private final Button button;
    private final Action action;
    private final int time;

    public OnNyxButtonClickEvent(ButtonValue value, Button button, Action action, final int time) {
        this.value = value;
        this.button = button;
        this.action = action;
        this.time = Math.max(0, time);
    }

    public ButtonValue getValue() {
        return value;
    }

    public Button getButton() {
        return button;
    }

    public Action getAction() {
        return action;
    }

    public int getTime() {
        return time;
    }

    public boolean isLeftButton() {
        return button == Button.LEFT;
    }

    public boolean isRightButton() {
        return button == Button.RIGHT;
    }

    public boolean isClick() {
        return action == Action.CLICK;
    }

    public boolean isDoubleClick() {
        return action == Action.DOUBLE_CLICK;
    }

    public boolean isLongPress() {
        return action == Action.LONG_PRESS;
    }

    public enum Button {
        LEFT,
        RIGHT
    }

    public enum Action {
        CLICK,
        DOUBLE_CLICK,
        LONG_PRESS
    }
}
