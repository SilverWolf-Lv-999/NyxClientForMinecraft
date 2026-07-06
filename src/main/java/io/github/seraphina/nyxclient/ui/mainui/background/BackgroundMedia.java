package io.github.seraphina.nyxclient.ui.mainui.background;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_WARNING;

public final class BackgroundMedia implements AutoCloseable {
    private static final Identifier DEFAULT_BACKGROUND = Identifier.fromNamespaceAndPath(
        "nyxclient",
        "ui/background/background.jpg"
    );
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();
    private static final int THUMBNAIL_WIDTH = 192;
    private static final int THUMBNAIL_HEIGHT = 108;
    private static final long DEFAULT_FRAME_NANOS = 33_333_333L;
    private static final int DISABLED_STREAM_INDEX = Integer.MAX_VALUE;

    private final String key;
    private final String displayName;
    @Nullable
    private final Path path;
    private final boolean builtIn;
    private final boolean animated;

    @Nullable
    private BufferedImage currentImage;
    @Nullable
    private DynamicTexture texture;
    private int textureWidth;
    private int textureHeight;

    @Nullable
    private DynamicTexture thumbnailTexture;
    private int thumbnailWidth;
    private int thumbnailHeight;

    @Nullable
    private FFmpegFrameGrabber grabber;
    @Nullable
    private Java2DFrameConverter frameConverter;

    private boolean imageLoadAttempted;
    private boolean thumbnailLoadAttempted;
    private boolean decoderStartAttempted;
    private boolean decoderFailed;
    private long lastFrameNanos;
    private long frameNanos = DEFAULT_FRAME_NANOS;

    private BackgroundMedia(String key, String displayName, @Nullable Path path, boolean builtIn, boolean animated) {
        this.key = key;
        this.displayName = displayName;
        this.path = path;
        this.builtIn = builtIn;
        this.animated = animated;
    }

    public static BackgroundMedia defaultBackground() {
        return new BackgroundMedia(BackgroundLibrary.DEFAULT_KEY, "Default", null, true, false);
    }

    public static BackgroundMedia fileBackground(Path path, boolean animated) {
        Path absolutePath = path.toAbsolutePath().normalize();
        return new BackgroundMedia(absolutePath.toString(), path.getFileName().toString(), absolutePath, false, animated);
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public String sourceLabel() {
        return this.path == null ? "Built-in background" : this.path.toString();
    }

    public boolean animated() {
        return this.animated;
    }

    public boolean render(float x, float y, float width, float height) {
        updateFrame();
        if (this.texture == null) {
            return false;
        }

        Render2DUtility.drawTextureCover(this.texture.getTextureView(), this.textureWidth, this.textureHeight, x, y, width, height);
        return true;
    }

    public boolean renderThumbnail(float x, float y, float width, float height) {
        ensureThumbnailTexture();
        if (this.thumbnailTexture == null) {
            return false;
        }

        Render2DUtility.drawTextureCover(
            this.thumbnailTexture.getTextureView(),
            this.thumbnailWidth,
            this.thumbnailHeight,
            x,
            y,
            width,
            height
        );
        return true;
    }

    public void pausePlayback() {
        closeDecoder();
        this.decoderStartAttempted = false;
        this.decoderFailed = false;
        this.lastFrameNanos = 0L;
    }

    private void updateFrame() {
        if (this.animated && this.path != null) {
            updateAnimatedFrame();
            return;
        }

        ensureStillImageLoaded();
    }

    private void ensureStillImageLoaded() {
        if (this.imageLoadAttempted) {
            return;
        }

        this.imageLoadAttempted = true;
        BufferedImage image = this.builtIn ? readDefaultImage() : readPathImage(this.path);
        if (image == null && this.path != null) {
            image = decodeFirstFrame(this.path);
        }
        if (image != null) {
            setCurrentImage(image);
        }
    }

    private void updateAnimatedFrame() {
        if (!ensureDecoderStarted()) {
            ensureStillImageLoaded();
            return;
        }

        long now = System.nanoTime();
        if (this.currentImage != null && now - this.lastFrameNanos < this.frameNanos) {
            return;
        }

        BufferedImage frame = grabNextFrame();
        if (frame == null) {
            rewindDecoder();
            frame = grabNextFrame();
        }
        if (frame != null) {
            setCurrentImage(frame);
            this.lastFrameNanos = now;
        }
    }

    private boolean ensureDecoderStarted() {
        if (this.grabber != null && this.frameConverter != null) {
            return true;
        }
        if (this.decoderStartAttempted && this.decoderFailed) {
            return false;
        }
        if (this.path == null) {
            return false;
        }

        this.decoderStartAttempted = true;
        try {
            this.grabber = createVideoGrabber(this.path);
            this.grabber.start();
            this.frameConverter = new Java2DFrameConverter();
            updateFrameInterval();
            this.decoderFailed = false;
            return true;
        } catch (Exception ignored) {
            this.decoderFailed = true;
            closeDecoder();
            return false;
        }
    }

    @Nullable
    private BufferedImage grabNextFrame() {
        if (this.grabber == null || this.frameConverter == null) {
            return null;
        }

        try {
            Frame frame = this.grabber.grabImage();
            if (frame == null) {
                return null;
            }

            BufferedImage image = this.frameConverter.convert(frame);
            return image == null ? null : copyArgb(image);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void rewindDecoder() {
        if (this.grabber == null) {
            return;
        }

        try {
            this.grabber.setTimestamp(0L);
        } catch (Exception ignored) {
            closeDecoder();
            this.decoderStartAttempted = false;
            this.decoderFailed = false;
        }
    }

    private void updateFrameInterval() {
        if (this.grabber == null) {
            this.frameNanos = DEFAULT_FRAME_NANOS;
            return;
        }

        double frameRate = this.grabber.getVideoFrameRate();
        if (!Double.isFinite(frameRate) || frameRate <= 0.0D) {
            frameRate = this.grabber.getFrameRate();
        }
        if (!Double.isFinite(frameRate) || frameRate <= 0.0D || frameRate > 240.0D) {
            frameRate = 30.0D;
        }
        this.frameNanos = Math.max(1_000_000L, Math.round(1_000_000_000.0D / frameRate));
    }

    private void ensureThumbnailTexture() {
        if (this.thumbnailTexture != null || this.thumbnailLoadAttempted) {
            return;
        }

        this.thumbnailLoadAttempted = true;
        BufferedImage image = loadFirstPreviewFrame();
        if (image == null) {
            return;
        }

        BufferedImage thumbnail = resizeCover(image, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        this.thumbnailWidth = thumbnail.getWidth();
        this.thumbnailHeight = thumbnail.getHeight();
        this.thumbnailTexture = new DynamicTexture(
            () -> "nyx-mainui-background-thumb-" + TEXTURE_IDS.incrementAndGet(),
            toNativeImage(thumbnail)
        );
    }

    @Nullable
    private BufferedImage loadFirstPreviewFrame() {
        BufferedImage image = this.builtIn ? readDefaultImage() : readPathImage(this.path);
        if (image != null) {
            return image;
        }
        return this.path == null ? null : decodeFirstFrame(this.path);
    }

    private void setCurrentImage(BufferedImage image) {
        BufferedImage argbImage = copyArgb(image);
        this.currentImage = argbImage;
        uploadCurrentImage(argbImage);
    }

    private void uploadCurrentImage(BufferedImage image) {
        NativeImage nativeImage = toNativeImage(image);
        if (this.texture == null || this.textureWidth != image.getWidth() || this.textureHeight != image.getHeight()) {
            closeTexture();
            this.texture = new DynamicTexture(
                () -> "nyx-mainui-background-" + TEXTURE_IDS.incrementAndGet(),
                nativeImage
            );
        } else {
            this.texture.setPixels(nativeImage);
            this.texture.upload();
        }

        this.textureWidth = image.getWidth();
        this.textureHeight = image.getHeight();
    }

    @Nullable
    private static BufferedImage readDefaultImage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return null;
        }

        try (InputStream input = minecraft.getResourceManager().open(DEFAULT_BACKGROUND)) {
            return ImageIO.read(input);
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private static BufferedImage readPathImage(@Nullable Path path) {
        if (path == null) {
            return null;
        }

        try (InputStream input = Files.newInputStream(path)) {
            return ImageIO.read(input);
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private static BufferedImage decodeFirstFrame(Path path) {
        try (FFmpegFrameGrabber previewGrabber = createVideoGrabber(path)) {
            previewGrabber.start();
            try {
                Java2DFrameConverter converter = new Java2DFrameConverter();
                Frame frame = previewGrabber.grabImage();
                if (frame == null) {
                    return null;
                }

                BufferedImage image = converter.convert(frame);
                return image == null ? null : copyArgb(image);
            } finally {
                previewGrabber.stop();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static FFmpegFrameGrabber createVideoGrabber(Path path) {
        FFmpegLogCallback.setLevel(AV_LOG_WARNING);
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path.toFile());
        grabber.setAudioStream(DISABLED_STREAM_INDEX);
        return grabber;
    }

    private static BufferedImage resizeCover(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage image = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double sourceAspect = source.getWidth() / (double)source.getHeight();
            double targetAspect = targetWidth / (double)targetHeight;
            int sourceX = 0;
            int sourceY = 0;
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            if (sourceAspect > targetAspect) {
                sourceWidth = Math.max(1, (int)Math.round(source.getHeight() * targetAspect));
                sourceX = (source.getWidth() - sourceWidth) / 2;
            } else if (sourceAspect < targetAspect) {
                sourceHeight = Math.max(1, (int)Math.round(source.getWidth() / targetAspect));
                sourceY = (source.getHeight() - sourceHeight) / 2;
            }

            graphics.drawImage(
                source,
                0,
                0,
                targetWidth,
                targetHeight,
                sourceX,
                sourceY,
                sourceX + sourceWidth,
                sourceY + sourceHeight,
                null
            );
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage copyArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return nativeImage;
    }

    private void closeTexture() {
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
        }
    }

    private void closeThumbnailTexture() {
        if (this.thumbnailTexture != null) {
            this.thumbnailTexture.close();
            this.thumbnailTexture = null;
        }
    }

    private void closeDecoder() {
        if (this.grabber != null) {
            try {
                this.grabber.close();
            } catch (Exception ignored) {
            }
            this.grabber = null;
        }
        this.frameConverter = null;
    }

    @Override
    public void close() {
        closeDecoder();
        closeTexture();
        closeThumbnailTexture();
        this.currentImage = null;
    }
}
