package io.github.seraphina.nyx.client.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.value.AbstractValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;
    private static final String MODULES_KEY = "modules";
    private static final String DEFAULT_CONFIG_NAME = "default";
    private static final String CONFIG_EXTENSION = ".json";
    private static final Pattern CONFIG_NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Path DEFAULT_CONFIG_FILE = PathManager.CONFIG_PATH.resolve("modules.json");
    private static final Path CONFIG_DIRECTORY = PathManager.CONFIG_PATH.resolve("profiles");
    private static final Path SELECTED_CONFIG_FILE = PathManager.CONFIG_PATH.resolve("selected.txt");

    private static String selectedConfigName = DEFAULT_CONFIG_NAME;
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

        try {
            Files.createDirectories(CONFIG_DIRECTORY);
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        selectedConfigName = readSelectedConfigName();
        load();
        if (!Files.exists(getConfigFile())) {
            save();
        }
        registerShutdownHook();
    }

    public static void load() {
        load(getConfigFile());
    }

    public static boolean load(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (!isValidConfigName(normalizedName)) {
            return false;
        }

        Path configFile = getConfigFile(normalizedName);
        if (!Files.exists(configFile)) {
            return false;
        }

        save();
        selectedConfigName = normalizedName;
        writeSelectedConfigName();
        load(configFile);
        return true;
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
        save(getConfigFile());
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
        return getConfigFile(selectedConfigName);
    }

    public static String getSelectedConfigName() {
        return selectedConfigName;
    }

    public static boolean create(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (!isValidConfigName(normalizedName) || exists(normalizedName)) {
            return false;
        }

        save();
        selectedConfigName = normalizedName;
        writeSelectedConfigName();
        save();
        return true;
    }

    public static boolean exists(String configName) {
        String normalizedName = normalizeConfigName(configName);
        return isValidConfigName(normalizedName) && Files.exists(getConfigFile(normalizedName));
    }

    public static List<String> getConfigNames() {
        TreeSet<String> names = new TreeSet<>();
        names.add(DEFAULT_CONFIG_NAME);

        if (Files.isDirectory(CONFIG_DIRECTORY)) {
            try (var paths = Files.list(CONFIG_DIRECTORY)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(CONFIG_EXTENSION))
                        .map(name -> name.substring(0, name.length() - CONFIG_EXTENSION.length()))
                        .map(ConfigManager::normalizeConfigName)
                        .filter(ConfigManager::isValidConfigName)
                        .forEach(names::add);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        return new ArrayList<>(names);
    }

    public static boolean isValidConfigName(String configName) {
        String normalizedName = normalizeConfigName(configName);
        return CONFIG_NAME_PATTERN.matcher(normalizedName).matches();
    }

    public static boolean delete() {
        try {
            return Files.deleteIfExists(getConfigFile());
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

    private static String normalizeConfigName(String configName) {
        return configName == null ? "" : configName.trim().toLowerCase(Locale.ROOT);
    }

    private static Path getConfigFile(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (DEFAULT_CONFIG_NAME.equals(normalizedName)) {
            return DEFAULT_CONFIG_FILE;
        }

        return CONFIG_DIRECTORY.resolve(normalizedName + CONFIG_EXTENSION);
    }

    private static String readSelectedConfigName() {
        if (!Files.exists(SELECTED_CONFIG_FILE)) {
            return DEFAULT_CONFIG_NAME;
        }

        try {
            String selectedName = normalizeConfigName(Files.readString(SELECTED_CONFIG_FILE, StandardCharsets.UTF_8));
            if (!isValidConfigName(selectedName)) {
                return DEFAULT_CONFIG_NAME;
            }

            return DEFAULT_CONFIG_NAME.equals(selectedName) || Files.exists(getConfigFile(selectedName))
                    ? selectedName
                    : DEFAULT_CONFIG_NAME;
        } catch (IOException exception) {
            exception.printStackTrace();
            return DEFAULT_CONFIG_NAME;
        }
    }

    private static void writeSelectedConfigName() {
        try {
            Files.createDirectories(SELECTED_CONFIG_FILE.getParent());
            Files.writeString(SELECTED_CONFIG_FILE, selectedConfigName, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
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
                values.add(value.getConfigName(), value.toJson());
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
            JsonElement valueElement = values.get(value.getConfigName());
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
