package io.github.seraphina.nyx.client.music;

public record SongFile(String url, long size) {
    public boolean isPlayable() {
        return url != null && !url.isBlank();
    }
}
