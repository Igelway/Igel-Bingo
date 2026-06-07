package de.igelbingo;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "igelbingo",
        name = "IgelBingo",
        version = "1.0.0",
        description = "Docker-based Bingo game management for Velocity",
        authors = {"Igelway"}
)
public final class IgelBingoPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private VelocityLang lang;
    private DockerServerManager dockerManager;
    private IgelBingoCommands commands;

    private volatile GameState state = GameState.IDLE;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> lobbyIdleTask;

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("igelbingo", "main");

    @Inject
    public IgelBingoPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("IgelBingo Velocity Plugin starting...");

        config = new PluginConfig(dataDirectory);
        lang = new VelocityLang(config.language);

        proxy.getChannelRegistrar().register(CHANNEL);

        dockerManager = new DockerServerManager(config, proxy, logger);
        if (config.dockerMode) {
            dockerManager.init();
        }

        commands = new IgelBingoCommands(this);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("ib").build(),
                commands
        );

        // Initially stop lobby - it starts when first player joins
        if (config.dockerMode && config.lobbyAutoStart) {
            proxy.getScheduler().buildTask(this, () -> {
                if (dockerManager.isLobbyRunning()) {
                    dockerManager.stopLobby();
                    logger.info("Initial lobby stop (waiting for first player)");
                }
            }).delay(3, TimeUnit.SECONDS).schedule();
        }

        logger.info("IgelBingo Velocity Plugin ready.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        scheduler.shutdownNow();
        if (dockerManager != null) {
            dockerManager.close();
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!config.dockerMode || !config.lobbyAutoStart) return;

        proxy.getScheduler().buildTask(this, () -> {
            if (state == GameState.RUNNING) {
                // Game is running, route directly to game
                proxy.getServer("game").ifPresent(game -> {
                    proxy.getAllPlayers().forEach(player -> {
                        if (player.getCurrentServer().isEmpty() ||
                                !player.getCurrentServer().get().getServerInfo().getName().equals("game")) {
                            player.createConnectionRequest(game).fireAndForget();
                        }
                    });
                });
                return;
            }

            if (!dockerManager.isLobbyRunning()) {
                dockerManager.startLobby();
                event.getPlayer().sendMessage(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand()
                                .deserialize(lang.prefixed("lobby.starting"))
                );

                dockerManager.waitForLobbyReady().thenAccept(ready -> {
                    if (ready) {
                        commands.routePlayerToLobby(event.getPlayer());
                        startLobbyIdleTimer();
                    }
                });
            } else {
                startLobbyIdleTimer();
                commands.routePlayerToLobby(event.getPlayer());
            }
        }).schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        checkLobbyIdle();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;

        byte[] data = event.getData();
        String message = new String(data, java.nio.charset.StandardCharsets.UTF_8);

        if ("game_ended".equals(message)) {
            logger.info("Game ended signal received from game server");
            handleGameEnded();
        } else if ("game_started".equals(message)) {
            logger.info("Game started signal received from game server");
        }
    }

    // =========================================================================
    //    Game lifecycle
    // =========================================================================

    private void handleGameEnded() {
        if (state != GameState.RUNNING) return;

        broadcast(lang.prefixed("game.stopping"));
        state = GameState.STOPPING;

        routeAllToLobby();

        dockerManager.stopGameServer();
        dockerManager.removeOldGameContainers();

        if (config.lobbyAutoStart && !dockerManager.isLobbyRunning()) {
            dockerManager.startLobby();
        }

        state = GameState.IDLE;
        broadcast(lang.prefixed("game.stopped"));
    }

    private void routeAllToLobby() {
        proxy.getServer("lobby").ifPresent(lobby -> {
            proxy.getAllPlayers().forEach(player -> {
                player.createConnectionRequest(lobby).fireAndForget();
            });
        });
    }

    private void broadcast(String message) {
        var component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(message);
        proxy.getAllPlayers().forEach(p -> p.sendMessage(component));
        proxy.getConsoleCommandSource().sendMessage(component);
    }

    // =========================================================================
    //    Lobby idle management
    // =========================================================================

    public void startLobbyIdleTimer() {
        cancelLobbyIdleTimer();
        if (config.lobbyIdleTimeout <= 0) return;

        lobbyIdleTask = scheduler.schedule(() -> {
            checkLobbyIdle();
        }, config.lobbyIdleTimeout, TimeUnit.SECONDS);
    }

    public void cancelLobbyIdleTimer() {
        if (lobbyIdleTask != null && !lobbyIdleTask.isDone()) {
            lobbyIdleTask.cancel(false);
        }
        lobbyIdleTask = null;
    }

    private void checkLobbyIdle() {
        if (state == GameState.RUNNING) return;

        boolean hasPlayersOnLobby = proxy.getServer("lobby")
                .flatMap(s -> s.getPlayersConnected().stream().findAny())
                .isPresent();

        if (!hasPlayersOnLobby && dockerManager.isLobbyRunning()) {
            dockerManager.stopLobby();
            cancelLobbyIdleTimer();
            logger.info("Lobby stopped due to inactivity");

            // Send remaining players (on limbo) to limbo
            proxy.getServer("limbo").ifPresent(limbo -> {
                proxy.getAllPlayers().forEach(player -> {
                    if (player.getCurrentServer().isEmpty() ||
                            !player.getCurrentServer().get().getServerInfo().getName().equals("limbo")) {
                        player.createConnectionRequest(limbo).fireAndForget();
                    }
                });
            });
        }
    }

    // =========================================================================
    //    Accessors
    // =========================================================================

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public PluginConfig getConfig() {
        return config;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public DockerServerManager getDockerManager() {
        return dockerManager;
    }

    public VelocityLang getLang() {
        return lang;
    }
}
