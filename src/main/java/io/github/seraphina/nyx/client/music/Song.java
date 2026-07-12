package io.github.seraphina.nyx.client.music;

public record Song(String image, String name, String singer, long id, long duration) {
    public String displayArtist() {
        return singer == null || singer.isBlank() ? "Unknown artist" : singer;
    }
}
