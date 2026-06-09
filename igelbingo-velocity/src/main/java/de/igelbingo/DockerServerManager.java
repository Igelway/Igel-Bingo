package de.igelbingo;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class DockerServerManager {

    private static final String GAME_CONTAINER_NAME = "igelbingo-game";
    private static final String LOBBY_CONTAINER_NAME = "igelbingo-lobby";
    private static final String NETWORK_NAME = "igelbingo-network";

    private final PluginConfig config;
    private final ProxyServer proxy;
    private final Logger logger;

    private String currentSeed;
    private CompletableFuture<Boolean> chunkyReadyFuture;

    public DockerServerManager(PluginConfig config, ProxyServer proxy, Logger logger) {
        this.config = config;
        this.proxy = proxy;
        this.logger = logger;
    }

    public void init() {
        logger.info("Docker client ready (CLI mode)");
    }

    // =========================================================================
    //    Game Server
    // =========================================================================

    public void createGameServer(boolean withChunky) {
        removeOldGameContainers();

        String seed = currentSeed != null ? currentSeed : String.valueOf(System.currentTimeMillis());

        Path dataDir = Path.of(config.gameDataDir, "game");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            logger.severe("Failed to create game data directory: " + e.getMessage());
        }

        String image = config.gameserverImage;

        if (config.pullGameImage) {
            docker("pull", image);
        }

        List<String> cmd = new ArrayList<>(List.of(
                "run", "-d",
                "--name", GAME_CONTAINER_NAME,
                "--network", NETWORK_NAME,
                "-v", dataDir.toAbsolutePath() + ":/data",
                "-e", "EULA=TRUE",
                "-e", "TYPE=PURPUR",
                "-e", "VERSION=" + config.gameVersion,
                "-e", "PURPUR_BUILD=" + config.purpurBuild,
                "-e", "ONLINE_MODE=FALSE",
                "-e", "SPAWN_PROTECTION=0",
                "-e", "ALLOW_FLIGHT=TRUE",
                "-e", "LEVEL_SEED=" + seed
        ));

        Set<String> forwardedKeys = new HashSet<>();
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("IGELBINGO_GAME_") && key.length() > 15) {
                String itzgKey = key.substring(15);
                cmd.add("-e");
                cmd.add(itzgKey + "=" + value);
                forwardedKeys.add(itzgKey);
            }
        });

        if (!config.gameOps.isEmpty() && !forwardedKeys.contains("OPS")) {
            cmd.add("-e");
            cmd.add("OPS=" + String.join(",", config.gameOps));
        }

        if (withChunky) {
            cmd.add("-e");
            cmd.add("IGELBINGO_CHUNKY_PRELOAD=true");
            cmd.add("-e");
            cmd.add("IGELBINGO_CHUNKY_OW_RADIUS=" + config.chunkyOwRadius);
            cmd.add("-e");
            cmd.add("IGELBINGO_CHUNKY_NETHER_RADIUS=" + config.chunkyNetherRadius);
            cmd.add("-e");
            cmd.add("IGELBINGO_CHUNKY_END_RADIUS=" + config.chunkyEndRadius);
        }

        cmd.add(image);

        docker(cmd.toArray(new String[0]));

        logger.info("Game container created and started (seed: " + seed + ")");
    }

    public CompletableFuture<Boolean> waitForServerReady() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        RegisteredServer gameServer = proxy.getServer("game").orElse(null);
        if (gameServer == null) {
            future.complete(false);
            return future;
        }

        Thread thread = new Thread(() -> {
            while (!future.isDone()) {
                try {
                    String state = dockerInspect(GAME_CONTAINER_NAME, "State.Running");
                    if ("true".equals(state)) {
                        try {
                            gameServer.ping().get(3, TimeUnit.SECONDS);
                            future.complete(true);
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        return future;
    }

    public CompletableFuture<Boolean> waitForChunkyReady() {
        chunkyReadyFuture = new CompletableFuture<>();

        Thread thread = new Thread(() -> {
            while (!chunkyReadyFuture.isDone()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (!chunkyReadyFuture.isDone()) {
                        chunkyReadyFuture.complete(false);
                    }
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        return chunkyReadyFuture;
    }

    public void onChunkyDone() {
        if (chunkyReadyFuture != null && !chunkyReadyFuture.isDone()) {
            chunkyReadyFuture.complete(true);
            logger.info("Chunky preload completed (signal from game plugin)");
        }
    }

    public void stopGameServer() {
        docker("stop", "-t", "10", GAME_CONTAINER_NAME);
    }

    public void removeOldGameContainers() {
        try {
            String out = docker("ps", "-aqf", "name=igelbingo-game");
            for (String line : out.split("\n")) {
                String id = line.trim();
                if (!id.isEmpty()) {
                    docker("rm", "-f", id);
                    logger.info("Removed old game container: " + id);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void cleanupGameData() {
        Path dataDir = Path.of(config.gameDataDir, "game");
        deleteRecursively(dataDir);
    }

    public void setSeed(String seed) {
        this.currentSeed = seed;
    }

    public void clearSeed() {
        this.currentSeed = null;
    }

    public String getCurrentSeed() {
        return currentSeed;
    }

    // =========================================================================
    //    Lobby Server
    // =========================================================================

    public boolean isLobbyRunning() {
        try {
            String state = dockerInspect(LOBBY_CONTAINER_NAME, "State.Running");
            return "true".equals(state);
        } catch (Exception e) {
            return false;
        }
    }

    public void startLobby() {
        try {
            docker("start", LOBBY_CONTAINER_NAME);
            logger.info("Lobby container started");
        } catch (Exception e) {
            logger.warning("Failed to start lobby container: " + e.getMessage());
        }
    }

    public void stopLobby() {
        try {
            docker("stop", "-t", "10", LOBBY_CONTAINER_NAME);
            logger.info("Lobby container stopped");
        } catch (Exception e) {
            logger.warning("Failed to stop lobby container: " + e.getMessage());
        }
    }

    public CompletableFuture<Boolean> waitForLobbyReady() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        RegisteredServer lobbyServer = proxy.getServer("lobby").orElse(null);
        if (lobbyServer == null) {
            future.complete(false);
            return future;
        }

        long start = System.currentTimeMillis();
        long timeout = 180_000;

        Thread thread = new Thread(() -> {
            while (System.currentTimeMillis() - start < timeout) {
                try {
                    lobbyServer.ping().get(2, TimeUnit.SECONDS);
                    future.complete(true);
                    return;
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                    return;
                }
            }
            future.complete(false);
        });
        thread.setDaemon(true);
        thread.start();

        return future;
    }

    // =========================================================================
    //    Docker CLI helpers
    // =========================================================================

    private String docker(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.addAll(Arrays.asList(args));
        return exec(cmd);
    }

    private String dockerInspect(String container, String format) {
        return docker("inspect", "-f", "{{." + format + "}}", container).trim();
    }

    private String exec(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 && command.size() > 1 && !"inspect".equals(command.get(1))) {
                logger.warning("Command exited with " + exitCode + ": " + String.join(" ", command));
            }

            return output.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute: " + String.join(" ", command), e);
        }
    }

    // =========================================================================
    //    Helpers
    // =========================================================================

    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    entries.forEach(this::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public void close() {
    }
}
