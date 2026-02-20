package com.irc.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.irc.IrcMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MessageReceiver {
    private final IrcClient ircClient;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private long lastTimestamp;
    private boolean running;

    public MessageReceiver(IrcClient ircClient) {
        this.ircClient = ircClient;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.gson = new Gson();
        // Initialize to 0 so we get all messages from the start
        // The worker will filter messages newer than this timestamp
        this.lastTimestamp = 0;
        this.running = false;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        // Poll for new messages every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (!running) {
                return;
            }

            try {
                String response = ircClient.getMessages(lastTimestamp);
                if (response != null && !response.isEmpty()) {
                    IrcMod.LOGGER.debug("Received response from worker: {}", response);
                    processMessages(response);
                } else {
                    IrcMod.LOGGER.debug("No messages received (response was null or empty)");
                }
            } catch (Exception e) {
                IrcMod.LOGGER.error("Error in message receiver", e);
            }
        }, 2, 2, TimeUnit.SECONDS);

        IrcMod.LOGGER.info("Message receiver started");
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
        IrcMod.LOGGER.info("Message receiver stopped");
    }

    private void processMessages(String response) {
        try {
            JsonObject json = gson.fromJson(response, JsonObject.class);

            if (!json.has("messages") || !json.get("messages").isJsonArray()) {
                IrcMod.LOGGER.warn("Response missing messages array: {}", response);
                return;
            }

            JsonArray messages = json.getAsJsonArray("messages");
            String currentSessionId = ircClient.getSessionId();

            IrcMod.LOGGER.debug("Processing {} messages, current sessionId: {}", messages.size(), currentSessionId);

            for (JsonElement element : messages) {
                JsonObject message = element.getAsJsonObject();
                long timestamp = message.get("timestamp").getAsLong();

                // Always update lastTimestamp to the highest timestamp we've seen
                // This ensures we don't miss messages even if they arrive out of order
                if (timestamp > lastTimestamp) {
                    lastTimestamp = timestamp;
                }

                String player = message.get("player").getAsString();
                String msg = message.get("message").getAsString();
                String sessionId = message.has("sessionId") ? message.get("sessionId").getAsString() : "";

                IrcMod.LOGGER.debug("Displaying message from {} (sessionId: {}): {}", player, sessionId, msg);
                displayMessage(player, msg);
            }
        } catch (Exception e) {
            IrcMod.LOGGER.error("Error processing messages", e);
        }
    }

    private void displayMessage(String player, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.execute(() -> {
                client.player.sendMessage(
                        Text.literal("§9[IRC] §b" + player + "§r: " + message),
                        false);
            });
        }
    }
}
