package io.github.seraphina.nyx.client.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FriendManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;
    private static final String FRIENDS_KEY = "friends";
    private static final Path FRIEND_FILE = PathManager.FRIEND.resolve("friends.json");
    private static final Map<String, String> FRIENDS = new LinkedHashMap<>();

    private static boolean shutdownHookRegistered;

    private FriendManager() {
    }

    public static void init() {
        try {
            PathManager.init();
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        load();
        if (!Files.exists(FRIEND_FILE)) {
            save();
        }
        registerShutdownHook();
    }

    public static void load() {
        load(FRIEND_FILE);
    }

    public static void load(Path friendFile) {
        if (friendFile == null || !Files.exists(friendFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(friendFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            FRIENDS.clear();

            if (rootElement.isJsonArray()) {
                loadFriends(rootElement.getAsJsonArray());
                return;
            }

            if (!rootElement.isJsonObject()) {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            JsonElement friendsElement = root.get(FRIENDS_KEY);
            if (friendsElement != null && friendsElement.isJsonArray()) {
                loadFriends(friendsElement.getAsJsonArray());
            }
        } catch (IOException | JsonParseException exception) {
            exception.printStackTrace();
        }
    }

    public static void save() {
        save(FRIEND_FILE);
    }

    public static void save(Path friendFile) {
        if (friendFile == null) {
            return;
        }

        try {
            writeConfig(friendFile, createRoot());
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static Path getFriendFile() {
        return FRIEND_FILE;
    }

    public static List<String> getFriends() {
        return Collections.unmodifiableList(List.copyOf(FRIENDS.values()));
    }

    public static boolean add(String name) {
        String cleanedName = clean(name);
        if (cleanedName.isEmpty()) {
            return false;
        }

        String key = normalizeName(cleanedName);
        if (FRIENDS.containsKey(key)) {
            return false;
        }

        FRIENDS.put(key, cleanedName);
        save();
        return true;
    }

    public static boolean remove(String name) {
        String key = normalizeName(name);
        if (key.isEmpty() || !FRIENDS.containsKey(key)) {
            return false;
        }

        FRIENDS.remove(key);
        save();
        return true;
    }

    public static boolean isFriend(String name) {
        String key = normalizeName(name);
        return !key.isEmpty() && FRIENDS.containsKey(key);
    }

    public static boolean isFriend(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        return isFriend(player.getName().getString());
    }

    private static JsonObject createRoot() {
        JsonObject root = new JsonObject();
        JsonArray friends = new JsonArray();

        for (String friend : FRIENDS.values()) {
            friends.add(friend);
        }

        root.addProperty("version", CONFIG_VERSION);
        root.add(FRIENDS_KEY, friends);
        return root;
    }

    private static void loadFriends(JsonArray array) {
        for (JsonElement element : array) {
            String name = readFriendName(element);
            String cleanedName = clean(name);
            if (!cleanedName.isEmpty()) {
                FRIENDS.putIfAbsent(normalizeName(cleanedName), cleanedName);
            }
        }
    }

    private static String readFriendName(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }

        try {
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }

            if (element.isJsonObject()) {
                JsonElement nameElement = element.getAsJsonObject().get("name");
                return nameElement == null || nameElement.isJsonNull() ? "" : nameElement.getAsString();
            }
        } catch (RuntimeException ignored) {
            return "";
        }

        return "";
    }

    private static void writeConfig(Path configFile, JsonObject root) throws IOException {
        Path target = configFile.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new FileNotFoundException("Friend path has no parent: " + configFile);
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

        Runtime.getRuntime().addShutdownHook(new Thread(FriendManager::save, "NyxClient Friend Save"));
        shutdownHookRegistered = true;
    }

    private static String normalizeName(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
