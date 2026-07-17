package io.github.seraphina.nyx.client.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;

public final class LuaUtility {
    private static final String DEFAULT_NAMESPACE = "nyxclient";

    private LuaUtility() {
    }

    public static Globals createGlobals() {
        return JsePlatform.standardGlobals();
    }

    public static LuaValue load(Globals globals, String path) throws IOException {
        Identifier resource = resource(path);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            throw new IOException("Minecraft resource manager is not available");
        }

        try (InputStream input = minecraft.getResourceManager().open(resource)) {
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return globals.load(source, resource.toString()).call();
        }
    }

    public static Identifier resource(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Lua resource path cannot be blank");
        }

        String normalized = path.replace('\\', '/').strip();
        if (normalized.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
            int separator = normalized.indexOf('/');
            if (separator <= 0 || separator >= normalized.length() - 1) {
                throw new IllegalArgumentException("Invalid asset path: " + path);
            }
            return Identifier.fromNamespaceAndPath(
                normalized.substring(0, separator),
                normalized.substring(separator + 1)
            );
        }

        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0) {
            return Identifier.fromNamespaceAndPath(
                normalized.substring(0, namespaceSeparator),
                normalized.substring(namespaceSeparator + 1)
            );
        }
        return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, normalized);
    }

    public static LuaValue toLua(Object value) {
        return toLua(value, new IdentityHashMap<>());
    }

    private static LuaValue toLua(Object value, IdentityHashMap<Object, LuaValue> visited) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof LuaValue luaValue) {
            return luaValue;
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return LuaValue.valueOf(((Number)value).longValue());
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
            return LuaValue.valueOf(value.toString());
        }

        LuaValue previous = visited.get(value);
        if (previous != null) {
            return previous;
        }

        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            visited.put(value, table);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    table.set(toLua(entry.getKey(), visited), toLua(entry.getValue(), visited));
                }
            }
            return table;
        }

        if (value instanceof Iterable<?> iterable) {
            LuaTable table = new LuaTable();
            visited.put(value, table);
            int index = 1;
            for (Object item : iterable) {
                table.set(index++, toLua(item, visited));
            }
            return table;
        }

        if (value.getClass().isArray()) {
            LuaTable table = new LuaTable();
            visited.put(value, table);
            for (int index = 0; index < Array.getLength(value); index++) {
                table.set(index + 1, toLua(Array.get(value, index), visited));
            }
            return table;
        }

        return LuaValue.valueOf(value.toString());
    }
}
