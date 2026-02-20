package com.irc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.irc.IrcMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class IrcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "irc-config.json";

    private static ConfigData config = new ConfigData();

    public static class ConfigData {
        public String workersUrl = "https://irc.typhfun.workers.dev";
        public String discordToken = "";
    }

    public static void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        File configFile = configPath.toFile();

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config = GSON.fromJson(reader, ConfigData.class);
                if (config == null) {
                    config = new ConfigData();
                }
                IrcMod.LOGGER.info("Loaded IRC config from {}", configPath);
            } catch (IOException e) {
                IrcMod.LOGGER.error("Failed to load IRC config", e);
                config = new ConfigData();
            }
        } else {
            // Create default config
            save();
        }
    }

    public static void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        File configFile = configPath.toFile();

        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            IrcMod.LOGGER.info("Saved IRC config to {}", configPath);
        } catch (IOException e) {
            IrcMod.LOGGER.error("Failed to save IRC config", e);
        }
    }

    public static String getWorkersUrl() {
        return config.workersUrl;
    }

    public static void setWorkersUrl(String url) {
        config.workersUrl = url;
        save();
    }

    public static String getDiscordToken() {
        return config.discordToken;
    }

    public static void setDiscordToken(String token) {
        config.discordToken = token;
        save();
    }
}
