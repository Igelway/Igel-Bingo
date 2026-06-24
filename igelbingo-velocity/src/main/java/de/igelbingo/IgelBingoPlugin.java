package de.igelbingo;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.UUID;
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

        commands = new IgelBingoCommands(this);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("ib").plugin(this).build(),
                commands
        );

        // Start idle timer if lobby is already running (from compose)
        if (config.dockerMode && config.lobbyAutoStart && dockerManager.isLobbyRunning()) {
            startLobbyIdleTimer();
            logger.info("Lobby is running, idle timer started (" + config.lobbyIdleTimeout + "s)");
        }

        logger.info("IgelBingo Velocity Plugin ready.");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (config.admins.isEmpty()) return;
        String username = event.getPlayer().getUsername();
        if (!config.admins.contains(username)) return;

        proxy.getScheduler().buildTask(this, () -> grantAdminPermission(event.getPlayer().getUniqueId(), username))
                .delay(2, TimeUnit.SECONDS).schedule();
    }

    private void grantAdminPermission(UUID uuid, String username) {
        try {
            if (proxy.getPluginManager().getPlugin("luckperms").isEmpty()) {
                logger.warning("LuckPerms not loaded — cannot grant igelbingo.admin to " + username);
                return;
            }
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = api.getUserManager().loadUser(uuid).get();
            if (user == null) {
                logger.warning("Could not load LuckPerms user for " + username);
                return;
            }
            net.luckperms.api.node.Node node = net.luckperms.api.node.Node.builder("igelbingo.admin").build();
            if (user.data().contains(node, net.luckperms.api.node.NodeEqualityPredicate.IGNORE_EXPIRY_TIME).asBoolean()) {
                logger.info(username + " already has igelbingo.admin, skipping");
                return;
            }
            user.data().add(node);
            api.getUserManager().saveUser(user);
            logger.info("Granted igelbingo.admin to " + username + " via LuckPerms API");
        } catch (Exception e) {
            logger.severe("Failed to grant igelbingo.admin to " + username + ": " + e.getMessage());
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        scheduler.shutdownNow();
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (!config.dockerMode || !config.lobbyAutoStart) return;

        var player = event.getPlayer();

        // Only redirect when player is on limbo
        if (player.getCurrentServer().isEmpty()) return;
        if (!"limbo".equals(player.getCurrentServer().get().getServerInfo().getName())) return;

        // Use a short delay to let the connection stabilize
        proxy.getScheduler().buildTask(this, () -> {
            if (state == GameState.RUNNING) {
                proxy.getServer("game").ifPresent(game -> {
                    if (player.getCurrentServer().isEmpty()
                            || !player.getCurrentServer().get().getServerInfo().getName().equals("game")) {
                        player.createConnectionRequest(game).fireAndForget();
                    }
                });
                return;
            }

            // Check if already on lobby — ServerPostConnectEvent fires once per connect
            if (player.getCurrentServer().isPresent()
                    && "lobby".equals(player.getCurrentServer().get().getServerInfo().getName())) {
                cancelLobbyIdleTimer();
                return;
            }

            if (!dockerManager.isLobbyRunning()) {
                dockerManager.startLobby();
            }

            player.sendMessage(
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand()
                            .deserialize(lang.prefixed("lobby.starting"))
            );

            dockerManager.waitForLobbyReady().thenAccept(ready -> {
                if (ready) {
                    proxy.getServer("lobby").ifPresent(lobby ->
                            player.createConnectionRequest(lobby).fireAndForget());
                }
            });
        }).delay(1, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (state == GameState.RUNNING) return;
        if (hasPlayersOnLobby()) return;
        startLobbyIdleTimer();
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
            setState(GameState.RUNNING);
        } else if ("chunky_done".equals(message)) {
            logger.info("Chunky preload done signal received from game server");
            dockerManager.onChunkyDone();
        }
    }

    // =========================================================================
    //    Game lifecycle
    // =========================================================================

    private void handleGameEnded() {
        if (state != GameState.RUNNING) return;

        broadcast(lang.prefixed("game.ended"));
        state = GameState.IDLE;

        // Don't route players — they stay on game server until /ib stop
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

    private boolean hasPlayersOnLobby() {
        return proxy.getServer("lobby")
                .flatMap(s -> s.getPlayersConnected().stream().findAny())
                .isPresent();
    }

    public void startLobbyIdleTimer() {
        cancelLobbyIdleTimer();
        if (config.lobbyIdleTimeout <= 0) return;

        lobbyIdleTask = scheduler.schedule(this::stopLobbyIfIdle,
                config.lobbyIdleTimeout, TimeUnit.SECONDS);
    }

    public void cancelLobbyIdleTimer() {
        if (lobbyIdleTask != null && !lobbyIdleTask.isDone()) {
            lobbyIdleTask.cancel(false);
        }
        lobbyIdleTask = null;
    }

    private void stopLobbyIfIdle() {
        if (state == GameState.RUNNING) return;
        if (hasPlayersOnLobby()) return;
        if (!dockerManager.isLobbyRunning()) return;

        dockerManager.stopLobby();
        lobbyIdleTask = null;
        logger.info("Lobby stopped due to inactivity");

        proxy.getServer("limbo").ifPresent(limbo -> {
            proxy.getAllPlayers().forEach(player -> {
                if (player.getCurrentServer().isEmpty()
                        || !player.getCurrentServer().get().getServerInfo().getName().equals("limbo")) {
                    player.createConnectionRequest(limbo).fireAndForget();
                }
            });
        });
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
