package io.github.seraphina.nyx.client.utility.web;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MicrosoftUtility {
    private static final String ACCOUNT_PICTURE_REGISTRY_KEY =
        "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\AccountPicture\\Users\\";

    private static final Pattern REGISTRY_IMAGE_VALUE_PATTERN =
        Pattern.compile("^\\s*(Image\\d+)\\s+REG_\\S+\\s+(.+?)\\s*$");
    private static final Pattern IMAGE_SIZE_PATTERN = Pattern.compile("Image(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern ENVIRONMENT_VARIABLE_PATTERN = Pattern.compile("%([^%]+)%");
    private static final int COMMAND_TIMEOUT_SECONDS = 5;

    private MicrosoftUtility() {
    }

    public static String getCurrentComputerUserName() {
        String userName = firstNonBlank(
            System.getProperty("user.name"),
            System.getenv("USERNAME"),
            System.getenv("USER")
        );
        return userName == null ? "" : userName;
    }

    public static String getCurrentWindowsAccountName() {
        return getWhoamiAccountName().orElseGet(MicrosoftUtility::getCurrentComputerUserName);
    }

    public static Optional<String> getCurrentUserSid() {
        if (!isWindows()) {
            return Optional.empty();
        }

        return runCommand("whoami", "/user", "/fo", "csv", "/nh")
            .flatMap(MicrosoftUtility::parseSidFromWhoami);
    }

    public static Optional<Path> getCurrentMicrosoftAccountAvatarPath() {
        return getCurrentWindowsAccountAvatarPath();
    }

    public static Optional<Path> getCurrentWindowsAccountAvatarPath() {
        if (!isWindows()) {
            return Optional.empty();
        }

        Optional<String> sid = getCurrentUserSid();
        if (sid.isPresent()) {
            Optional<Path> registryPath = findRegistryAvatarPath(sid.get());
            if (registryPath.isPresent()) {
                return registryPath;
            }

            Optional<Path> publicPath = findPublicAvatarPath(sid.get());
            if (publicPath.isPresent()) {
                return publicPath;
            }
        }

        return findDefaultAccountPicturePath();
    }

    public static Optional<byte[]> getCurrentMicrosoftAccountAvatarBytes() {
        return getCurrentWindowsAccountAvatarBytes();
    }

    public static Optional<byte[]> getCurrentWindowsAccountAvatarBytes() {
        return getCurrentWindowsAccountAvatarPath().flatMap(MicrosoftUtility::readBytes);
    }

    public static Optional<BufferedImage> getCurrentMicrosoftAccountAvatarImage() {
        return getCurrentWindowsAccountAvatarImage();
    }

    public static Optional<BufferedImage> getCurrentWindowsAccountAvatarImage() {
        return getCurrentWindowsAccountAvatarPath().flatMap(MicrosoftUtility::readImage);
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static Optional<String> getWhoamiAccountName() {
        if (!isWindows()) {
            return Optional.empty();
        }

        return runCommand("whoami", "/user", "/fo", "csv", "/nh")
            .flatMap(MicrosoftUtility::parseAccountNameFromWhoami);
    }

    private static Optional<Path> findRegistryAvatarPath(String sid) {
        return runCommand("reg", "query", ACCOUNT_PICTURE_REGISTRY_KEY + sid)
            .flatMap(MicrosoftUtility::parseBestRegistryAvatarPath);
    }

    private static Optional<Path> parseBestRegistryAvatarPath(String output) {
        List<AvatarCandidate> candidates = new ArrayList<>();

        output.lines().forEach(line -> {
            Matcher matcher = REGISTRY_IMAGE_VALUE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                return;
            }

            Path path = toExpandedPath(matcher.group(2));
            if (isReadableFile(path)) {
                candidates.add(new AvatarCandidate(path, parseImageSize(matcher.group(1))));
            }
        });

        return bestCandidate(candidates);
    }

    private static Optional<Path> findPublicAvatarPath(String sid) {
        String publicDirectory = System.getenv("PUBLIC");
        if (publicDirectory == null || publicDirectory.isBlank()) {
            return Optional.empty();
        }

        return findBestImageInDirectory(Path.of(publicDirectory, "AccountPictures", sid));
    }

    private static Optional<Path> findDefaultAccountPicturePath() {
        String programData = System.getenv("ProgramData");
        if (programData == null || programData.isBlank()) {
            return Optional.empty();
        }

        return findBestImageInDirectory(Path.of(programData, "Microsoft", "User Account Pictures"));
    }

    private static Optional<Path> findBestImageInDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        try (Stream<Path> paths = Files.list(directory)) {
            List<AvatarCandidate> candidates = paths
                .filter(MicrosoftUtility::isReadableFile)
                .filter(MicrosoftUtility::isSupportedImagePath)
                .map(path -> new AvatarCandidate(path, parseImageSize(path.getFileName().toString())))
                .toList();
            return bestCandidate(candidates);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Path> bestCandidate(List<AvatarCandidate> candidates) {
        return candidates.stream()
            .max(Comparator.comparingInt(AvatarCandidate::size)
                .thenComparing(candidate -> candidate.path().toString()))
            .map(AvatarCandidate::path);
    }

    private static boolean isReadableFile(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }

    private static boolean isSupportedImagePath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".png")
            || fileName.endsWith(".jpg")
            || fileName.endsWith(".jpeg")
            || fileName.endsWith(".bmp");
    }

    private static int parseImageSize(String value) {
        Matcher matcher = IMAGE_SIZE_PATTERN.matcher(value);
        int size = 0;
        while (matcher.find()) {
            size = Math.max(size, Integer.parseInt(matcher.group(1)));
        }

        if (size != 0) {
            return size;
        }

        matcher = NUMBER_PATTERN.matcher(value);
        while (matcher.find()) {
            size = Math.max(size, Integer.parseInt(matcher.group()));
        }

        return size;
    }

    private static Path toExpandedPath(String rawPath) {
        String path = rawPath.strip();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }

        Matcher matcher = ENVIRONMENT_VARIABLE_PATTERN.matcher(path);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String replacement = System.getenv(matcher.group(1));
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(builder);

        return Path.of(builder.toString());
    }

    private static Optional<String> parseAccountNameFromWhoami(String output) {
        return parseWhoamiCsv(output).stream().findFirst().filter(value -> !value.isBlank());
    }

    private static Optional<String> parseSidFromWhoami(String output) {
        List<String> values = parseWhoamiCsv(output);
        if (values.size() < 2 || values.get(1).isBlank()) {
            return Optional.empty();
        }

        return Optional.of(values.get(1));
    }

    private static List<String> parseWhoamiCsv(String output) {
        return output.lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .findFirst()
            .map(MicrosoftUtility::parseCsvLine)
            .orElseGet(List::of);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }

        values.add(value.toString());
        return values;
    }

    private static Optional<byte[]> readBytes(Path path) {
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> readImage(Path path) {
        try {
            return Optional.ofNullable(ImageIO.read(path.toFile()));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> runCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return Optional.empty();
            }

            byte[] output = process.getInputStream().readAllBytes();
            if (process.exitValue() != 0) {
                return Optional.empty();
            }

            return Optional.of(new String(output, Charset.defaultCharset()));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return Optional.empty();
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private record AvatarCandidate(Path path, int size) {
    }
}
