package io.github.seraphina.nyx.client.command;

import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class KeyNames {
    private static final Map<String, Integer> KEYS = new LinkedHashMap<>();

    static {
        register("none", -1);

        for (char key = 'a'; key <= 'z'; key++) {
            register(String.valueOf(key), GLFW.GLFW_KEY_A + key - 'a');
        }

        for (char key = '0'; key <= '9'; key++) {
            register(String.valueOf(key), GLFW.GLFW_KEY_0 + key - '0');
        }

        for (int key = 1; key <= 25; key++) {
            register("f" + key, GLFW.GLFW_KEY_F1 + key - 1);
        }

        register("space", GLFW.GLFW_KEY_SPACE);
        register("tab", GLFW.GLFW_KEY_TAB);
        register("enter", GLFW.GLFW_KEY_ENTER);
        register("escape", GLFW.GLFW_KEY_ESCAPE);
        register("backspace", GLFW.GLFW_KEY_BACKSPACE);
        register("delete", GLFW.GLFW_KEY_DELETE);
        register("insert", GLFW.GLFW_KEY_INSERT);
        register("home", GLFW.GLFW_KEY_HOME);
        register("end", GLFW.GLFW_KEY_END);
        register("page_up", GLFW.GLFW_KEY_PAGE_UP);
        register("page_down", GLFW.GLFW_KEY_PAGE_DOWN);
        register("up", GLFW.GLFW_KEY_UP);
        register("down", GLFW.GLFW_KEY_DOWN);
        register("left", GLFW.GLFW_KEY_LEFT);
        register("right", GLFW.GLFW_KEY_RIGHT);
        register("left_shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        register("right_shift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        register("left_control", GLFW.GLFW_KEY_LEFT_CONTROL);
        register("right_control", GLFW.GLFW_KEY_RIGHT_CONTROL);
        register("left_alt", GLFW.GLFW_KEY_LEFT_ALT);
        register("right_alt", GLFW.GLFW_KEY_RIGHT_ALT);
        register("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
        register("minus", GLFW.GLFW_KEY_MINUS);
        register("equal", GLFW.GLFW_KEY_EQUAL);
        register("left_bracket", GLFW.GLFW_KEY_LEFT_BRACKET);
        register("right_bracket", GLFW.GLFW_KEY_RIGHT_BRACKET);
        register("backslash", GLFW.GLFW_KEY_BACKSLASH);
        register("semicolon", GLFW.GLFW_KEY_SEMICOLON);
        register("apostrophe", GLFW.GLFW_KEY_APOSTROPHE);
        register("comma", GLFW.GLFW_KEY_COMMA);
        register("period", GLFW.GLFW_KEY_PERIOD);
        register("slash", GLFW.GLFW_KEY_SLASH);
        register("caps_lock", GLFW.GLFW_KEY_CAPS_LOCK);
    }

    private KeyNames() {
    }

    public static int getKey(String name) {
        Integer key = KEYS.get(normalize(name));
        if (key == null) {
            throw new IllegalArgumentException("Unknown key: " + name);
        }

        return key;
    }

    public static String getName(String name) {
        String normalized = normalize(name);
        if (!KEYS.containsKey(normalized)) {
            throw new IllegalArgumentException("Unknown key: " + name);
        }

        return normalized;
    }

    public static Set<String> suggestions() {
        return Collections.unmodifiableSet(KEYS.keySet());
    }

    private static void register(String name, int key) {
        KEYS.put(name, key);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
    }
}
