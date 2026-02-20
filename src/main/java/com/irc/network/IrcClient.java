package com.irc.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.irc.IrcMod;
import com.irc.config.IrcConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class IrcClient {
    private final HttpClient httpClient;
    private final String workersUrl;
    private final Gson gson;
    private String sessionId;

    public IrcClient(String workersUrl) {
        this.workersUrl = workersUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.sessionId = UUID.randomUUID().toString();
    }

    public void sendMessage(String message) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "send");
            payload.addProperty("message", message);
            payload.addProperty("sessionId", sessionId);
            payload.addProperty("token", IrcConfig.getDiscordToken());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(workersUrl + "/api/irc"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            IrcMod.LOGGER.debug("Message sent successfully: {}", message);
                        } else {
                            IrcMod.LOGGER.error("Failed to send IRC message (status {}): {}", response.statusCode(),
                                    response.body());
                        }
                    })
                    .exceptionally(e -> {
                        IrcMod.LOGGER.error("Error sending IRC message", e);
                        return null;
                    });
        } catch (Exception e) {
            IrcMod.LOGGER.error("Error sending IRC message", e);
        }
    }

    public String getMessages(long lastTimestamp) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "receive");
            payload.addProperty("sessionId", sessionId);
            payload.addProperty("lastTimestamp", lastTimestamp);
            payload.addProperty("token", IrcConfig.getDiscordToken());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(workersUrl + "/api/irc"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                IrcMod.LOGGER.debug("Received messages response (lastTimestamp: {}): {}", lastTimestamp,
                        response.body());
                return response.body();
            } else if (response.statusCode() == 401) {
                // Token invalid - this is expected if user hasn't verified yet
                IrcMod.LOGGER.debug("Token invalid for receiving messages (user may need to verify): {}",
                        response.body());
                // Return empty messages array instead of null so receiver doesn't error
                return "{\"messages\":[]}";
            } else {
                IrcMod.LOGGER.error("Failed to receive IRC messages (status {}): {}", response.statusCode(),
                        response.body());
                return null;
            }
        } catch (Exception e) {
            IrcMod.LOGGER.error("Error receiving IRC messages", e);
            return null;
        }
    }

    public String getSessionId() {
        return sessionId;
    }
}
