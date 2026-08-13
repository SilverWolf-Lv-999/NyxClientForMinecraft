package io.github.seraphina.nyx.client.music;

public record LyricWord(String text, long timeMs, long durationMs) {
    public LyricWord {
        text = text == null ? "" : text;
        timeMs = Math.max(0L, timeMs);
        durationMs = Math.max(0L, durationMs);
    }
}
