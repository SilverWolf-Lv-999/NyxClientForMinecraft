package io.github.seraphina.nyx.client.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.seraphina.nyx.client.manager.PathManager;
import io.github.seraphina.nyx.client.utility.web.WebUtility;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NeteaseMusicApi {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SESSION_VERSION = 1;
    private static final Path SESSION_FILE = PathManager.CLIENT_PATH.resolve("netease-music-session.json");
    private static volatile LoginSession loginSession;

    private NeteaseMusicApi() {
    }

    public static List<Song> getTopNewSongs() throws IOException, InterruptedException {
        JsonObject root = getJsonObject("/top/song?type=0");
        List<Song> result = new ArrayList<>();
        JsonArray data = array(root, "data");
        for (int i = 0; i < Math.min(30, data.size()); i++) {
            JsonObject song = object(data.get(i));
            JsonObject album = object(song.get("album"));
            JsonArray artists = array(song, "artists");
            result.add(new Song(
                appendImageSize(string(album, "picUrl")),
                string(song, "name"),
                firstArtist(artists),
                number(song, "id"),
                number(song, "duration")
            ));
        }
        return result;
    }

    public static List<Playlist> getRecommendPlaylists(int limit) throws IOException, InterruptedException {
        JsonObject root = getJsonObject("/personalized?limit=" + Math.max(1, limit));
        List<Playlist> result = new ArrayList<>();
        JsonArray data = array(root, "result");
        for (JsonElement element : data) {
            JsonObject playlist = object(element);
            result.add(new Playlist(
                number(playlist, "id"),
                string(playlist, "name"),
                appendImageSize(string(playlist, "picUrl")),
                number(playlist, "playCount")
            ));
        }
        return result;
    }

    public static List<Song> getPlaylistDetail(long id) throws IOException, InterruptedException {
        JsonObject root = getJsonObject(withSession("/playlist/track/all?id=" + id + "&limit=80"));
        List<Song> result = new ArrayList<>();
        JsonArray songs = array(root, "songs");
        for (JsonElement element : songs) {
            result.add(parseCloudSong(object(element)));
        }
        return result;
    }

    public static LoginSession loginCellphone(String phone, String password) throws IOException, InterruptedException {
        if (phone == null || phone.isBlank()) {
            throw new IOException("Phone number is empty");
        }
        if (password == null || password.isBlank()) {
            throw new IOException("Password is empty");
        }

        JsonObject root = getJsonObject("/login/cellphone?phone=" + WebUtility.encode(phone.trim())
            + "&password=" + WebUtility.encode(password)
            + "&timestamp=" + System.currentTimeMillis());
        return rememberSession(sessionFromLoginResponse(root));
    }

    public static void sendCaptcha(String phone) throws IOException, InterruptedException {
        if (phone == null || phone.isBlank()) {
            throw new IOException("Phone number is empty");
        }

        JsonObject root = getJsonObject("/captcha/sent?phone=" + WebUtility.encode(phone.trim())
            + "&timestamp=" + System.currentTimeMillis());
        requireCode(root, 200, "Failed to send captcha");
    }

    public static LoginSession loginCellphoneCaptcha(String phone, String captcha) throws IOException, InterruptedException {
        if (phone == null || phone.isBlank()) {
            throw new IOException("Phone number is empty");
        }
        if (captcha == null || captcha.isBlank()) {
            throw new IOException("Captcha is empty");
        }

        JsonObject root = getJsonObject("/login/cellphone?phone=" + WebUtility.encode(phone.trim())
            + "&captcha=" + WebUtility.encode(captcha.trim())
            + "&timestamp=" + System.currentTimeMillis());
        return rememberSession(sessionFromLoginResponse(root));
    }

    public static QrLogin createQrLogin() throws IOException, InterruptedException {
        JsonObject keyRoot = getJsonObject("/login/qr/key?timestamp=" + System.currentTimeMillis());
        requireCode(keyRoot, 200, "Failed to create QR key");
        String key = string(object(keyRoot.get("data")), "unikey");
        if (key.isBlank()) {
            throw new IOException("Netease QR login response did not include a key");
        }

        JsonObject qrRoot = getJsonObject("/login/qr/create?key=" + WebUtility.encode(key)
            + "&qrimg=true"
            + "&timestamp=" + System.currentTimeMillis());
        requireCode(qrRoot, 200, "Failed to create QR code");
        JsonObject data = object(qrRoot.get("data"));
        String qrImage = string(data, "qrimg");
        String qrUrl = string(data, "qrurl");
        if (qrImage.isBlank() && qrUrl.isBlank()) {
            throw new IOException("Netease QR login response did not include a QR code");
        }
        return new QrLogin(key, qrUrl, qrImage);
    }

    public static QrLoginStatus checkQrLogin(String key) throws IOException, InterruptedException {
        if (key == null || key.isBlank()) {
            throw new IOException("QR key is empty");
        }

        JsonObject root = getJsonObject("/login/qr/check?key=" + WebUtility.encode(key)
            + "&timestamp=" + System.currentTimeMillis());
        int code = (int)number(root, "code");
        String message = errorMessage(root, switch (code) {
            case 800 -> "QR code expired";
            case 801 -> "Waiting for scan";
            case 802 -> "Waiting for confirmation";
            case 803 -> "QR login confirmed";
            default -> "QR login failed: " + code;
        });
        if (code != 803) {
            return new QrLoginStatus(code, message, null);
        }

        String cookie = string(root, "cookie");
        if (cookie.isBlank()) {
            throw new IOException("Netease QR login response did not include a cookie");
        }
        LoginSession session = rememberSession(loadSessionFromCookie(cookie));
        return new QrLoginStatus(code, message, session);
    }

    public static boolean hasSavedSession() {
        return Files.isRegularFile(SESSION_FILE);
    }

    public static LoginSession restoreSession() throws IOException, InterruptedException {
        LoginSession current = loginSession;
        if (current != null) {
            return current;
        }

        LoginSession savedSession = readSavedSession();
        if (savedSession == null) {
            return null;
        }

        try {
            return rememberSession(loadSessionFromCookie(savedSession.cookie()));
        } catch (InvalidLoginSessionException exception) {
            deleteSavedSession();
            return null;
        }
    }

    public static void logout() throws IOException {
        loginSession = null;
        deleteSavedSession();
    }

    public static boolean isLoggedIn() {
        return loginSession != null;
    }

    public static LoginSession currentSession() {
        return loginSession;
    }

    public static List<Playlist> getUserPlaylists() throws IOException, InterruptedException {
        LoginSession session = loginSession;
        if (session == null) {
            return List.of();
        }

        JsonObject root = getJsonObject("/user/playlist?uid=" + session.uid()
            + "&limit=100&cookie=" + WebUtility.encode(session.cookie())
            + "&timestamp=" + System.currentTimeMillis());
        List<Playlist> result = new ArrayList<>();
        JsonArray data = array(root, "playlist");
        for (JsonElement element : data) {
            JsonObject playlist = object(element);
            result.add(new Playlist(
                number(playlist, "id"),
                string(playlist, "name"),
                appendImageSize(string(playlist, "coverImgUrl")),
                number(playlist, "playCount")
            ));
        }
        return result;
    }

    public static List<Song> search(String query) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        JsonObject root = getJsonObject("/cloudsearch?keywords=" + WebUtility.encode(query));
        JsonObject resultObject = object(root.get("result"));
        JsonArray songs = array(resultObject, "songs");
        List<Song> result = new ArrayList<>();
        for (JsonElement element : songs) {
            result.add(parseCloudSong(object(element)));
        }
        return result;
    }

    public static SongFile getSongFile(long id) throws IOException, InterruptedException {
        JsonObject root = getJsonObject(withSession("/song/url/v1?id=" + id + "&level=exhigh&UnblockNeteaseMusic=true"));
        JsonArray data = array(root, "data");
        if (data.isEmpty()) {
            return new SongFile("", 0L);
        }

        JsonObject file = object(data.get(0));
        return new SongFile(string(file, "url"), number(file, "size"));
    }

    public static List<LyricLine> getLyric(long id) throws IOException, InterruptedException {
        JsonObject root = getJsonObject(withSession("/lyric?id=" + id));
        JsonObject lrc = object(root.get("lrc"));
        return LyricLineProcessor.parse(string(lrc, "lyric"));
    }

    private static Song parseCloudSong(JsonObject song) {
        JsonObject album = object(song.get("al"));
        JsonArray artists = array(song, "ar");
        return new Song(
            appendImageSize(string(album, "picUrl")),
            string(song, "name"),
            firstArtist(artists),
            number(song, "id"),
            number(song, "dt")
        );
    }

    private static JsonObject getJsonObject(String path) throws IOException, InterruptedException {
        IOException failure = null;
        for (String baseUrl : baseUrls()) {
            try {
                return WebUtility.getJsonObject(baseUrl + path);
            } catch (IOException exception) {
                failure = exception;
            }
        }
        if (failure != null) {
            throw failure;
        }
        throw new IOException("No Netease API base URL configured");
    }

    private static String withSession(String path) {
        LoginSession session = loginSession;
        if (session == null || session.cookie().isBlank()) {
            return path;
        }
        return path + (path.contains("?") ? "&" : "?")
            + "cookie=" + WebUtility.encode(session.cookie())
            + "&timestamp=" + System.currentTimeMillis();
    }

    private static List<String> baseUrls() {
        Set<String> urls = new LinkedHashSet<>();
        urls.add(NeteaseMusicLocalService.baseUrl());
        return List.copyOf(urls);
    }

    private static void addConfiguredUrls(Set<String> urls, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String rawUrl : value.split(",")) {
            String url = rawUrl.trim();
            if (!url.isBlank()) {
                urls.add(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
            }
        }
    }

    private static LoginSession sessionFromLoginResponse(JsonObject root) throws IOException {
        requireCode(root, 200, "Netease login failed");
        JsonObject profile = object(root.get("profile"));
        LoginSession session = new LoginSession(
            number(profile, "userId"),
            string(profile, "nickname"),
            string(root, "cookie")
        );
        if (session.uid() <= 0L || session.cookie().isBlank()) {
            throw new IOException("Netease login response did not include a usable session");
        }
        return session;
    }

    private static LoginSession loadSessionFromCookie(String cookie) throws IOException, InterruptedException {
        JsonObject root = getJsonObject("/user/account?cookie=" + WebUtility.encode(cookie)
            + "&timestamp=" + System.currentTimeMillis());
        int code = (int)number(root, "code");
        if (code != 200) {
            String message = errorMessage(root, "Netease login session is invalid: " + code);
            if (code == 301 || code == 302) {
                throw new InvalidLoginSessionException(message);
            }
            throw new IOException(message);
        }

        JsonObject profile = object(root.get("profile"));
        LoginSession session = new LoginSession(
            number(profile, "userId"),
            string(profile, "nickname"),
            cookie
        );
        if (session.uid() <= 0L || session.cookie().isBlank()) {
            throw new InvalidLoginSessionException("Netease account response did not include a usable session");
        }
        return session;
    }

    private static LoginSession rememberSession(LoginSession session) throws IOException {
        writeSavedSession(session);
        loginSession = session;
        return session;
    }

    private static LoginSession readSavedSession() throws IOException {
        if (!Files.isRegularFile(SESSION_FILE)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(SESSION_FILE, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                deleteSavedSession();
                return null;
            }

            JsonObject root = rootElement.getAsJsonObject();
            LoginSession session = new LoginSession(
                number(root, "uid"),
                string(root, "nickname"),
                string(root, "cookie")
            );
            if (session.cookie().isBlank()) {
                deleteSavedSession();
                return null;
            }
            return session;
        } catch (JsonParseException | IllegalStateException exception) {
            deleteSavedSession();
            return null;
        }
    }

    private static void writeSavedSession(LoginSession session) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", SESSION_VERSION);
        root.addProperty("uid", session.uid());
        root.addProperty("nickname", session.nickname());
        root.addProperty("cookie", session.cookie());

        Path target = SESSION_FILE.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new FileNotFoundException("Netease music session path has no parent: " + SESSION_FILE);
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

    private static void deleteSavedSession() throws IOException {
        Files.deleteIfExists(SESSION_FILE);
    }

    private static void requireCode(JsonObject root, int expected, String fallback) throws IOException {
        int code = (int)number(root, "code");
        if (code != expected) {
            throw new IOException(errorMessage(root, fallback + ": " + code));
        }
    }

    private static String errorMessage(JsonObject root, String fallback) {
        String message = string(root, "message");
        if (message.isBlank()) {
            message = string(root, "msg");
        }
        return message.isBlank() ? fallback : message;
    }

    private static String firstArtist(JsonArray artists) {
        if (artists.isEmpty()) {
            return "";
        }
        return string(object(artists.get(0)), "name");
    }

    private static String appendImageSize(String url) {
        if (url == null || url.isBlank() || url.contains("?param=")) {
            return url == null ? "" : url;
        }
        return url + "?param=200y200";
    }

    private static JsonArray array(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonArray()) {
            return new JsonArray();
        }
        return object.getAsJsonArray(name);
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return "";
        }
        try {
            return object.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static long number(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return 0L;
        }
        try {
            return object.get(name).getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public record LoginSession(long uid, String nickname, String cookie) {
    }

    public record QrLogin(String key, String qrUrl, String qrImage) {
    }

    public record QrLoginStatus(int code, String message, LoginSession session) {
    }

    private static final class InvalidLoginSessionException extends IOException {
        private InvalidLoginSessionException(String message) {
            super(message);
        }
    }
}
