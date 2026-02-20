package com.irc;

import com.irc.config.IrcConfig;
import com.irc.discord.DiscordVerifier;
import com.irc.network.IrcClient;
import com.irc.network.MessageReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class IrcModClient implements ClientModInitializer {
    private static IrcModClient instance;
    private static IrcClient ircClient;
    private static MessageReceiver messageReceiver;
    private static DiscordVerifier discordVerifier;
    private static boolean isVerified = false;

    @Override
    public void onInitializeClient() {
        instance = this;
        IrcMod.LOGGER.info("IRC Mod Client initialized!");

        // Load configuration
        IrcConfig.load();

        // Initialize Discord verifier
        discordVerifier = new DiscordVerifier();

        // Initialize IRC client
        ircClient = new IrcClient(IrcConfig.getWorkersUrl());

        // Initialize message receiver
        messageReceiver = new MessageReceiver(ircClient);
        messageReceiver.start();

        // Register chat message handler
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message != null && message.startsWith("%irc ")) {
                handleIrcCommand(message.substring(5));
                return false; // Cancel the original message
            }
            return true; // Allow normal messages
        });

        // Check if user needs to verify
        checkDiscordVerification();
    }

    private void handleIrcCommand(String message) {
        String trimmed = message.trim();

        // Handle %irc link (with or without code)
        if (trimmed.equals("link") || trimmed.startsWith("link ")) {
            // If just "link" or "link " with no code, open auth page
            if (trimmed.equals("link")) {
                MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§a[IRC] Opening Discord verification page..."),
                        false);
                openDiscordAuthPage();
                return;
            }

            // Handle %irc link <code> for Discord verification
            String code = trimmed.substring(5).trim();
            if (code.isEmpty()) {
                // No code provided, open the Discord OAuth page
                MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§a[IRC] Opening Discord verification page..."),
                        false);
                openDiscordAuthPage();
                return;
            }

            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§a[IRC] Verifying Discord code..."),
                    false);

            discordVerifier.handleAuthCode(code, (verified) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.execute(() -> {
                        if (verified) {
                            mc.player.sendMessage(
                                    Text.literal(
                                            "§a[IRC] Discord verification successful! You can now use %irc <message>"),
                                    false);
                            IrcModClient.setVerified(true);
                        }
                        // Error messages are already shown by DiscordVerifier.handleAuthCode
                        // No need to show a duplicate message here
                    });
                }
            });
            return;
        }

        // Handle regular %irc <message>
        if (!isVerified) {
            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§c[IRC] You must verify with Discord first! Opening browser..."),
                    false);
            openDiscordAuthPage();
            return;
        }

        if (trimmed.isEmpty()) {
            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§c[IRC] Usage: %irc <message>"),
                    false);
            return;
        }

        ircClient.sendMessage(trimmed);

        // Show confirmation
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§a[IRC] Message sent!"),
                false);
    }

    private void checkDiscordVerification() {
        // Check if user has a stored token
        String token = IrcConfig.getDiscordToken();
        if (token != null && !token.isEmpty()) {
            // Verify token is still valid
            discordVerifier.verifyTokenAsync(token, (valid) -> {
                isVerified = valid;
                if (valid) {
                    IrcMod.LOGGER.info("Discord verification token is valid");
                } else {
                    IrcMod.LOGGER.warn("Discord verification token is invalid, user needs to re-verify");
                    // Automatically open auth page if token is invalid
                    openDiscordAuthPage();
                }
            });
        } else {
            // No token found, automatically open auth page
            IrcMod.LOGGER.info("No Discord token found, opening auth page");
            openDiscordAuthPage();
        }
    }

    private void openDiscordAuthPage() {
        new Thread(() -> {
            try {
                String authUrl = discordVerifier.getAuthUrl();
                if (authUrl != null && !authUrl.isEmpty()) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.execute(() -> {
                            client.player.sendMessage(
                                    Text.literal("§a[IRC] Opening Discord verification page in browser..."),
                                    false);
                        });
                    }

                    // Open browser using cross-platform approach
                    if (openBrowser(authUrl)) {
                        IrcMod.LOGGER.info("Opened Discord auth page: {}", authUrl);
                    } else {
                        IrcMod.LOGGER.warn("Could not open browser automatically. Please visit: {}", authUrl);
                        if (client.player != null) {
                            client.execute(() -> {
                                client.player.sendMessage(
                                        Text.literal("§e[IRC] Please visit: " + authUrl),
                                        false);
                            });
                        }
                    }
                } else {
                    IrcMod.LOGGER.error("Failed to get Discord auth URL");
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.execute(() -> {
                            client.player.sendMessage(
                                    Text.literal(
                                            "§c[IRC] Failed to get Discord auth URL. Check your workers URL configuration."),
                                    false);
                        });
                    }
                }
            } catch (Exception e) {
                IrcMod.LOGGER.error("Error opening Discord auth page", e);
            }
        }).start();
    }

    private boolean openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Runtime rt = Runtime.getRuntime();

            if (os.indexOf("win") >= 0) {
                // Windows
                rt.exec("rundll32 url.dll,FileProtocolHandler " + url);
                return true;
            } else if (os.indexOf("mac") >= 0) {
                // Mac
                rt.exec("open " + url);
                return true;
            } else if (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0) {
                // Linux/Unix
                String[] browsers = { "google-chrome", "firefox", "mozilla", "epiphany", "konqueror",
                        "netscape", "opera", "links", "lynx" };

                StringBuilder cmd = new StringBuilder();
                for (int i = 0; i < browsers.length; i++) {
                    if (i == 0) {
                        cmd.append(String.format("%s \"%s\"", browsers[i], url));
                    } else {
                        cmd.append(String.format(" || %s \"%s\"", browsers[i], url));
                    }
                }

                rt.exec(new String[] { "sh", "-c", cmd.toString() });
                return true;
            } else {
                IrcMod.LOGGER.warn("Unknown operating system: {}", os);
                return false;
            }
        } catch (Exception e) {
            IrcMod.LOGGER.error("Error opening browser", e);
            return false;
        }
    }

    public static void setVerified(boolean verified) {
        isVerified = verified;
    }

    public static boolean isVerified() {
        return isVerified;
    }

    public static DiscordVerifier getDiscordVerifier() {
        return discordVerifier;
    }

    public static IrcClient getIrcClient() {
        return ircClient;
    }

    public static MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }
}
