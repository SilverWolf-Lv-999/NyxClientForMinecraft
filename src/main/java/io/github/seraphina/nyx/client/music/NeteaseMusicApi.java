package io.github.seraphina.nyx.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.seraphina.nyx.client.utility.web.WebUtility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NeteaseMusicApi {
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
        JsonObject root = getJsonObject("/playlist/track/all?id=" + id + "&limit=80");
        List<Song> result = new ArrayList<>();
        JsonArray songs = array(root, "songs");
        for (JsonElement element : songs) {
            result.add(parseCloudSong(object(element)));
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
        JsonObject root = getJsonObject("/song/url/v1?id=" + id + "&level=exhigh&UnblockNeteaseMusic=true");
        JsonArray data = array(root, "data");
        if (data.isEmpty()) {
            return new SongFile("", 0L);
        }

        JsonObject file = object(data.get(0));
        return new SongFile(string(file, "url"), number(file, "size"));
    }

    public static List<LyricLine> getLyric(long id) throws IOException, InterruptedException {
        JsonObject root = getJsonObject("/lyric?id=" + id);
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
}
