package io.github.seraphina.nyxclient.utility.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class WebUtility {
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    public static final String DEFAULT_USER_AGENT = "NyxClient/1.0";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private WebUtility() {
    }

    public static HttpClient client() {
        return HTTP_CLIENT;
    }

    public static URI uri(String url) {
        return URI.create(url);
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static HttpRequest.Builder requestBuilder(String url) {
        return requestBuilder(uri(url));
    }

    public static HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
            .timeout(DEFAULT_REQUEST_TIMEOUT)
            .header("User-Agent", DEFAULT_USER_AGENT);
    }

    public static String getString(String url) throws IOException, InterruptedException {
        return getString(uri(url));
    }

    public static String getString(URI uri) throws IOException, InterruptedException {
        return getString(uri, Map.of());
    }

    public static String getString(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return getString(uri(url), headers);
    }

    public static String getString(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest request = addHeaders(requestBuilder(uri)
            .GET()
            .header("Accept", "text/plain, application/json, */*"), headers).build();
        return requireSuccess(HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))).body();
    }

    public static byte[] getBytes(String url) throws IOException, InterruptedException {
        return getBytes(uri(url));
    }

    public static byte[] getBytes(URI uri) throws IOException, InterruptedException {
        return getBytes(uri, Map.of());
    }

    public static byte[] getBytes(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return getBytes(uri(url), headers);
    }

    public static byte[] getBytes(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest request = addHeaders(requestBuilder(uri)
            .GET()
            .header("Accept", "*/*"), headers).build();
        return requireSuccess(HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray())).body();
    }

    public static JsonElement getJson(String url) throws IOException, InterruptedException {
        return getJson(uri(url));
    }

    public static JsonElement getJson(URI uri) throws IOException, InterruptedException {
        return JsonParser.parseString(getString(uri, Map.of("Accept", "application/json")));
    }

    public static JsonObject getJsonObject(String url) throws IOException, InterruptedException {
        return getJsonObject(uri(url));
    }

    public static JsonObject getJsonObject(URI uri) throws IOException, InterruptedException {
        JsonElement element = getJson(uri);
        if (!element.isJsonObject()) {
            throw new IOException("Expected JSON object from " + uri);
        }

        return element.getAsJsonObject();
    }

    public static BufferedImage getImage(String url) throws IOException, InterruptedException {
        return getImage(uri(url));
    }

    public static BufferedImage getImage(URI uri) throws IOException, InterruptedException {
        return decodeImage(getBytes(uri, Map.of("Accept", "image/*")));
    }

    public static Path download(String url, Path output) throws IOException, InterruptedException {
        return download(uri(url), output);
    }

    public static Path download(URI uri, Path output) throws IOException, InterruptedException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.write(output, getBytes(uri));
        return output;
    }

    public static CompletableFuture<String> getStringAsync(String url) {
        return getStringAsync(uri(url));
    }

    public static CompletableFuture<String> getStringAsync(URI uri) {
        HttpRequest request = requestBuilder(uri)
            .GET()
            .header("Accept", "text/plain, application/json, */*")
            .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(WebUtility::requireSuccessUnchecked)
            .thenApply(HttpResponse::body);
    }

    public static CompletableFuture<byte[]> getBytesAsync(String url) {
        return getBytesAsync(uri(url));
    }

    public static CompletableFuture<byte[]> getBytesAsync(URI uri) {
        HttpRequest request = requestBuilder(uri)
            .GET()
            .header("Accept", "*/*")
            .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(WebUtility::requireSuccessUnchecked)
            .thenApply(HttpResponse::body);
    }

    public static CompletableFuture<JsonElement> getJsonAsync(String url) {
        return getJsonAsync(uri(url));
    }

    public static CompletableFuture<JsonElement> getJsonAsync(URI uri) {
        return getStringAsync(uri).thenApply(JsonParser::parseString);
    }

    public static CompletableFuture<BufferedImage> getImageAsync(String url) {
        return getImageAsync(uri(url));
    }

    public static CompletableFuture<BufferedImage> getImageAsync(URI uri) {
        HttpRequest request = requestBuilder(uri)
            .GET()
            .header("Accept", "image/*")
            .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(WebUtility::requireSuccessUnchecked)
            .thenApply(HttpResponse::body)
            .thenApply(WebUtility::decodeImageUnchecked);
    }

    public static boolean isSuccessStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static HttpRequest.Builder addHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach((name, value) -> {
            if (name != null && value != null && !name.isBlank()) {
                builder.setHeader(name, value);
            }
        });
        return builder;
    }

    private static <T> HttpResponse<T> requireSuccess(HttpResponse<T> response) throws IOException {
        if (!isSuccessStatus(response.statusCode())) {
            throw new IOException("HTTP " + response.statusCode() + " from " + response.uri());
        }

        return response;
    }

    private static <T> HttpResponse<T> requireSuccessUnchecked(HttpResponse<T> response) {
        try {
            return requireSuccess(response);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private static BufferedImage decodeImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("Unsupported image data");
        }

        return image;
    }

    private static BufferedImage decodeImageUnchecked(byte[] bytes) {
        try {
            return decodeImage(bytes);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }
}
