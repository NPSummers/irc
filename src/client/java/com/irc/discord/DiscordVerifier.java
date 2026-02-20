package com.irc.discord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.irc.IrcMod;
import com.irc.IrcModClient;
import com.irc.config.IrcConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

public class DiscordVerifier {
    private final HttpClient httpClient;
    private final Gson gson;

    public DiscordVerifier() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    public void verifyTokenAsync(String token, Consumer<Boolean> callback) {
        new Thread(() -> {
            boolean valid = verifyToken(token);
            callback.accept(valid);
        }).start();
    }

    public boolean verifyToken(String token) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "verify");
            payload.addProperty("token", token);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(IrcConfig.getWorkersUrl() + "/api/discord/verify"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                boolean verified = json.has("verified") && json.get("verified").getAsBoolean();

                if (verified) {
                    IrcModClient.setVerified(true);
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.execute(() -> {
                            client.player.sendMessage(
                                    Text.literal("§a[IRC] Discord verification successful!"),
                                    false);
                        });
                    }
                }

                return verified;
            } else {
                IrcMod.LOGGER.error("Discord verification failed: {}", response.body());
                return false;
            }
        } catch (Exception e) {
            IrcMod.LOGGER.error("Error verifying Discord token", e);
            return false;
        }
    }

    public String getAuthUrl() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(IrcConfig.getWorkersUrl() + "/api/discord/authurl"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.has("authUrl")) {
                    return json.get("authUrl").getAsString();
                }
            }

            IrcMod.LOGGER.error("Failed to get auth URL: {}", response.body());
            return null;
        } catch (Exception e) {
            IrcMod.LOGGER.error("Error getting auth URL", e);
            return null;
        }
    }

    public void handleAuthCode(String code, Consumer<Boolean> callback) {
        new Thread(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("action", "exchange");
                payload.addProperty("code", code);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(IrcConfig.getWorkersUrl() + "/api/discord/callback"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.has("token") && json.has("verified")) {
                        String token = json.get("token").getAsString();
                        boolean verified = json.get("verified").getAsBoolean();

                        if (verified) {
                            IrcConfig.setDiscordToken(token);
                            callback.accept(true);
                        } else {
                            // Verification failed even though we got a token
                            // This shouldn't happen, but handle it gracefully
                            IrcMod.LOGGER.warn("Token received but verification failed");
                            callback.accept(false);
                        }
                    } else {
                        // No token in response, verification failed
                        callback.accept(false);
                    }
                } else {
                    String errorBody = response.body();
                    IrcMod.LOGGER.error("Failed to exchange auth code: {}", errorBody);

                    // Try to parse error for debug info and show user-friendly message
                    try {
                        JsonObject errorJson = gson.fromJson(errorBody, JsonObject.class);
                        String errorMessage = errorJson.has("error") ? errorJson.get("error").getAsString()
                                : "Unknown error";

                        // Show error message to user in chat
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player != null) {
                            client.execute(() -> {
                                client.player.sendMessage(
                                        Text.literal("§c[IRC] " + errorMessage),
                                        false);

                                // If guilds array is empty, suggest re-authorizing
                                if (errorJson.has("isEmptyGuilds") && errorJson.get("isEmptyGuilds").getAsBoolean()) {
                                    client.player.sendMessage(
                                            Text.literal(
                                                    "§e[IRC] Please type §6%irc link §eto re-authorize with the correct permissions."),
                                            false);
                                }
                            });
                        }

                        if (errorJson.has("debug")) {
                            IrcMod.LOGGER.error("Debug info: {}", errorJson.get("debug").toString());
                        }
                        if (errorJson.has("userGuildIds")) {
                            IrcMod.LOGGER.error("Your Discord server IDs: {}",
                                    errorJson.get("userGuildIds").toString());
                        }
                        if (errorJson.has("requiredServerId")) {
                            IrcMod.LOGGER.error("Required server ID: {}",
                                    errorJson.get("requiredServerId").getAsString());
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                        IrcMod.LOGGER.error("Error parsing error response", e);
                    }

                    callback.accept(false);
                }
            } catch (Exception e) {
                IrcMod.LOGGER.error("Error handling auth code", e);
                callback.accept(false);
            }
        }).start();
    }
}
