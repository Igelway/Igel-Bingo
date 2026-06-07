package de.igelbingo;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.transport.DockerHttpClient;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    private DockerClient dockerClient;

    private String currentSeed;

    public DockerServerManager(PluginConfig config, ProxyServer proxy, Logger logger) {
        this.config = config;
        this.proxy = proxy;
        this.logger = logger;
    }

    public void init() {
        DockerClientBuilder builder = DockerClientBuilder.getInstance(config.dockerHost);
        this.dockerClient = builder.build();
        logger.info("Docker client connected to " + config.dockerHost);
    }

    // =========================================================================
    //    Game Server
    // =========================================================================

    public void createGameServer(boolean withChunky) {
        removeOldGameContainers();

        String seed = currentSeed != null ? currentSeed : String.valueOf(System.currentTimeMillis());

        Map<String, String> env = new HashMap<>();
        env.put("EULA", "TRUE");
        env.put("TYPE", "PURPUR");
        env.put("VERSION", config.gameVersion);
        env.put("PURPUR_BUILD", config.purpurBuild);
        env.put("ONLINE_MODE", "FALSE");
        env.put("DIFFICULTY", config.gameDifficulty);
        env.put("VIEW_DISTANCE", String.valueOf(config.gameViewDistance));
        env.put("MAX_PLAYERS", String.valueOf(config.gameMaxPlayers));
        env.put("MEMORY", config.gameMemory);
        env.put("SPAWN_PROTECTION", "0");
        env.put("ALLOW_FLIGHT", "TRUE");
        env.put("LEVEL_SEED", seed);

        applyForwardedEnv(env, "IGELBINGO_GAME_");

        if (withChunky && config.chunkyPreload) {
            env.put("IGELBINGO_CHUNKY_ENABLED", "true");
            env.put("IGELBINGO_CHUNKY_OW_RADIUS", String.valueOf(config.chunkyOwRadius));
            env.put("IGELBINGO_CHUNKY_NETHER_RADIUS", String.valueOf(config.chunkyNetherRadius));
            env.put("IGELBINGO_CHUNKY_END_RADIUS", String.valueOf(config.chunkyEndRadius));
        }

        List<String> ops = config.gameOps;
        if (!ops.isEmpty()) {
            env.put("OPS", String.join(",", ops));
        }

        Path dataDir = Path.of(config.gameDataDir, "game");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            logger.severe("Failed to create game data directory: " + e.getMessage());
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode(NETWORK_NAME)
                .withBinds(Bind.parse(dataDir.toAbsolutePath() + ":/data"));

        CreateContainerCmd createCmd = dockerClient.createContainerCmd(config.gameserverImage)
                .withName(GAME_CONTAINER_NAME)
                .withEnv(toEnvList(env))
                .withHostConfig(hostConfig);

        if (config.pullGameImage) {
            try {
                dockerClient.pullImageCmd(config.gameserverImage).start().awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        createCmd.exec();
        dockerClient.startContainerCmd(GAME_CONTAINER_NAME).exec();

        logger.info("Game container created and started (seed: " + seed + ")");
    }

    public CompletableFuture<Boolean> waitForServerReady() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        RegisteredServer gameServer = proxy.getServer("game").orElse(null);
        if (gameServer == null) {
            future.complete(false);
            return future;
        }

        long start = System.currentTimeMillis();
        long timeout = 120_000;

        Thread thread = new Thread(() -> {
            while (System.currentTimeMillis() - start < timeout) {
                try {
                    InspectContainerResponse inspect = dockerClient.inspectContainerCmd(GAME_CONTAINER_NAME).exec();
                    InspectContainerResponse.ContainerState state = inspect.getState();
                    if (state != null && Boolean.TRUE.equals(state.getRunning())) {
                        // Also check Velocity can ping it
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
            future.complete(false);
        });
        thread.setDaemon(true);
        thread.start();

        return future;
    }

    public CompletableFuture<Boolean> waitForChunkyReady() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        RegisteredServer gameServer = proxy.getServer("game").orElse(null);
        if (gameServer == null) {
            future.complete(true);
            return future;
        }

        long start = System.currentTimeMillis();
        long timeout = 600_000; // 10 min for chunky

        Thread thread = new Thread(() -> {
            while (System.currentTimeMillis() - start < timeout) {
                try {
                    ExecCreateCmd execCreate = dockerClient.execCreateCmd(GAME_CONTAINER_NAME)
                            .withCmd("rcon-cli", "chunky", "progress")
                            .withAttachStdout(true)
                            .withAttachStderr(true);

                    ExecCreateCmdResponse execCreateResp = execCreate.exec();

                    StringBuilder output = new StringBuilder();
                    dockerClient.execStartCmd(execCreateResp.getId())
                            .exec(new ResultCallback.Adapter<>() {
                                @Override
                                public void onNext(Frame frame) {
                                    output.append(new String(frame.getPayload()));
                                }
                            })
                            .awaitCompletion();

                    String out = output.toString();
                    if (out.contains("Task is done") || out.contains("No tasks currently running") || out.contains("no tasks")) {
                        future.complete(true);
                        return;
                    }
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                    return;
                }
            }
            future.complete(true); // Timeout, continue anyway
        });
        thread.setDaemon(true);
        thread.start();

        return future;
    }

    public void stopGameServer() {
        try {
            dockerClient.stopContainerCmd(GAME_CONTAINER_NAME).withTimeout(10).exec();
        } catch (Exception ignored) {
        }
    }

    public void removeOldGameContainers() {
        try {
            for (var container : dockerClient.listContainersCmd().withShowAll(true).exec()) {
                for (String name : container.getNames()) {
                    if (name != null && name.contains("igelbingo-game")) {
                        try {
                            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                            logger.info("Removed old game container: " + container.getId());
                        } catch (Exception ignored) {
                        }
                    }
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
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(LOBBY_CONTAINER_NAME).exec();
            return inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (Exception e) {
            return false;
        }
    }

    public void startLobby() {
        try {
            dockerClient.startContainerCmd(LOBBY_CONTAINER_NAME).exec();
            logger.info("Lobby container started");
        } catch (Exception e) {
            logger.warning("Failed to start lobby container: " + e.getMessage());
        }
    }

    public void stopLobby() {
        try {
            dockerClient.stopContainerCmd(LOBBY_CONTAINER_NAME).withTimeout(10).exec();
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
        long timeout = 60_000;

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
    //    Helpers
    // =========================================================================

    private void applyForwardedEnv(Map<String, String> target, String prefix) {
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                String itzgKey = key.substring(prefix.length());
                if (!itzgKey.isEmpty() && !target.containsKey(itzgKey)) {
                    target.put(itzgKey, value);
                }
            }
        });
    }

    private List<String> toEnvList(Map<String, String> env) {
        List<String> result = new ArrayList<>();
        env.forEach((k, v) -> result.add(k + "=" + v));
        return result;
    }

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
        try {
            if (dockerClient instanceof Closeable) {
                ((Closeable) dockerClient).close();
            }
        } catch (IOException ignored) {
        }
    }
}
