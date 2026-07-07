package io.github.seraphina.nyx.client.alt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.utility.web.WebUtility;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MicrosoftAuth {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final URI DEVICE_CODE_URL = URI.create("https://login.live.com/oauth20_connect.srf");
    private static final URI TOKEN_URL = URI.create("https://login.live.com/oauth20_token.srf");
    private static final URI XBOX_AUTH_URL = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH_URL = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MINECRAFT_AUTH_URL = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final URI MINECRAFT_PROFILE_URL = URI.create("https://api.minecraftservices.com/minecraft/profile");

    private MicrosoftAuth() {
    }

    public static CompletableFuture<Void> login(
        Consumer<String> onUrl,
        Consumer<AltManager.Account> onSuccess,
        Consumer<String> onError
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                DeviceCode deviceCode = requestDeviceCode();
                String loginUrl = deviceCode.loginUrl();

                openBrowser(loginUrl);
                runOnRenderThread(() -> onUrl.accept(loginUrl));

                String microsoftToken = pollForMicrosoftToken(deviceCode);
                XboxToken xboxToken = authenticateXboxLive(microsoftToken);
                XstsToken xstsToken = authenticateXsts(xboxToken);
                String minecraftToken = authenticateMinecraft(xstsToken);
                MinecraftProfile profile = requestMinecraftProfile(minecraftToken);

                AltManager.Account account = AltManager.Account.microsoft(profile.name(), profile.uuid(), minecraftToken);
                account.setXuid(xstsToken.xuid());

                runOnRenderThread(() -> onSuccess.accept(account));
            } catch (AuthException exception) {
                NyxClient.LOGGER.warn("Microsoft authentication failed: {}", exception.getMessage());
                runOnRenderThread(() -> onError.accept(exception.getMessage()));
            } catch (Exception exception) {
                NyxClient.LOGGER.error("Microsoft authentication failed", exception);
                runOnRenderThread(() -> onError.accept("Microsoft login failed."));
            }
        });
    }

    private static DeviceCode requestDeviceCode() throws IOException, InterruptedException, AuthException {
        String body = "client_id=" + WebUtility.encode(CLIENT_ID)
            + "&scope=" + WebUtility.encode("XboxLive.signin offline_access")
            + "&response_type=device_code";

        JsonObject json = postForm(DEVICE_CODE_URL, body);
        String deviceCode = requireString(json, "device_code", "Microsoft did not return a device code.");
        String userCode = requireString(json, "user_code", "Microsoft did not return a user code.");
        String verificationUri = readString(json, "verification_uri");
        String verificationUriComplete = readString(json, "verification_uri_complete");
        int interval = Math.max(1, readInt(json, "interval", 5));
        int expiresIn = Math.max(interval, readInt(json, "expires_in", 900));

        if (verificationUriComplete != null && !verificationUriComplete.isBlank()) {
            return new DeviceCode(deviceCode, userCode, verificationUriComplete, interval, expiresIn);
        }
        if (verificationUri == null || verificationUri.isBlank()) {
            throw new AuthException("Microsoft did not return a verification URL.");
        }

        return new DeviceCode(deviceCode, userCode, verificationUri + "?otc=" + WebUtility.encode(userCode), interval, expiresIn);
    }

    private static String pollForMicrosoftToken(DeviceCode deviceCode) throws IOException, InterruptedException, AuthException {
        long deadline = System.nanoTime() + Duration.ofSeconds(deviceCode.expiresInSeconds()).toNanos();
        int intervalSeconds = deviceCode.intervalSeconds();

        while (System.nanoTime() < deadline) {
            Thread.sleep(Duration.ofSeconds(intervalSeconds).toMillis());

            String body = "client_id=" + WebUtility.encode(CLIENT_ID)
                + "&device_code=" + WebUtility.encode(deviceCode.deviceCode())
                + "&grant_type=" + WebUtility.encode("urn:ietf:params:oauth:grant-type:device_code");

            HttpRequest request = formRequest(TOKEN_URL, body);
            HttpResponse<String> response = WebUtility.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject json = parseObject(response.body());

            String accessToken = readString(json, "access_token");
            if (accessToken != null && !accessToken.isBlank()) {
                return accessToken;
            }

            String error = readString(json, "error");
            if ("authorization_pending".equals(error)) {
                continue;
            }
            if ("slow_down".equals(error)) {
                intervalSeconds += 5;
                continue;
            }
            if ("authorization_declined".equals(error)) {
                throw new AuthException("Microsoft login was declined.");
            }
            if ("expired_token".equals(error)) {
                throw new AuthException("Microsoft login expired.");
            }

            throw new AuthException(errorMessage(json, "Microsoft login failed with HTTP " + response.statusCode() + "."));
        }

        throw new AuthException("Microsoft login timed out.");
    }

    private static XboxToken authenticateXboxLive(String microsoftToken) throws IOException, InterruptedException, AuthException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftToken);

        JsonObject payload = new JsonObject();
        payload.add("Properties", properties);
        payload.addProperty("RelyingParty", "http://auth.xboxlive.com");
        payload.addProperty("TokenType", "JWT");

        JsonObject json = postJson(XBOX_AUTH_URL, payload);
        return new XboxToken(requireString(json, "Token", "Xbox Live authentication failed."));
    }

    private static XstsToken authenticateXsts(XboxToken xboxToken) throws IOException, InterruptedException, AuthException {
        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken.token());

        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);

        JsonObject payload = new JsonObject();
        payload.add("Properties", properties);
        payload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        payload.addProperty("TokenType", "JWT");

        JsonObject json = postJson(XSTS_AUTH_URL, payload);
        String token = requireString(json, "Token", "XSTS authentication failed.");
        JsonObject xui = firstXui(json);
        String userHash = readString(xui, "uhs");
        if (userHash == null || userHash.isBlank()) {
            throw new AuthException("XSTS authentication did not return a user hash.");
        }

        return new XstsToken(token, userHash, readString(xui, "xid"));
    }

    private static String authenticateMinecraft(XstsToken xstsToken) throws IOException, InterruptedException, AuthException {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", "XBL3.0 x=" + xstsToken.userHash() + ";" + xstsToken.token());

        JsonObject json = postJson(MINECRAFT_AUTH_URL, payload);
        return requireString(json, "access_token", "Minecraft authentication failed.");
    }

    private static MinecraftProfile requestMinecraftProfile(String minecraftToken) throws IOException, InterruptedException, AuthException {
        HttpRequest request = WebUtility.requestBuilder(MINECRAFT_PROFILE_URL)
            .GET()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + minecraftToken)
            .build();

        JsonObject json = sendJson(request, "This Microsoft account does not own Minecraft Java Edition.");
        String name = requireString(json, "name", "Minecraft profile did not include a name.");
        String rawId = requireString(json, "id", "Minecraft profile did not include a UUID.");
        UUID uuid = parseUuid(rawId);
        if (uuid == null) {
            throw new AuthException("Minecraft profile returned an invalid UUID.");
        }

        return new MinecraftProfile(name, uuid);
    }

    private static JsonObject postForm(URI uri, String body) throws IOException, InterruptedException, AuthException {
        return sendJson(formRequest(uri, body), "HTTP request failed.");
    }

    private static JsonObject postJson(URI uri, JsonObject payload) throws IOException, InterruptedException, AuthException {
        HttpRequest request = WebUtility.requestBuilder(uri)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build();
        return sendJson(request, "HTTP request failed.");
    }

    private static HttpRequest formRequest(URI uri, String body) {
        return WebUtility.requestBuilder(uri)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .build();
    }

    private static JsonObject sendJson(HttpRequest request, String fallbackError) throws IOException, InterruptedException, AuthException {
        HttpResponse<String> response = WebUtility.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject json = parseObject(response.body());
        if (!WebUtility.isSuccessStatus(response.statusCode())) {
            throw new AuthException(errorMessage(json, fallbackError + " HTTP " + response.statusCode() + "."));
        }

        return json;
    }

    private static JsonObject parseObject(String body) throws AuthException {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(body);
        } catch (RuntimeException exception) {
            throw new AuthException("Authentication service returned invalid JSON.");
        }

        if (!element.isJsonObject()) {
            throw new AuthException("Authentication service returned unexpected JSON.");
        }

        return element.getAsJsonObject();
    }

    private static JsonObject firstXui(JsonObject json) {
        JsonElement displayClaimsElement = json.get("DisplayClaims");
        if (displayClaimsElement == null || !displayClaimsElement.isJsonObject()) {
            return new JsonObject();
        }

        JsonElement xuiElement = displayClaimsElement.getAsJsonObject().get("xui");
        if (xuiElement == null || !xuiElement.isJsonArray() || xuiElement.getAsJsonArray().isEmpty()) {
            return new JsonObject();
        }

        JsonElement first = xuiElement.getAsJsonArray().get(0);
        return first.isJsonObject() ? first.getAsJsonObject() : new JsonObject();
    }

    private static String errorMessage(JsonObject json, String fallback) {
        String description = readString(json, "error_description");
        if (description != null && !description.isBlank()) {
            return description;
        }

        String message = readString(json, "message");
        if (message != null && !message.isBlank()) {
            return message;
        }

        String error = readString(json, "error");
        return error == null || error.isBlank() ? fallback : error;
    }

    private static String requireString(JsonObject json, String key, String message) throws AuthException {
        String value = readString(json, key);
        if (value == null || value.isBlank()) {
            throw new AuthException(message);
        }

        return value;
    }

    private static String readString(JsonObject json, String key) {
        if (json == null || key == null) {
            return null;
        }

        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int readInt(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String uuid = value.strip();
        if (uuid.length() == 32) {
            uuid = uuid.substring(0, 8) + "-"
                + uuid.substring(8, 12) + "-"
                + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-"
                + uuid.substring(20);
        }

        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder builder;
            if (os.contains("win")) {
                builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                builder = new ProcessBuilder("open", url);
            } else {
                builder = new ProcessBuilder("xdg-open", url);
            }

            Process process = builder.start();
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        } catch (IOException | RuntimeException exception) {
            NyxClient.LOGGER.warn("Failed to open browser for Microsoft authentication", exception);
        }
    }

    private static void runOnRenderThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(action);
        } else {
            action.run();
        }
    }

    private record DeviceCode(String deviceCode, String userCode, String loginUrl, int intervalSeconds, int expiresInSeconds) {
    }

    private record XboxToken(String token) {
    }

    private record XstsToken(String token, String userHash, String xuid) {
    }

    private record MinecraftProfile(String name, UUID uuid) {
    }

    private static final class AuthException extends Exception {
        private AuthException(String message) {
            super(message);
        }
    }
}
