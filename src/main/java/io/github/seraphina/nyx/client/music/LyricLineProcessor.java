package io.github.seraphina.nyx.client.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricLineProcessor {
    private static final Pattern LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");
    private static final Pattern YRC_LINE_PATTERN = Pattern.compile("\\[(\\d+),(\\d+)](.*)");
    private static final Pattern YRC_WORD_PATTERN = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)");

    private LyricLineProcessor() {
    }

    public static List<LyricLine> parse(String text) {
        List<LyricLine> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        for (String line : text.replace("\\n", "\n").split("\\R")) {
            Matcher matcher = LRC_LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            long minutes = Long.parseLong(matcher.group(1));
            long seconds = Long.parseLong(matcher.group(2));
            long fraction = Long.parseLong(matcher.group(3));
            if (matcher.group(3).length() == 2) {
                fraction *= 10L;
            }
            lines.add(new LyricLine(matcher.group(4), minutes * 60_000L + seconds * 1000L + fraction));
        }

        lines.sort(Comparator.comparingLong(LyricLine::timeMs));
        return lines;
    }

    public static List<LyricLine> parseYrc(String text) {
        List<LyricLine> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        for (String line : text.replace("\\n", "\n").split("\\R")) {
            Matcher lineMatcher = YRC_LINE_PATTERN.matcher(line);
            if (!lineMatcher.matches()) {
                continue;
            }

            long lineTimeMs = Long.parseLong(lineMatcher.group(1));
            String content = lineMatcher.group(3);
            Matcher wordMatcher = YRC_WORD_PATTERN.matcher(content);
            List<LyricWord> words = new ArrayList<>();
            StringBuilder lyric = new StringBuilder();
            while (wordMatcher.find()) {
                String word = wordMatcher.group(3);
                long wordTime = Long.parseLong(wordMatcher.group(1));
                long wordDurationMs = Long.parseLong(wordMatcher.group(2));
                words.add(new LyricWord(word, wordTime, wordDurationMs));
                lyric.append(word);
            }

            if (!words.isEmpty()) {
                lines.add(new LyricLine(lyric.toString(), lineTimeMs, words));
            }
        }

        lines.sort(Comparator.comparingLong(LyricLine::timeMs));
        return lines;
    }

    public static int currentIndex(List<LyricLine> lyrics, long timeMs) {
        int index = -1;
        for (int i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).timeMs() > timeMs) {
                break;
            }
            index = i;
        }
        return index;
    }
}
