package io.github.seraphina.nyx.client.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.github.seraphina.nyx.client.manager.PathManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AltManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;
    private static final String ACCOUNTS_KEY = "accounts";
    private static final String EMPTY_ACCESS_TOKEN = "0";
    private static final Path ALT_FILE = PathManager.CLIENT_PATH.resolve("alts.json");
    private static final Path LEGACY_ALT_FILE = PathManager.CONFIG_PATH.resolve("alts.json");
    private static final List<Account> ACCOUNTS = new ArrayList<>();

    private static Account currentAccount;
    private static boolean shutdownHookRegistered;

    private AltManager() {
    }

    public static void init() {
        try {
            PathManager.init();
            migrateLegacyAltFile();
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        load();
        if (!Files.exists(ALT_FILE)) {
            save();
        }
        registerShutdownHook();
    }

    public static void load() {
        load(ALT_FILE);
    }

    public static void load(Path altFile) {
        if (altFile == null || !Files.exists(altFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(altFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            ACCOUNTS.clear();

            if (rootElement.isJsonArray()) {
                loadAccounts(rootElement.getAsJsonArray());
                return;
            }

            if (!rootElement.isJsonObject()) {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            JsonElement accountsElement = root.get(ACCOUNTS_KEY);
            if (accountsElement != null && accountsElement.isJsonArray()) {
                loadAccounts(accountsElement.getAsJsonArray());
            } else {
                Account account = fromJson(root);
                if (account.isValid()) {
                    ACCOUNTS.add(account);
                }
            }
        } catch (IOException | JsonParseException exception) {
            exception.printStackTrace();
        }
    }

    public static void save() {
        save(ALT_FILE);
    }

    public static void save(Path altFile) {
        if (altFile == null) {
            return;
        }

        try {
            writeConfig(altFile, createRoot());
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static Path getAltFile() {
        return ALT_FILE;
    }

    public static List<Account> getAccounts() {
        return Collections.unmodifiableList(ACCOUNTS);
    }

    public static Optional<Account> getCurrentAccount() {
        return Optional.ofNullable(currentAccount);
    }

    public static Optional<Account> getLastUsedAccount() {
        return ACCOUNTS.stream().max(Comparator.comparingLong(Account::getLastUsed));
    }

    public static Account addCracked(String name) {
        Account account = Account.cracked(name);
        add(account);
        return account;
    }

    public static Account addMicrosoft(String name, String uuid, String accessToken) {
        Account account = Account.microsoft(name, uuid, accessToken);
        add(account);
        return account;
    }

    public static Account addMicrosoft(String name, UUID uuid, String accessToken) {
        Account account = Account.microsoft(name, uuid, accessToken);
        add(account);
        return account;
    }

    public static boolean add(Account account) {
        if (account == null || !account.isValid()) {
            return false;
        }

        int index = indexOf(account);
        if (index >= 0) {
            Account previous = ACCOUNTS.set(index, account);
            if (previous == currentAccount) {
                currentAccount = account;
            }
        } else {
            ACCOUNTS.add(account);
        }

        save();
        return true;
    }

    public static boolean remove(Account account) {
        if (account == null) {
            return false;
        }

        int index = indexOf(account);
        if (index < 0) {
            return false;
        }

        Account removed = ACCOUNTS.remove(index);
        if (removed == currentAccount) {
            currentAccount = null;
        }

        save();
        return true;
    }

    public static boolean remove(String name) {
        Optional<Account> account = findByName(name);
        return account.filter(AltManager::remove).isPresent();
    }

    public static void clear() {
        ACCOUNTS.clear();
        currentAccount = null;
        save();
    }

    public static Optional<Account> findByName(String name) {
        if (isBlank(name)) {
            return Optional.empty();
        }

        String normalizedName = name.strip();
        return ACCOUNTS.stream()
                .filter(account -> normalizedName.equalsIgnoreCase(account.getName()))
                .findFirst();
    }

    public static Optional<Account> findByUuid(String uuid) {
        return findByUuid(parseUuid(uuid));
    }

    public static Optional<Account> findByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }

        return ACCOUNTS.stream()
                .filter(account -> uuid.equals(account.getUuidValue()))
                .findFirst();
    }

    public static boolean login(String name) {
        return findByName(name).map(AltManager::login).orElse(false);
    }

    public static boolean login(Account account) {
        if (account == null || !account.isValid()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        Account storedAccount = resolveStoredAccount(account);
        User user = storedAccount.toUser();
        UserApiService userApiService = createUserApiService(minecraft, storedAccount, user);

        minecraft.user = user;
        minecraft.userApiService = userApiService;
        minecraft.profileFuture = createProfileFuture(minecraft, storedAccount, user);
        minecraft.userPropertiesFuture = createUserPropertiesFuture(userApiService);
        minecraft.profileKeyPairManager = createProfileKeyPairManager(minecraft, storedAccount, user, userApiService);

        storedAccount.setLastUsed(System.currentTimeMillis());
        currentAccount = storedAccount;
        save();
        return true;
    }

    public static boolean isCurrent(Account account) {
        if (account == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getUser() == null) {
            return account == currentAccount;
        }

        return Objects.equals(account.getUuidValue(), minecraft.getUser().getProfileId())
                && account.getName().equalsIgnoreCase(minecraft.getUser().getName());
    }

    private static JsonObject createRoot() {
        JsonObject root = new JsonObject();
        JsonArray accounts = new JsonArray();

        for (Account account : ACCOUNTS) {
            if (account.isValid()) {
                accounts.add(account.toJson());
            }
        }

        root.addProperty("version", CONFIG_VERSION);
        root.add(ACCOUNTS_KEY, accounts);
        return root;
    }

    private static void loadAccounts(JsonArray array) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            Account account = fromJson(element.getAsJsonObject());
            if (account.isValid() && indexOf(account) < 0) {
                ACCOUNTS.add(account);
            }
        }
    }

    private static Account fromJson(JsonObject object) {
        Account account = new Account();
        account.parseJson(object);
        return account;
    }

    private static Account resolveStoredAccount(Account account) {
        int index = indexOf(account);
        if (index >= 0) {
            Account storedAccount = ACCOUNTS.get(index);
            if (storedAccount != account) {
                ACCOUNTS.set(index, account);
                return account;
            }

            return storedAccount;
        }

        ACCOUNTS.add(account);
        return account;
    }

    private static int indexOf(Account account) {
        if (account == null) {
            return -1;
        }

        UUID uuid = account.getUuidValue();
        for (int index = 0; index < ACCOUNTS.size(); index++) {
            Account other = ACCOUNTS.get(index);
            if (uuid != null && uuid.equals(other.getUuidValue())) {
                return index;
            }

            if (!isBlank(account.getName()) && account.getName().equalsIgnoreCase(other.getName())) {
                return index;
            }
        }

        return -1;
    }

    private static UserApiService createUserApiService(Minecraft minecraft, Account account, User user) {
        if (account.getType() == AccountType.MICROSOFT) {
            return new YggdrasilAuthenticationService(minecraft.getProxy()).createUserApiService(user.getAccessToken());
        }

        return UserApiService.OFFLINE;
    }

    private static CompletableFuture<com.mojang.authlib.yggdrasil.ProfileResult> createProfileFuture(Minecraft minecraft, Account account, User user) {
        if (account.getType() != AccountType.MICROSOFT) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> minecraft.services().sessionService().fetchProfile(user.getProfileId(), true));
    }

    private static CompletableFuture<UserApiService.UserProperties> createUserPropertiesFuture(UserApiService userApiService) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return userApiService.fetchProperties();
            } catch (AuthenticationException exception) {
                return UserApiService.OFFLINE_PROPERTIES;
            }
        });
    }

    private static ProfileKeyPairManager createProfileKeyPairManager(Minecraft minecraft, Account account, User user, UserApiService userApiService) {
        if (account.getType() == AccountType.MICROSOFT) {
            return ProfileKeyPairManager.create(userApiService, user, minecraft.gameDirectory.toPath());
        }

        return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
    }

    private static void writeConfig(Path configFile, JsonObject root) throws IOException {
        Path target = configFile.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new FileNotFoundException("Alt path has no parent: " + configFile);
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

        Runtime.getRuntime().addShutdownHook(new Thread(AltManager::save, "NyxClient Alt Save"));
        shutdownHookRegistered = true;
    }

    private static void migrateLegacyAltFile() throws IOException {
        if (!Files.exists(ALT_FILE) && Files.exists(LEGACY_ALT_FILE)) {
            Files.move(LEGACY_ALT_FILE, ALT_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsLong();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String cleanNullable(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static UUID parseUuid(String value) {
        if (isBlank(value)) {
            return null;
        }

        String uuid = value.strip();
        if (uuid.length() == 32) {
            uuid = uuid.substring(0, 8) + "-"
                    + uuid.substring(8, 12) + "-"
                    + uuid.substring(12, 16) + "-"
                    + uuid.substring(16, 20) + "-"
                    + uuid.substring(20);
        }

        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public enum AccountType {
        CRACKED("cracked"),
        MICROSOFT("microsoft");

        private final String name;

        AccountType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static AccountType getByName(String name) {
            if (isBlank(name)) {
                return CRACKED;
            }

            String normalizedName = name.strip().toLowerCase(Locale.ROOT);
            for (AccountType type : values()) {
                if (type.name.equals(normalizedName) || type.name().equalsIgnoreCase(normalizedName)) {
                    return type;
                }
            }

            return CRACKED;
        }
    }

    public static class Account {
        private AccountType type;
        private String name;
        private String uuid;
        private String accessToken;
        private String xuid;
        private String clientId;
        private long lastUsed;

        public Account() {
            this(AccountType.CRACKED, "", "", EMPTY_ACCESS_TOKEN);
        }

        public Account(AccountType type, String name, String uuid, String accessToken) {
            this.type = type == null ? AccountType.CRACKED : type;
            this.name = clean(name);
            this.uuid = clean(uuid);
            this.accessToken = clean(accessToken);

            normalize();
        }

        public Account(AccountType type, String name, UUID uuid, String accessToken) {
            this(type, name, uuid == null ? "" : uuid.toString(), accessToken);
        }

        public static Account cracked(String name) {
            String cleanedName = clean(name);
            return new Account(AccountType.CRACKED, cleanedName, offlineUuid(cleanedName), EMPTY_ACCESS_TOKEN);
        }

        public static Account microsoft(String name, String uuid, String accessToken) {
            return new Account(AccountType.MICROSOFT, name, uuid, accessToken);
        }

        public static Account microsoft(String name, UUID uuid, String accessToken) {
            return new Account(AccountType.MICROSOFT, name, uuid, accessToken);
        }

        public boolean login() {
            return AltManager.login(this);
        }

        public boolean isValid() {
            return !isBlank(name)
                    && type != null
                    && getUuidValue() != null
                    && (type == AccountType.CRACKED || !isBlank(accessToken));
        }

        public User toUser() {
            UUID profileId = getUuidValue();
            if (profileId == null) {
                profileId = offlineUuid(name);
            }

            return new User(name, profileId, getSessionAccessToken(), Optional.ofNullable(xuid), Optional.ofNullable(clientId));
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("type", type.getName());
            object.addProperty("name", name);
            object.addProperty("uuid", uuid);
            object.addProperty("accessToken", accessToken);
            object.addProperty("lastUsed", lastUsed);

            if (!isBlank(xuid)) {
                object.addProperty("xuid", xuid);
            }
            if (!isBlank(clientId)) {
                object.addProperty("clientId", clientId);
            }

            return object;
        }

        public void parseJson(JsonObject object) {
            if (object == null) {
                return;
            }

            type = AccountType.getByName(readString(object, "type"));
            name = clean(readString(object, "name"));
            uuid = clean(readString(object, "uuid"));
            accessToken = clean(readString(object, "accessToken"));
            xuid = cleanNullable(readString(object, "xuid"));
            clientId = cleanNullable(readString(object, "clientId"));
            lastUsed = readLong(object, "lastUsed", 0L);

            normalize();
        }

        public AccountType getType() {
            return type;
        }

        public void setType(AccountType type) {
            this.type = type == null ? AccountType.CRACKED : type;
            normalize();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = clean(name);
            normalize();
        }

        public String getUuid() {
            return uuid;
        }

        public UUID getUuidValue() {
            return parseUuid(uuid);
        }

        public void setUuid(String uuid) {
            this.uuid = clean(uuid);
            normalize();
        }

        public void setUuid(UUID uuid) {
            this.uuid = uuid == null ? "" : uuid.toString();
            normalize();
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = clean(accessToken);
            normalize();
        }

        public String getXuid() {
            return xuid;
        }

        public void setXuid(String xuid) {
            this.xuid = cleanNullable(xuid);
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = cleanNullable(clientId);
        }

        public long getLastUsed() {
            return lastUsed;
        }

        public void setLastUsed(long lastUsed) {
            this.lastUsed = lastUsed;
        }

        private String getSessionAccessToken() {
            if (type == AccountType.CRACKED && isBlank(accessToken)) {
                return EMPTY_ACCESS_TOKEN;
            }

            return clean(accessToken);
        }

        private void normalize() {
            if (type == null) {
                type = AccountType.CRACKED;
            }

            name = clean(name);
            uuid = clean(uuid);
            accessToken = clean(accessToken);

            if (type == AccountType.CRACKED) {
                if (!isBlank(name) && parseUuid(uuid) == null) {
                    uuid = offlineUuid(name).toString();
                }

                if (isBlank(accessToken)) {
                    accessToken = EMPTY_ACCESS_TOKEN;
                }
            }
        }
    }
}
