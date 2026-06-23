package de.igelbingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.*;
import org.bukkit.GameRules;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class IgelBingoGamePlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private static final String CHANNEL = "igelbingo:main";

    private GamePhase phase = GamePhase.IDLE;
    private final GameConfig gameConfig = new GameConfig();
    private boolean firstStart = false;

    private BukkitTask countdownTask;
    private int countdownRemaining;

    private BukkitTask elytraTask;
    private BukkitTask fireworksTask;

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();
        loadGameConfig();

        performFirstStart();

        extractResourcePack();

        if ("true".equalsIgnoreCase(System.getenv("IGELBINGO_CHUNKY_PRELOAD"))) {
            startChunkyPreload();
        }

        getLogger().info("IgelBingo Game Plugin enabled.");
    }

    @Override
    public void onDisable() {
        cancelAllTasks();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        getLogger().info("IgelBingo Game Plugin disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        World world = event.getPlayer().getWorld();
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    // =========================================================================
    //    Config
    // =========================================================================

    private void loadGameConfig() {
        var config = getConfig();
        gameConfig.countdownSeconds = config.getInt("countdown-seconds", 30);
        gameConfig.worldborderOverworld = config.getInt("worldborder-overworld", 10000);
        gameConfig.worldborderNether = config.getInt("worldborder-nether", 4000);
        gameConfig.worldborderEnd = config.getInt("worldborder-end", 8000);
        gameConfig.elytraReplenishInterval = config.getInt("elytra-replenish-interval", 8);
        gameConfig.elytraReplenishAmount = config.getInt("elytra-replenish-amount", 64);
    }

    // =========================================================================
    //    Commands (called by BingoReloaded config hooks)
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) return false;

        switch (args[0].toLowerCase()) {
            case "start":
                startPreGameSequence();
                return true;
            case "end":
                endGameSequence();
                return true;
            case "firststart":
                performFirstStart();
                return true;
            case "abort":
                abortCountdown();
                return true;
        }
        return false;
    }

    // =========================================================================
    //    First Start (world init)
    // =========================================================================

    public void performFirstStart() {
        World overworld = getServer().getWorlds().getFirst();

        // Set worldspawn to 0,~,0
        overworld.setSpawnLocation(new Location(overworld, 0, overworld.getHighestBlockYAt(0, 0) + 1, 0));

        // Worldborders
        WorldBorder owBorder = overworld.getWorldBorder();
        owBorder.setCenter(0, 0);
        owBorder.setSize(gameConfig.worldborderOverworld);

        World nether = getServer().getWorld(overworld.getName() + "_nether");
        if (nether != null) {
            nether.getWorldBorder().setCenter(0, 0);
            nether.getWorldBorder().setSize(gameConfig.worldborderNether);
        }

        World end = getServer().getWorld(overworld.getName() + "_the_end");
        if (end != null) {
            end.getWorldBorder().setCenter(0, 0);
            end.getWorldBorder().setSize(gameConfig.worldborderEnd);
        }

        // Gamerules (from bingo_purpur)
        applyGameRules(overworld);
        firstStart = true;

        getLogger().info("World initialized with bingo settings.");
    }

    private void applyGameRules(World world) {
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setGameRule(GameRules.FREEZE_DAMAGE, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.REDUCED_DEBUG_INFO, true);
        world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.ELYTRA_MOVEMENT_CHECK, true);
    }

    // =========================================================================
    //    Pre-Game Sequence (delegates to bingo_purpur datapack)
    // =========================================================================

    public void startPreGameSequence() {
        if (phase != GamePhase.IDLE) return;

        phase = GamePhase.COUNTDOWN;
        countdownRemaining = gameConfig.countdownSeconds;

        suppressBacNotifications();

        // Run the datapack's bingo_start function (handles countdown, kit, lobby)
        getServer().dispatchCommand(getServer().getConsoleSender(),
                "function bingo_setup:bingo_start/bingo_start");

        // Show abort button to players with igelbingo.admin permission
        showAbortDialog();

        // Notify Velocity and release after countdown
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                phase = GamePhase.RUNNING;
                countdownTask = null;
                sendPluginMessage("game_started");
                getLogger().info("Game started (countdown complete).");
            }
        }.runTaskLater(this, 20L * gameConfig.countdownSeconds + 20L);
    }

    public void abortCountdown() {
        if (phase != GamePhase.COUNTDOWN) return;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        // Cancel datapack countdown (revert to lobby state)
        getServer().dispatchCommand(getServer().getConsoleSender(),
                "function bingo_setup:bingo_start/abort_countdown/revert_countdown");

        // End BingoReloaded game
        getServer().dispatchCommand(getServer().getConsoleSender(), "bingo end");
        getServer().dispatchCommand(getServer().getConsoleSender(), "autobingo end");

        phase = GamePhase.IDLE;
        broadcast("&6[Igel-Bingo] &cCountdown abgebrochen.");
        getLogger().info("Countdown aborted, BingoReloaded game ended.");
    }

    private void showAbortDialog() {
        Component msg = Component.text()
                .append(Component.text("[Igel-Bingo] ", NamedTextColor.GOLD))
                .append(Component.text("[Countdown abbrechen]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/igelbingo abort")))
                .build();

        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission("igelbingo.admin")) {
                player.sendMessage(msg);
            }
        }
    }

    // =========================================================================
    //    End Game Sequence
    // =========================================================================

    public void endGameSequence() {
        phase = GamePhase.ENDING;

        cancelAllTasks();

        getServer().dispatchCommand(getServer().getConsoleSender(),
                "function bingo_setup:bingo_end/bingo_end");

        sendPluginMessage("game_ended");
        phase = GamePhase.IDLE;

        getLogger().info("Game ended (datapack + plugin messaging).");
    }

    // =========================================================================
    //    Starter Kit
    // =========================================================================

    private void giveStarterKit(Player player) {
        // Unbreakable Elytra
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        var meta = (Damageable) elytra.getItemMeta();
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Igel-Bingo Elytra", NamedTextColor.GOLD));
        meta.addEnchant(Enchantment.PROTECTION, 0, true);
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        // Fireworks (slot 4, power 1)
        ItemStack fireworks = new ItemStack(Material.FIREWORK_ROCKET, 64);
        FireworkMeta fwMeta = (FireworkMeta) fireworks.getItemMeta();
        fwMeta.setPower(1);
        fireworks.setItemMeta(fwMeta);
        player.getInventory().setItem(4, fireworks);

        // Shovel (Silk Touch I, unbreakable, slots 0-3)
        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        var shovelMeta = shovel.getItemMeta();
        shovelMeta.setUnbreakable(true);
        shovelMeta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
        shovelMeta.displayName(Component.text("◠◡◠◡◠◡◠◡◠◡◠◡Schaufel◠◡◠◡◠◡◠◡◠◡◠◡", NamedTextColor.AQUA));
        shovel.setItemMeta(shovelMeta);
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing == null || existing.isEmpty()) {
                player.getInventory().setItem(slot, shovel);
                break;
            }
        }
    }

    // =========================================================================
    //    Elytra & Fireworks Replenish
    // =========================================================================

    private void startElytraTasks() {
        // Elytra check every second
        elytraTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;

                    ItemStack chest = player.getInventory().getChestplate();
                    if (chest == null || chest.getType() != Material.ELYTRA) {
                        giveStarterElytra(player);
                    }
                }
            }
        }.runTaskTimer(this, 40L, 20L);

        // Firework replenish (slot 4, fills to 64)
        fireworksTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;

                    ItemStack slot4 = player.getInventory().getItem(4);
                    if (slot4 == null || slot4.isEmpty()) {
                        ItemStack fw = new ItemStack(Material.FIREWORK_ROCKET, Math.min(gameConfig.elytraReplenishAmount, 64));
                        FireworkMeta fwMeta = (FireworkMeta) fw.getItemMeta();
                        fwMeta.setPower(1);
                        fw.setItemMeta(fwMeta);
                        player.getInventory().setItem(4, fw);
                    } else if (slot4.getType() == Material.FIREWORK_ROCKET) {
                        int current = slot4.getAmount();
                        if (current < 64) {
                            slot4.setAmount(Math.min(current + gameConfig.elytraReplenishAmount, 64));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L * gameConfig.elytraReplenishInterval, 20L * gameConfig.elytraReplenishInterval);
    }

    private void giveStarterElytra(Player player) {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        var meta = (Damageable) elytra.getItemMeta();
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Igel-Bingo Elytra", NamedTextColor.GOLD));
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);
    }

    // =========================================================================
    //    Sound Effects
    // =========================================================================

    private void playCountdownSound(int seconds) {
        for (Player player : getServer().getOnlinePlayers()) {
            if (seconds <= 3) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1f);
            }
        }
    }

    // =========================================================================
    //    BAC Silencing
    // =========================================================================

    private void suppressBacNotifications() {
        ConsoleCommandSender console = getServer().getConsoleSender();

        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_task_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_goal_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_challenge_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_super_challenge_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_milestone_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_set_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_set_server1");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/msg_set_vanilla_msg");

        dispatchSilently(console, "execute as @a run function blazeandcave:config/intro_msg_off");

        dispatchSilently(console, "execute as @a run function blazeandcave:config/trophies_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/item_rewards_off");
        dispatchSilently(console, "execute as @a run function blazeandcave:config/exp_rewards_off");

        getLogger().info("BAC notifications, toasts, sounds and rewards silenced.");
    }

    private void dispatchSilently(ConsoleCommandSender console, String command) {
        getServer().dispatchCommand(console, command);
    }

    // =========================================================================
    //    Resource Pack
    // =========================================================================

    private void extractResourcePack() {
        Path outputDir = getDataFolder().toPath().resolve("resourcepack");
        Path outputZip = outputDir.resolve("IgelBingo_Resources.zip");

        if (Files.exists(outputZip)) {
            getLogger().info("Resource pack already extracted, skipping.");
            return;
        }

        try {
            Files.createDirectories(outputDir);
            Path sourceDir = extractResourceDir("resourcepack", outputDir.resolve("_tmp"));
            zipDirectory(sourceDir, outputZip);
            deleteRecursively(sourceDir);
            getLogger().info("Resource pack extracted to " + outputZip);
        } catch (IOException e) {
            getLogger().severe("Failed to extract resource pack: " + e.getMessage());
        }
    }

    private Path extractResourceDir(String resourcePath, Path targetDir) throws IOException {
        var codeSource = getClass().getProtectionDomain().getCodeSource();
        if (codeSource == null) throw new IOException("Cannot access JAR");

        try {
            Path jarPath = Path.of(codeSource.getLocation().toURI());
            try (FileSystem fs = FileSystems.newFileSystem(jarPath)) {
                Path sourceDir = fs.getPath(resourcePath);
                Files.createDirectories(targetDir);
                try (var stream = Files.walk(sourceDir)) {
                    stream.forEach(source -> {
                        Path target = targetDir.resolve(sourceDir.relativize(source).toString());
                        try {
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(target);
                            } else {
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract resource directory", e);
        }
        return targetDir;
    }

    private void zipDirectory(Path sourceDir, Path outputZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            try (var stream = Files.walk(sourceDir)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    String entryName = sourceDir.relativize(file).toString();
                    try {
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    } catch (IOException ignored) {
                    }
                });
            }
        }
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
    // =========================================================================

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        String msg = new String(message);
        getLogger().info("Received plugin message: " + msg);

        switch (msg) {
            case "stop_game":
                endGameSequence();
                break;
            case "start_game":
                startPreGameSequence();
                break;
        }
    }

    public void sendPluginMessage(String message) {
        byte[] data = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (!getServer().getOnlinePlayers().isEmpty()) {
            Player first = getServer().getOnlinePlayers().iterator().next();
            first.sendPluginMessage(this, CHANNEL, data);
        } else {
            getServer().sendPluginMessage(this, CHANNEL, data);
        }
    }

    // =========================================================================
    //    Chunky Preload
    // =========================================================================

    private void startChunkyPreload() {
        try {
            Class<?> chunkyApiClass = Class.forName("org.popcraft.chunky.api.ChunkyAPI");
            Object chunky = getServer().getServicesManager().load(chunkyApiClass);
            if (chunky == null) {
                getLogger().warning("Chunky not installed — skipping preload");
                sendPluginMessage("chunky_done");
                return;
            }

            int owRadius = envInt("IGELBINGO_CHUNKY_OW_RADIUS", 5000);
            int netherRadius = envInt("IGELBINGO_CHUNKY_NETHER_RADIUS", 2000);
            int endRadius = envInt("IGELBINGO_CHUNKY_END_RADIUS", 2000);

            int[] completed = {0};
            int[] started = {0};

            Class<?> genEventClass = Class.forName("org.popcraft.chunky.api.event.task.GenerationCompleteEvent");
            java.lang.reflect.Method onCompleteMethod = chunkyApiClass.getMethod("onGenerationComplete", java.util.function.Consumer.class);
            onCompleteMethod.invoke(chunky, (java.util.function.Consumer<Object>) event -> {
                completed[0]++;
                try {
                    java.lang.reflect.Method worldMethod = genEventClass.getMethod("world");
                    String world = (String) worldMethod.invoke(event);
                    getLogger().info("Chunky generation complete for " + world
                            + " (" + completed[0] + "/" + started[0] + ")");
                } catch (Exception e) {
                    getLogger().warning("Failed to get Chunky world name: " + e.getMessage());
                }
                if (completed[0] >= started[0]) {
                    getLogger().info("All Chunky preload tasks complete!");
                    sendPluginMessage("chunky_done");
                }
            });

            java.lang.reflect.Method startTaskMethod = chunkyApiClass.getMethod(
                    "startTask", String.class, String.class, int.class, int.class, int.class, int.class, String.class);

            World overworld = getServer().getWorlds().getFirst();
            startTaskMethod.invoke(chunky, overworld.getName(), "square", 0, 0, owRadius, owRadius, "concentric");
            started[0]++;

            World nether = getServer().getWorld(overworld.getName() + "_nether");
            if (nether != null) {
                startTaskMethod.invoke(chunky, nether.getName(), "square", 0, 0, netherRadius, netherRadius, "concentric");
                started[0]++;
            }

            World end = getServer().getWorld(overworld.getName() + "_the_end");
            if (end != null) {
                startTaskMethod.invoke(chunky, end.getName(), "square", 0, 0, endRadius, endRadius, "concentric");
                started[0]++;
            }

            getLogger().info("Chunky preload started for " + started[0] + " worlds"
                    + " (ow=" + owRadius + ", nether=" + netherRadius + ", end=" + endRadius + ")");
        } catch (ClassNotFoundException e) {
            getLogger().warning("Chunky not installed — skipping preload");
            sendPluginMessage("chunky_done");
        } catch (Exception e) {
            getLogger().severe("Failed to start Chunky preload: " + e.getMessage());
            sendPluginMessage("chunky_done");
        }
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

    // =========================================================================
    //    Helpers
    // =========================================================================

    private void cancelAllTasks() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (elytraTask != null) { elytraTask.cancel(); elytraTask = null; }
        if (fireworksTask != null) { fireworksTask.cancel(); fireworksTask = null; }
    }

    private void broadcast(String message) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        getServer().broadcast(component);
    }
}
