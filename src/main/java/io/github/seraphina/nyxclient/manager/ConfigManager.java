package io.github.seraphina.nyxclient.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.value.AbstractValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;
    private static final String MODULES_KEY = "modules";
    private static final Path CONFIG_FILE = PathManager.CONFIG_PATH.resolve("modules.json");

    private static boolean shutdownHookRegistered;

    private ConfigManager() {
    }

    public static void init() {
        try {
            PathManager.init();
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        load();
        if (!Files.exists(CONFIG_FILE)) {
            save();
        }
        registerShutdownHook();
    }

    public static void load() {
        load(CONFIG_FILE);
    }

    public static void load(Path configFile) {
        if (configFile == null || !Files.exists(configFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            loadModules(readModules(root));
        } catch (IOException | JsonParseException exception) {
            exception.printStackTrace();
        }
    }

    public static void save() {
        save(CONFIG_FILE);
    }

    public static void save(Path configFile) {
        if (configFile == null) {
            return;
        }

        try {
            writeConfig(configFile, createRoot());
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static Path getConfigFile() {
        return CONFIG_FILE;
    }

    public static boolean delete() {
        try {
            return Files.deleteIfExists(CONFIG_FILE);
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private static JsonObject createRoot() {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();

        ModuleManager.getModules().stream()
                .sorted(Comparator.comparing(Module::getConfigName))
                .forEach(module -> modules.add(module.getConfigName(), writeModule(module)));

        root.addProperty("version", CONFIG_VERSION);
        root.add(MODULES_KEY, modules);
        return root;
    }

    private static JsonObject readModules(JsonObject root) {
        JsonElement modulesElement = root.get(MODULES_KEY);
        if (modulesElement != null && modulesElement.isJsonObject()) {
            return modulesElement.getAsJsonObject();
        }

        return root;
    }

    private static void loadModules(JsonObject modules) {
        for (Module module : ModuleManager.getModules()) {
            JsonElement moduleElement = modules.get(module.getConfigName());
            if (moduleElement == null || !moduleElement.isJsonObject()) {
                continue;
            }

            try {
                loadModule(module, moduleElement.getAsJsonObject());
            } catch (RuntimeException exception) {
                exception.printStackTrace();
            }
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
        Integer key = readInt(object, "key");
        if (key != null) {
            module.setKey(key);
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

        Boolean enabled = readBoolean(object, "enabled");
        if (enabled != null) {
            module.setEnabled(enabled);
        }
    }

    private static Integer readInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsInt();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Boolean readBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsBoolean();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void writeConfig(Path configFile, JsonObject root) throws IOException {
        Path target = configFile.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new FileNotFoundException("Config path has no parent: " + configFile);
        }

        Files.createDirectories(directory);
        Path tempFile = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        boolean moved = false;

        try {
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
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
