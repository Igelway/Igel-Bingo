package de.igelbingo;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IgelBingoCommands implements SimpleCommand {

    private final IgelBingoPlugin plugin;
    private final PluginConfig config;
    private final ProxyServer proxy;
    private final DockerServerManager docker;
    private final VelocityLang lang;
    private final AtomicBoolean chunkyResultBroadcast = new AtomicBoolean(false);

    public IgelBingoCommands(IgelBingoPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.proxy = plugin.getProxy();
        this.docker = plugin.getDockerManager();
        this.lang = plugin.getLang();
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length == 0) {
            source.sendMessage(deserialize(lang.prefixed("invalid-argument")));
            return;
        }

        if (!hasPermission(invocation)) {
            source.sendMessage(deserialize(lang.prefixed("no-permission")));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(args, invocation);
            case "prepare" -> handlePrepare(args);
            case "stop" -> handleStop();
            case "seed" -> handleSeed(args);
            case "state" -> handleState();
            case "cleanup" -> handleCleanup();
            default -> source.sendMessage(deserialize(lang.prefixed("invalid-argument")));
        }
    }

    private void handleStart(String[] args, Invocation invocation) {
        var source = invocation.source();
        if (plugin.getState() == GameState.RUNNING) {
            if (source instanceof com.velocitypowered.api.proxy.Player player
                    && player.getCurrentServer().isPresent()
                    && "game".equals(player.getCurrentServer().get().getServerInfo().getName())) {
                source.sendMessage(deserialize(lang.prefixed("game.already-on-game")));
                return;
            }
            routeAllToGame();
            broadcast(lang.prefixed("game.started"));
            return;
        }

        if (plugin.getState() != GameState.IDLE) {
            broadcast(lang.prefixed("game.already-running"));
            return;
        }

        boolean clean = args.length > 1 && "--clean".equals(args[1]);

        plugin.setState(GameState.STARTING);
        broadcast(lang.prefixed("game.starting"));

        boolean withChunky = config.chunkyPreload && !clean;

        docker.createGameServer(withChunky);

        docker.waitForServerReady().thenAccept(ready -> {
            if (!ready) {
                broadcast(lang.prefixed("game.start-failed"));
                plugin.setState(GameState.IDLE);
                return;
            }

            plugin.setState(GameState.RUNNING);
            routeAllToGame();
            broadcast(lang.prefixed("game.started"));
            if (config.lobbyStopOnGame) delayStopLobby();

            if (withChunky) {
                chunkyResultBroadcast.set(false);
                broadcastAdmins(lang.prefixed("prepare.chunky-started"));
                docker.waitForChunkyReady().thenAccept(chunkyDone -> {
                    if (chunkyResultBroadcast.compareAndSet(false, true)) {
                        broadcastAdmins(lang.prefixed(chunkyDone ? "prepare.chunky-done" : "prepare.chunky-timeout"));
                    }
                    docker.startGameIdleTimer(config.gameIdleTimeoutMinutes);
                });
            } else {
                docker.startGameIdleTimer(config.gameIdleTimeoutMinutes);
            }
        });
    }

    private void handlePrepare(String[] args) {
        if (plugin.getState() != GameState.IDLE) {
            broadcast(lang.prefixed("game.already-running"));
            return;
        }

        boolean withChunky = config.chunkyPreload;

        plugin.setState(GameState.PREPARING);

        if (args.length > 1) {
            docker.setSeed(args[1]);
        }

        broadcast(lang.prefixed(withChunky ? "prepare.starting" : "game.starting"));
        docker.createGameServer(withChunky);

        docker.waitForServerReady().thenAccept(ready -> {
            if (!ready) {
                broadcast(lang.prefixed(withChunky ? "prepare.start-failed" : "game.start-failed"));
                plugin.setState(GameState.IDLE);
                return;
            }

            plugin.setState(GameState.RUNNING);
            broadcast(lang.prefixed("game.started"));

            if (withChunky) {
                chunkyResultBroadcast.set(false);
                broadcastAdmins(lang.prefixed("prepare.chunky-started"));
                docker.waitForChunkyReady().thenAccept(chunkyDone -> {
                    if (chunkyResultBroadcast.compareAndSet(false, true)) {
                        broadcastAdmins(lang.prefixed(chunkyDone ? "prepare.chunky-done" : "prepare.chunky-timeout"));
                    }
                    docker.startGameIdleTimer(config.gameIdleTimeoutMinutes);
                });
            } else {
                docker.startGameIdleTimer(config.gameIdleTimeoutMinutes);
            }
        });
    }

    private void handleStop() {
        broadcast(lang.prefixed("game.stopping"));
        plugin.setState(GameState.STOPPING);

        if (!docker.isLobbyRunning() && config.lobbyAutoStart) {
            docker.startLobby();
        }

        routeAllToLimbo();

        docker.stopGameServer();
        docker.removeOldGameContainers();
        plugin.setState(GameState.IDLE);
        broadcast(lang.prefixed("game.stopped"));
    }

    private void routeAllToLimbo() {
        proxy.getServer("limbo").ifPresent(limbo -> {
            proxy.getAllPlayers().forEach(player -> {
                player.createConnectionRequest(limbo).fireAndForget();
            });
        });
    }

    private void handleSeed(String[] args) {
        if (args.length < 2) {
            String seed = docker.getCurrentSeed();
            if (seed != null) {
                broadcast(lang.prefixed("seed.current", "seed", seed));
            } else {
                broadcast(lang.prefixed("seed.none"));
            }
            return;
        }

        if ("clear".equalsIgnoreCase(args[1])) {
            docker.clearSeed();
            broadcast(lang.prefixed("seed.cleared"));
        } else {
            docker.setSeed(args[1]);
            broadcast(lang.prefixed("seed.set", "seed", args[1]));
        }
    }

    private void handleState() {
        String state = plugin.getState().name();
        broadcast(lang.prefixed("game.state", "state", state));
    }

    private void handleCleanup() {
        docker.removeOldGameContainers();
        broadcast(lang.prefixed("cleanup.done"));
    }

    // =========================================================================
    //    Routing
    // =========================================================================

    private void routeAllToGame() {
        proxy.getServer("game").ifPresent(game -> {
            proxy.getAllPlayers().forEach(player -> {
                player.createConnectionRequest(game).fireAndForget();
            });
        });
    }

    private void routeAllToLobby() {
        proxy.getServer("lobby").ifPresent(lobby -> {
            proxy.getAllPlayers().forEach(player -> {
                player.createConnectionRequest(lobby).fireAndForget();
            });
        });
    }

    public void routePlayerToLobby(com.velocitypowered.api.proxy.Player player) {
        proxy.getServer("lobby").ifPresent(lobby -> {
            player.sendMessage(deserialize(lang.prefixed("lobby.started")));
            player.createConnectionRequest(lobby).fireAndForget();
        });
    }

    // =========================================================================
    //    Helpers
    // =========================================================================

    private void broadcast(String message) {
        Component component = deserialize(message);
        proxy.getAllPlayers().forEach(p -> p.sendMessage(component));
        proxy.getConsoleCommandSource().sendMessage(component);
    }

    private void broadcastAdmins(String message) {
        Component component = deserialize(message);
        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission("igelbingo.admin"))
                .forEach(p -> p.sendMessage(component));
        proxy.getConsoleCommandSource().sendMessage(component);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (invocation.source().equals(proxy.getConsoleCommandSource())) {
            return true;
        }
        return invocation.source().hasPermission("igelbingo.admin");
    }

    private Component deserialize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private void delayStopLobby() {
        proxy.getScheduler().buildTask(plugin, docker::stopLobby)
                .delay(5, TimeUnit.SECONDS).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("start", "prepare", "stop", "seed", "state", "cleanup");
        }
        if (args.length == 1) {
            return List.of("start", "prepare", "stop", "seed", "state", "cleanup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if ("start".equals(args[0].toLowerCase()) && args.length == 2) {
            return List.of("--clean");
        }
        if ("seed".equals(args[0].toLowerCase()) && args.length == 2) {
            return List.of("clear");
        }
        return List.of();
    }
}
