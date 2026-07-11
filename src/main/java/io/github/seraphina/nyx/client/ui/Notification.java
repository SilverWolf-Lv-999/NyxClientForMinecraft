package io.github.seraphina.nyx.client.ui;

import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.IMinecraft;

public class Notification implements IMinecraft {
    private static final long ENTER_TIME_MS = 260L;
    private static final long DISPLAY_TIME_MS = 3000L;
    private static final long EXIT_TIME_MS = 240L;

    private final State state;
    private final String text;
    private final long createdAt;
    private final long lifetime;

    public Notification(State state, String text) {
        this.state = state == null ? State.INFO_MSG : state;
        this.text = sanitize(text);
        this.createdAt = System.currentTimeMillis();
        this.lifetime = ENTER_TIME_MS + DISPLAY_TIME_MS + EXIT_TIME_MS;
    }

    public static Notification module(String moduleName, boolean enabled) {
        return new Notification(
            enabled ? State.MODULE_ENABLED : State.MODULE_DISABLED,
            "Module " + sanitize(moduleName) + " | " + (enabled ? "Enabled" : "Disabled")
        );
    }

    public static Notification debug(String message) {
        return new Notification(State.DEBUG_MSG, "Debug | " + sanitize(message));
    }

    public static Notification info(String message) {
        return new Notification(State.INFO_MSG, "Info | " + sanitize(message));
    }

    public State getState() {
        return state;
    }

    public String getText() {
        return text;
    }

    public float getVisibilityProgress(long now) {
        long age = Math.max(0L, now - createdAt);
        if (age < ENTER_TIME_MS) {
            return MathUtility.easeOutCubic(age / (float)ENTER_TIME_MS);
        }

        long remaining = lifetime - age;
        if (remaining < EXIT_TIME_MS) {
            return MathUtility.easeInCubic(remaining / (float)EXIT_TIME_MS);
        }

        return 1.0F;
    }

    public boolean isExpired(long now) {
        return now - createdAt >= lifetime;
    }

    private static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim();
    }

    public enum State {
        MODULE_ENABLED,
        MODULE_DISABLED,
        INFO_MSG,
        DEBUG_MSG
    }
}
