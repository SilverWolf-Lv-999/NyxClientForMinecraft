package io.github.seraphina.nyxclient.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.value.AbstractValue;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Path.of(PathManager.CONFIG, "modules.json");
    private static boolean shutdownHookRegistered;

    public static void init() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        load();
        registerShutdownHook();
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            JsonObject modules = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules")
                    : root;

            for (Module module : ModuleManager.MODULES) {
                JsonElement moduleElement = modules.get(module.getConfigName());
                if (moduleElement != null && moduleElement.isJsonObject()) {
                    try {
                        loadModule(module, moduleElement.getAsJsonObject());
                    } catch (RuntimeException exception) {
                        exception.printStackTrace();
                    }
                }
            }
        } catch (IOException | JsonParseException exception) {
            exception.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();

        ModuleManager.MODULES.stream()
                .sorted(Comparator.comparing(Module::getConfigName))
                .forEach(module -> modules.add(module.getConfigName(), writeModule(module)));

        root.add("modules", modules);

        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static JsonObject writeModule(Module module) {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", module.isEnabled());
        object.addProperty("key", module.getKey());

        JsonObject values = new JsonObject();
        for (AbstractValue<?> value : module.getValues()) {
            if (value.isSerializable()) {
                values.add(value.getName(), value.toJson());
            }
        }

        object.add("values", values);
        return object;
    }

    private static void loadModule(Module module, JsonObject object) {
        if (object.has("key")) {
            module.setKey(object.get("key").getAsInt());
        }

        JsonObject values = object.has("values") && object.get("values").isJsonObject()
                ? object.getAsJsonObject("values")
                : new JsonObject();

        for (AbstractValue<?> value : module.getValues()) {
            JsonElement valueElement = values.get(value.getName());
            if (valueElement == null) {
                continue;
            }

            if (valueElement.isJsonObject()) {
                value.fromJson(valueElement.getAsJsonObject());
            } else {
                JsonObject wrapper = new JsonObject();
                wrapper.add("value", valueElement);
                value.fromJson(wrapper);
            }
        }

        if (object.has("enabled")) {
            module.setEnabled(object.get("enabled").getAsBoolean());
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(ConfigManager::save, "NyxClient Config Save"));
        shutdownHookRegistered = true;
    }
}
