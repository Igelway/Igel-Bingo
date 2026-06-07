package de.igelbingo;

import com.velocitypowered.api.plugin.PluginContainer;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PluginConfig {

    public boolean dockerMode = true;
    public String dockerHost = "unix:///var/run/docker.sock";

    // Admin
    public List<String> admins = new ArrayList<>();

    // Game server
    public String gameVersion = "26.1.2";
    public String purpurBuild = "LATEST";
    public List<String> gameOps = new ArrayList<>();
    public String gameMemory = "6G";
    public String gameDifficulty = "easy";
    public int gameViewDistance = 20;
    public int gameMaxPlayers = -1;
    public boolean pullGameImage = false;

    // Lobby
    public boolean lobbyAutoStart = true;
    public boolean lobbyStopOnGame = true;
    public int lobbyIdleTimeout = 300;
    public String lobbyMemory = "2G";
    public String lobbyDifficulty = "peaceful";
    public int lobbyViewDistance = 10;

    // Chunky
    public boolean chunkyPreload = false;
    public int chunkyOwRadius = 5000;
    public int chunkyNetherRadius = 2000;
    public int chunkyEndRadius = 2000;

    // Language
    public String language = "de_de";

    // Game data directory
    public String gameDataDir = "./data";

    // Docker images
    public String velocityImage = "ghcr.io/igelway/igel-bingo-velocity:latest";
    public String gameserverImage = "ghcr.io/igelway/igel-bingo-gameserver:latest";

    public PluginConfig(Path dataDirectory) {
        Path configFile = dataDirectory.resolve("plugins/igelbingo/config.yml");
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (Files.exists(configFile)) {
            loadFromFile(configFile);
        }
        loadFromEnv();
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile(Path file) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(Files.readString(file));
            if (data == null) return;

            dockerMode = getBool(data, "docker-mode", dockerMode);
            dockerHost = getString(data, "docker-host", dockerHost);
            admins = getStringList(data, "admins", admins);
            gameVersion = getString(data, "game-version", gameVersion);
            purpurBuild = getString(data, "purpur-build", purpurBuild);
            gameOps = getStringList(data, "game-ops", gameOps);
            gameMemory = getString(data, "game-memory", gameMemory);
            pullGameImage = getBool(data, "pull-game-image", pullGameImage);
            lobbyAutoStart = getBool(data, "lobby-auto-start", lobbyAutoStart);
            lobbyStopOnGame = getBool(data, "lobby-stop-on-game", lobbyStopOnGame);
            lobbyIdleTimeout = getInt(data, "lobby-idle-timeout", lobbyIdleTimeout);
            lobbyMemory = getString(data, "lobby-memory", lobbyMemory);
            chunkyPreload = getBool(data, "chunky-preload", chunkyPreload);
            chunkyOwRadius = getInt(data, "chunky-ow-radius", chunkyOwRadius);
            chunkyNetherRadius = getInt(data, "chunky-nether-radius", chunkyNetherRadius);
            chunkyEndRadius = getInt(data, "chunky-end-radius", chunkyEndRadius);
            language = getString(data, "language", language);
        } catch (Exception ignored) {
        }
    }

    private void loadFromEnv() {
        admins = envList("IGELBINGO_ADMINS", admins);
        gameOps = envList("IGELBINGO_GAME_OPS", gameOps);
        gameDifficulty = envString("IGELBINGO_GAME_DIFFICULTY", gameDifficulty);
        gameViewDistance = envInt("IGELBINGO_GAME_VIEW_DISTANCE", gameViewDistance);
        gameMaxPlayers = envInt("IGELBINGO_GAME_MAX_PLAYERS", gameMaxPlayers);
        gameMemory = envString("IGELBINGO_GAME_MEMORY", gameMemory);
        lobbyDifficulty = envString("IGELBINGO_LOBBY_DIFFICULTY", lobbyDifficulty);
        lobbyViewDistance = envInt("IGELBINGO_LOBBY_VIEW_DISTANCE", lobbyViewDistance);
        lobbyMemory = envString("IGELBINGO_LOBBY_MEMORY", lobbyMemory);
        lobbyAutoStart = envBool("IGELBINGO_LOBBY_AUTO_START", lobbyAutoStart);
        lobbyStopOnGame = envBool("IGELBINGO_LOBBY_STOP_ON_GAME", lobbyStopOnGame);
        lobbyIdleTimeout = envInt("IGELBINGO_LOBBY_IDLE_TIMEOUT", lobbyIdleTimeout);
        chunkyPreload = envBool("IGELBINGO_CHUNKY_PRELOAD", chunkyPreload);
        chunkyOwRadius = envInt("IGELBINGO_CHUNKY_OW_RADIUS", chunkyOwRadius);
        chunkyNetherRadius = envInt("IGELBINGO_CHUNKY_NETHER_RADIUS", chunkyNetherRadius);
        chunkyEndRadius = envInt("IGELBINGO_CHUNKY_END_RADIUS", chunkyEndRadius);
        pullGameImage = envBool("IGELBINGO_PULL_GAME_IMAGE", pullGameImage);
        if (System.getenv("GAME_DATA_DIR") != null) {
            gameDataDir = System.getenv("GAME_DATA_DIR");
        }
        if (System.getenv("IGELBINGO_VELOCITY_IMAGE") != null && !System.getenv("IGELBINGO_VELOCITY_IMAGE").isEmpty()) {
            velocityImage = System.getenv("IGELBINGO_VELOCITY_IMAGE");
        }
        if (System.getenv("IGELBINGO_GAMESERVER_IMAGE") != null && !System.getenv("IGELBINGO_GAMESERVER_IMAGE").isEmpty()) {
            gameserverImage = System.getenv("IGELBINGO_GAMESERVER_IMAGE");
        }
    }

    @SuppressWarnings("unchecked")
    private String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : def;
    }

    private boolean getBool(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : def;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key, List<String> def) {
        Object val = map.get(key);
        if (val instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) val) {
                if (item instanceof String) result.add((String) item);
            }
            return result;
        }
        return def;
    }

    private String envString(String key, String def) {
        String val = System.getenv(key);
        return val != null ? val : def;
    }

    private boolean envBool(String key, boolean def) {
        String val = System.getenv(key);
        if (val == null) return def;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    private int envInt(String key, int def) {
        String val = System.getenv(key);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private List<String> envList(String key, List<String> def) {
        String val = System.getenv(key);
        if (val == null || val.isEmpty()) return def;
        List<String> result = new ArrayList<>();
        for (String part : val.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}
