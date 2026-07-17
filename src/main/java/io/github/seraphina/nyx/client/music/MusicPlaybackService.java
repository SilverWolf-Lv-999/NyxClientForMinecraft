package io.github.seraphina.nyx.client.music;

import com.goxr3plus.streamplayer.enums.Status;
import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerEvent;
import com.goxr3plus.streamplayer.stream.StreamPlayerListener;
import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.manager.PathManager;
import io.github.seraphina.nyx.client.utility.web.WebUtility;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public final class MusicPlaybackService implements StreamPlayerListener {
    public static final MusicPlaybackService INSTANCE = new MusicPlaybackService();

    private static final int SPECTRUM_BANDS = 64;
    private static final int FFT_SIZE = 1024;
    private static final float DEFAULT_SAMPLE_RATE = 44100.0F;
    private static final int DEFAULT_CHANNELS = 2;
    private static final int DEFAULT_SAMPLE_BITS = 16;
    private static final long SPECTRUM_STALE_NANOS = 300_000_000L;
    private static final float MIN_SPECTRUM_HZ = 40.0F;
    private static final float MAX_SPECTRUM_HZ = 16000.0F;

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
    private volatile PlaybackMode playbackMode = PlaybackMode.LIST;
    private volatile float[] spectrumBands = new float[SPECTRUM_BANDS];
    private volatile long spectrumUpdateNanos;

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

        synchronized (playlist) {
            int index = playlist.indexOf(song);
            if (index >= 0) {
                currentIndex = index;
            }
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

                clearSpectrum();
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
            clearSpectrum();
        });
    }

    public void playNext() {
        Song next;
        synchronized (playlist) {
            if (playlist.isEmpty() || changingSong) {
                return;
            }
            currentIndex = nextIndex();
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
            currentIndex = previousIndex();
            previous = playlist.get(currentIndex);
        }
        playSong(previous);
    }

    public void cyclePlaybackMode() {
        PlaybackMode[] modes = PlaybackMode.values();
        playbackMode = modes[(playbackMode.ordinal() + 1) % modes.length];
    }

    public void setPlaybackMode(PlaybackMode playbackMode) {
        if (playbackMode != null) {
            this.playbackMode = playbackMode;
        }
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

    public void seekTo(float progress) {
        float safeProgress = Math.max(0.0F, Math.min(1.0F, progress));
        executor.execute(() -> {
            if (currentFile == null || totalDurationMs <= 0L || changingSong) {
                return;
            }

            try {
                int durationSeconds = player.getDurationInSeconds();
                if (durationSeconds <= 0) {
                    return;
                }
                int targetSeconds = Math.min(
                    durationSeconds - 1,
                    (int)Math.floor(durationSeconds * safeProgress)
                );
                player.seekTo(targetSeconds);
                positionMs = Math.min(totalDurationMs - 1L, Math.round(totalDurationMs * safeProgress));
            } catch (Exception exception) {
                NyxClient.LOGGER.warn("Failed to seek music", exception);
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

    public PlaybackMode playbackMode() {
        return playbackMode;
    }

    public String status() {
        return status;
    }

    public float[] spectrumSnapshot(int bandCount) {
        int safeBandCount = Math.max(1, Math.min(128, bandCount));
        float[] source = spectrumBands;
        if (!playing || System.nanoTime() - spectrumUpdateNanos > SPECTRUM_STALE_NANOS) {
            return new float[safeBandCount];
        }

        if (source.length == safeBandCount) {
            return source.clone();
        }

        float[] snapshot = new float[safeBandCount];
        if (safeBandCount == 1) {
            snapshot[0] = source[0];
            return snapshot;
        }

        for (int band = 0; band < safeBandCount; band++) {
            float sourceIndex = band * (source.length - 1.0F) / (safeBandCount - 1.0F);
            int low = (int)Math.floor(sourceIndex);
            int high = Math.min(source.length - 1, low + 1);
            float progress = sourceIndex - low;
            snapshot[band] = source[low] + (source[high] - source[low]) * progress;
        }
        return snapshot;
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
        updateSpectrum(pcmData);
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
            clearSpectrum();
            if (ended && !changingSong) {
                playNext();
            }
        }
    }

    private void updateSpectrum(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 64) {
            return;
        }

        AudioFormat format = player.getSourceDataLine() == null ? null : player.getSourceDataLine().getFormat();
        float sampleRate = format == null || format.getSampleRate() <= 0.0F ? DEFAULT_SAMPLE_RATE : format.getSampleRate();
        int channels = format == null || format.getChannels() <= 0 ? DEFAULT_CHANNELS : format.getChannels();
        int sampleBits = format == null || format.getSampleSizeInBits() <= 0 ? DEFAULT_SAMPLE_BITS : format.getSampleSizeInBits();
        boolean bigEndian = format != null && format.isBigEndian();

        float[] samples = readMonoSamples(pcmData, channels, sampleBits, bigEndian);
        if (samples == null) {
            return;
        }

        float[] real = new float[FFT_SIZE];
        float[] imaginary = new float[FFT_SIZE];
        int sampleOffset = Math.max(0, samples.length - FFT_SIZE);
        for (int i = 0; i < FFT_SIZE; i++) {
            int sourceIndex = sampleOffset + i;
            float sample = sourceIndex < samples.length ? samples[sourceIndex] : 0.0F;
            float window = 0.5F - 0.5F * (float)Math.cos(2.0D * Math.PI * i / (FFT_SIZE - 1));
            real[i] = sample * window;
        }

        fft(real, imaginary);
        spectrumBands = buildBands(real, imaginary, sampleRate);
        spectrumUpdateNanos = System.nanoTime();
    }

    private static float[] readMonoSamples(byte[] pcmData, int channels, int sampleBits, boolean bigEndian) {
        int bytesPerSample = Math.max(1, sampleBits / 8);
        if (bytesPerSample > 4) {
            return null;
        }

        int frameSize = bytesPerSample * Math.max(1, channels);
        int frameCount = pcmData.length / frameSize;
        if (frameCount <= 0) {
            return null;
        }

        float[] samples = new float[Math.min(frameCount, FFT_SIZE)];
        int sourceFrameOffset = Math.max(0, frameCount - samples.length);
        for (int frame = 0; frame < samples.length; frame++) {
            float mixedSample = 0.0F;
            int frameOffset = (sourceFrameOffset + frame) * frameSize;
            for (int channel = 0; channel < channels; channel++) {
                int sampleOffset = frameOffset + channel * bytesPerSample;
                mixedSample += readSample(pcmData, sampleOffset, bytesPerSample, bigEndian);
            }
            samples[frame] = mixedSample / channels;
        }
        return samples;
    }

    private static float readSample(byte[] pcmData, int offset, int bytesPerSample, boolean bigEndian) {
        if (offset < 0 || offset + bytesPerSample > pcmData.length) {
            return 0.0F;
        }

        if (bytesPerSample == 1) {
            return pcmData[offset] / 128.0F;
        }

        if (bytesPerSample == 2) {
            int first = pcmData[offset] & 0xFF;
            int second = pcmData[offset + 1] & 0xFF;
            int value = bigEndian ? (first << 8) | second : (second << 8) | first;
            return (short)value / 32768.0F;
        }

        int value = 0;
        for (int i = 0; i < bytesPerSample; i++) {
            int byteIndex = bigEndian ? i : bytesPerSample - 1 - i;
            value = (value << 8) | (pcmData[offset + byteIndex] & 0xFF);
        }
        int shift = (4 - bytesPerSample) * 8;
        value = (value << shift) >> shift;
        return value / (float)(1L << (bytesPerSample * 8 - 1));
    }

    private static void fft(float[] real, float[] imaginary) {
        int size = real.length;
        for (int i = 1, j = 0; i < size; i++) {
            int bit = size >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tempReal = real[i];
                real[i] = real[j];
                real[j] = tempReal;
                float tempImaginary = imaginary[i];
                imaginary[i] = imaginary[j];
                imaginary[j] = tempImaginary;
            }
        }

        for (int length = 2; length <= size; length <<= 1) {
            double angle = -2.0D * Math.PI / length;
            float phaseStepReal = (float)Math.cos(angle);
            float phaseStepImaginary = (float)Math.sin(angle);
            for (int start = 0; start < size; start += length) {
                float phaseReal = 1.0F;
                float phaseImaginary = 0.0F;
                for (int offset = 0; offset < length / 2; offset++) {
                    int evenIndex = start + offset;
                    int oddIndex = evenIndex + length / 2;
                    float oddReal = real[oddIndex] * phaseReal - imaginary[oddIndex] * phaseImaginary;
                    float oddImaginary = real[oddIndex] * phaseImaginary + imaginary[oddIndex] * phaseReal;
                    real[oddIndex] = real[evenIndex] - oddReal;
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary;
                    real[evenIndex] += oddReal;
                    imaginary[evenIndex] += oddImaginary;

                    float nextPhaseReal = phaseReal * phaseStepReal - phaseImaginary * phaseStepImaginary;
                    phaseImaginary = phaseReal * phaseStepImaginary + phaseImaginary * phaseStepReal;
                    phaseReal = nextPhaseReal;
                }
            }
        }
    }

    private static float[] buildBands(float[] real, float[] imaginary, float sampleRate) {
        float[] bands = new float[SPECTRUM_BANDS];
        float nyquist = sampleRate * 0.5F;
        float maxHz = Math.min(MAX_SPECTRUM_HZ, nyquist);
        float minHz = Math.min(MIN_SPECTRUM_HZ, maxHz * 0.5F);
        double logMin = Math.log(minHz);
        double logMax = Math.log(maxHz);

        for (int band = 0; band < bands.length; band++) {
            float startProgress = band / (float)bands.length;
            float endProgress = (band + 1.0F) / bands.length;
            float startHz = (float)Math.exp(logMin + (logMax - logMin) * startProgress);
            float endHz = (float)Math.exp(logMin + (logMax - logMin) * endProgress);
            int startBin = Math.max(1, Math.round(startHz * FFT_SIZE / sampleRate));
            int endBin = Math.max(startBin + 1, Math.round(endHz * FFT_SIZE / sampleRate));
            endBin = Math.min(endBin, FFT_SIZE / 2);

            float total = 0.0F;
            float peak = 0.0F;
            int count = 0;
            for (int bin = startBin; bin < endBin; bin++) {
                float magnitude = (float)Math.sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) / FFT_SIZE;
                total += magnitude;
                peak = Math.max(peak, magnitude);
                count++;
            }

            float average = count == 0 ? 0.0F : total / count;
            bands[band] = clamp01((float)Math.log10(1.0F + (peak + average * 0.35F) * 38.0F));
        }
        return bands;
    }

    private void clearSpectrum() {
        spectrumBands = new float[SPECTRUM_BANDS];
        spectrumUpdateNanos = 0L;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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

    private int nextIndex() {
        return switch (playbackMode) {
            case LOOP -> currentIndex;
            case RANDOM -> randomIndex();
            case LIST -> (currentIndex + 1) % playlist.size();
        };
    }

    private int previousIndex() {
        return switch (playbackMode) {
            case LOOP -> currentIndex;
            case RANDOM -> randomIndex();
            case LIST -> currentIndex > 0 ? currentIndex - 1 : playlist.size() - 1;
        };
    }

    private int randomIndex() {
        if (playlist.size() <= 1) {
            return 0;
        }

        int next = ThreadLocalRandom.current().nextInt(playlist.size());
        if (next == currentIndex) {
            next = (next + 1) % playlist.size();
        }
        return next;
    }

    public enum PlaybackMode {
        LOOP("循环"),
        LIST("列表"),
        RANDOM("随机");

        private final String label;

        PlaybackMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
