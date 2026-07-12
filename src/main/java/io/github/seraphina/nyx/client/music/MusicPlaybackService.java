package io.github.seraphina.nyx.client.music;

import com.goxr3plus.streamplayer.enums.Status;
import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerEvent;
import com.goxr3plus.streamplayer.stream.StreamPlayerListener;
import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.manager.PathManager;
import io.github.seraphina.nyx.client.utility.web.WebUtility;

import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MusicPlaybackService implements StreamPlayerListener {
    public static final MusicPlaybackService INSTANCE = new MusicPlaybackService();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Nyx-MusicPlayer");
        thread.setDaemon(true);
        return thread;
    });
    private final StreamPlayer player = new StreamPlayer();
    private final List<Song> playlist = Collections.synchronizedList(new ArrayList<>());
    private final List<LyricLine> lyric = Collections.synchronizedList(new ArrayList<>());

    private volatile Song currentSong;
    private volatile File currentFile;
    private volatile boolean playing;
    private volatile boolean changingSong;
    private volatile long positionMs;
    private volatile long totalDurationMs;
    private volatile float volume = 1.0F;
    private volatile int currentIndex;
    private volatile String status = "Idle";

    private MusicPlaybackService() {
        player.addStreamPlayerListener(this);
    }

    public void setPlaylist(List<Song> songs, int startIndex) {
        synchronized (playlist) {
            playlist.clear();
            if (songs != null) {
                playlist.addAll(songs);
            }
            currentIndex = Math.max(0, Math.min(startIndex, Math.max(0, playlist.size() - 1)));
        }
    }

    public void playSong(Song song) {
        if (song == null) {
            return;
        }

        executor.execute(() -> {
            try {
                changingSong = true;
                status = "Loading " + song.name();
                SongFile songFile = NeteaseMusicApi.getSongFile(song.id());
                if (!songFile.isPlayable()) {
                    status = "Song is unavailable";
                    return;
                }

                Path cachedFile = cachePath(song);
                if (!Files.exists(cachedFile) || (songFile.size() > 0L && Files.size(cachedFile) < songFile.size())) {
                    status = "Downloading " + song.name();
                    Files.write(cachedFile, WebUtility.getBytes(songFile.url()));
                }

                List<LyricLine> nextLyric;
                try {
                    nextLyric = NeteaseMusicApi.getLyric(song.id());
                } catch (Exception ignored) {
                    nextLyric = List.of();
                }
                synchronized (lyric) {
                    lyric.clear();
                    lyric.addAll(nextLyric);
                }

                currentSong = song;
                currentFile = cachedFile.toFile();
                positionMs = 0L;
                totalDurationMs = song.duration() > 0L ? song.duration() : calculateDuration(currentFile);
                player.stop();
                player.open(currentFile);
                player.setGain(volume);
                playing = true;
                status = "Playing";
                player.play();
            } catch (Exception exception) {
                playing = false;
                status = "Failed: " + exception.getMessage();
                NyxClient.LOGGER.warn("Failed to play music", exception);
            } finally {
                changingSong = false;
            }
        });
    }

    public void toggle() {
        executor.execute(() -> {
            try {
                if (playing) {
                    player.pause();
                    playing = false;
                    status = "Paused";
                } else {
                    player.resume();
                    playing = true;
                    status = "Playing";
                }
            } catch (Exception exception) {
                NyxClient.LOGGER.warn("Failed to toggle music playback", exception);
            }
        });
    }

    public void stop() {
        executor.execute(() -> {
            player.stop();
            playing = false;
            positionMs = 0L;
            status = "Stopped";
        });
    }

    public void playNext() {
        Song next;
        synchronized (playlist) {
            if (playlist.isEmpty() || changingSong) {
                return;
            }
            currentIndex = (currentIndex + 1) % playlist.size();
            next = playlist.get(currentIndex);
        }
        playSong(next);
    }

    public void playPrevious() {
        Song previous;
        synchronized (playlist) {
            if (playlist.isEmpty() || changingSong) {
                return;
            }
            currentIndex = currentIndex > 0 ? currentIndex - 1 : playlist.size() - 1;
            previous = playlist.get(currentIndex);
        }
        playSong(previous);
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
        executor.execute(() -> {
            try {
                player.setGain(this.volume);
            } catch (Exception exception) {
                NyxClient.LOGGER.warn("Failed to set music volume", exception);
            }
        });
    }

    public Song currentSong() {
        return currentSong;
    }

    public boolean isPlaying() {
        return playing;
    }

    public long positionMs() {
        return positionMs;
    }

    public long totalDurationMs() {
        return totalDurationMs;
    }

    public float volume() {
        return volume;
    }

    public String status() {
        return status;
    }

    public List<LyricLine> lyricsSnapshot() {
        synchronized (lyric) {
            return List.copyOf(lyric);
        }
    }

    public static String formatTime(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public void opened(Object dataSource, java.util.Map<String, Object> properties) {
    }

    @Override
    public void progress(int encodedBytes, long microsecondPosition, byte[] pcmData, java.util.Map<String, Object> properties) {
        positionMs = microsecondPosition / 1000L;
    }

    @Override
    public void statusUpdated(StreamPlayerEvent event) {
        Status playerStatus = event.getPlayerStatus();
        if (playerStatus == Status.PLAYING || playerStatus == Status.RESUMED) {
            playing = true;
            status = "Playing";
        } else if (playerStatus == Status.PAUSED) {
            playing = false;
            status = "Paused";
        } else if (playerStatus == Status.STOPPED) {
            boolean ended = totalDurationMs > 0L && positionMs >= totalDurationMs * 0.90F;
            playing = false;
            if (ended && !changingSong) {
                playNext();
            }
        }
    }

    private static Path cachePath(Song song) throws Exception {
        Path cacheDir = PathManager.CLIENT_PATH.resolve("music-cache");
        Files.createDirectories(cacheDir);
        return cacheDir.resolve(song.id() + ".mp3");
    }

    private static long calculateDuration(File file) {
        try (var stream = AudioSystem.getAudioInputStream(file)) {
            float frameRate = stream.getFormat().getFrameRate();
            if (frameRate <= 0.0F) {
                return 0L;
            }
            return (long)(stream.getFrameLength() / frameRate * 1000.0F);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
