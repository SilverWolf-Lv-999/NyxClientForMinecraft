package io.github.seraphina.nyx.client.music;

import java.util.List;

public record LyricLine(String text, long timeMs, List<LyricWord> words) {
    public LyricLine {
        text = text == null ? "" : text;
        timeMs = Math.max(0L, timeMs);
        words = words == null ? List.of() : List.copyOf(words);
    }

    public LyricLine(String text, long timeMs) {
        this(text, timeMs, List.of());
    }
}
