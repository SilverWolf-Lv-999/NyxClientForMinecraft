package io.github.seraphina.nyx.client.utility;

import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.module.other.MusicPlayer;
import io.github.seraphina.nyx.client.music.LyricLine;
import io.github.seraphina.nyx.client.music.MusicPlaybackService;
import io.github.seraphina.nyx.client.music.Song;

import java.util.List;

public final class MusicUtility {
    private static final long SMTC_REFRESH_NANOS = 100_000_000L;

    private static volatile SeraNative.SmtcMediaInfo thisSmtcMediaInfo = SeraNative.SmtcMediaInfo.EMPTY;
    private static volatile long thisSmtcUpdatedNanos;
    private static volatile String thisLastSmtcLogState = "";

    private MusicUtility() {
    }

    public static MusicSnapshot snapshot() {
        return isSmtcSelected() ? smtcSnapshot() : clientSnapshot();
    }

    public static float[] spectrumSnapshot(int bandCount) {
        int safeBandCount = Math.max(1, Math.min(128, bandCount));
        if (isSmtcSelected()) {
            // SMTC exposes media metadata and playback state, but not PCM samples.
            return new float[safeBandCount];
        }
        return MusicPlaybackService.INSTANCE.spectrumSnapshot(safeBandCount);
    }

    public static String formatTime(long milliseconds) {
        return MusicPlaybackService.formatTime(milliseconds);
    }

    private static boolean isSmtcSelected() {
        return MusicPlayer.INSTANCE.from.is(MusicPlayer.From.SMTC);
    }

    private static MusicSnapshot clientSnapshot() {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        return new MusicSnapshot(
            player.currentSong(),
            player.isPlaying(),
            player.positionMs(),
            player.totalDurationMs(),
            player.status(),
            player.lyricsSnapshot()
        );
    }

    private static MusicSnapshot smtcSnapshot() {
        long now = System.nanoTime();
        SeraNative.SmtcMediaInfo mediaInfo = smtcMediaInfo(now);
        if (!mediaInfo.hasActiveSession()) {
            return new MusicSnapshot(
                null,
                false,
                0L,
                0L,
                mediaInfo.diagnostic(),
                List.of()
            );
        }

        long positionMilliseconds = mediaInfo.positionMilliseconds();
        if (mediaInfo.playbackStatus() == SeraNative.SmtcPlaybackStatus.PLAYING) {
            positionMilliseconds += Math.max(0L, (now - thisSmtcUpdatedNanos) / 1_000_000L);
        }

        long durationMilliseconds = mediaInfo.durationMilliseconds();
        if (durationMilliseconds > 0L) {
            positionMilliseconds = Math.min(positionMilliseconds, durationMilliseconds);
        }

        Song song = new Song(
            "",
            smtcTitle(mediaInfo),
            mediaInfo.artist().strip(),
            smtcSongId(mediaInfo),
            durationMilliseconds
        );
        return new MusicSnapshot(
            song,
            mediaInfo.playbackStatus() == SeraNative.SmtcPlaybackStatus.PLAYING,
            positionMilliseconds,
            durationMilliseconds,
            smtcStatus(mediaInfo),
            smtcLyrics(song)
        );
    }

    private static SeraNative.SmtcMediaInfo smtcMediaInfo(long now) {
        if (now - thisSmtcUpdatedNanos < SMTC_REFRESH_NANOS) {
            return thisSmtcMediaInfo;
        }

        synchronized (MusicUtility.class) {
            if (now - thisSmtcUpdatedNanos >= SMTC_REFRESH_NANOS) {
                thisSmtcMediaInfo = SeraNative.getSmtcInfo();
                thisSmtcUpdatedNanos = now;
                logSmtcState(thisSmtcMediaInfo);
            }
            return thisSmtcMediaInfo;
        }
    }

    private static void logSmtcState(SeraNative.SmtcMediaInfo mediaInfo) {
        String state = mediaInfo.hasActiveSession()
            + "\u0000" + mediaInfo.sourceAppId()
            + "\u0000" + mediaInfo.playbackStatus()
            + "\u0000" + mediaInfo.title()
            + "\u0000" + mediaInfo.artist()
            + "\u0000" + mediaInfo.diagnostic();
        if (state.equals(thisLastSmtcLogState)) {
            return;
        }

        thisLastSmtcLogState = state;
        NyxClient.LOGGER.info(
            "SMTC session active={}, source={}, status={}, title={}, artist={}, diagnostic={}",
            mediaInfo.hasActiveSession(),
            mediaInfo.sourceAppId(),
            mediaInfo.playbackStatus(),
            mediaInfo.title(),
            mediaInfo.artist(),
            mediaInfo.diagnostic()
        );
    }

    private static String smtcTitle(SeraNative.SmtcMediaInfo mediaInfo) {
        String title = mediaInfo.title().strip();
        return title.isBlank() ? "Unknown title" : title;
    }

    private static long smtcSongId(SeraNative.SmtcMediaInfo mediaInfo) {
        String key = mediaInfo.sourceAppId() + '\u0000' + mediaInfo.title() + '\u0000' + mediaInfo.artist();
        return key.hashCode();
    }

    private static String smtcStatus(SeraNative.SmtcMediaInfo mediaInfo) {
        String sourceAppId = mediaInfo.sourceAppId().strip();
        String source = sourceAppId.isBlank() ? "SMTC" : sourceAppId;
        return switch (mediaInfo.playbackStatus()) {
            case PLAYING -> source + ": Playing";
            case PAUSED -> source + ": Paused";
            case STOPPED -> source + ": Stopped";
            case OPENED, CHANGING -> source + ": Loading";
            case CLOSED, UNKNOWN -> source;
        };
    }

    private static List<LyricLine> smtcLyrics(Song song) {
        String artist = song.singer();
        String text = artist == null || artist.isBlank()
            ? song.name()
            : song.name() + " - " + artist.strip();
        return List.of(new LyricLine(text, 0L));
    }

    public record MusicSnapshot(
            Song song,
            boolean playing,
            long positionMs,
            long durationMs,
            String status,
            List<LyricLine> lyrics
    ) {
        public static final MusicSnapshot EMPTY = new MusicSnapshot(null, false, 0L, 0L, "No active SMTC session", List.of());
    }
}
