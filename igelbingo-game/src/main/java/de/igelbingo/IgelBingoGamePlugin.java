package de.igelbingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.*;
import org.bukkit.GameRules;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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

public final class IgelBingoGamePlugin extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "igelbingo:main";

    private GamePhase phase = GamePhase.IDLE;
    private final GameConfig gameConfig = new GameConfig();
    private boolean firstStart = false;

    private BukkitTask countdownTask;
    private int countdownRemaining;

    private BukkitTask elytraTask;
    private BukkitTask fireworksTask;
    private final Map<UUID, Long> lastFireworkReplenish = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);

        saveDefaultConfig();
        loadGameConfig();

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

    // =========================================================================
    //    Config
    // =========================================================================

    private void loadGameConfig() {
        var config = getConfig();
        gameConfig.countdownSeconds = config.getInt("countdown-seconds", 30);
        gameConfig.worldborderOverworld = config.getInt("worldborder-overworld", 10000);
        gameConfig.worldborderNether = config.getInt("worldborder-nether", 4000);
        gameConfig.worldborderEnd = config.getInt("worldborder-end", 8000);
        gameConfig.elytraReplenishInterval = config.getInt("elytra-replenish-interval", 30);
        gameConfig.elytraReplenishAmount = config.getInt("elytra-replenish-amount", 3);
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
    //    Pre-Game Sequence (Countdown)
    // =========================================================================

    public void startPreGameSequence() {
        if (phase != GamePhase.IDLE) return;

        phase = GamePhase.COUNTDOWN;
        countdownRemaining = gameConfig.countdownSeconds;

        World world = getServer().getWorlds().getFirst();
        world.setDifficulty(Difficulty.EASY);
        world.setGameRule(GameRules.RAIDS, true);

        // Set time to day
        world.setTime(1000);
        world.setStorm(false);
        world.setThundering(false);

        // Silence BAC advancement toasts, sounds, chat and rewards (overwhelming in Bingo)
        suppressBacNotifications();

        // Adventure mode for all players during countdown
        for (Player player : getServer().getOnlinePlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        broadcast("&6[Igel-Bingo] &eSpiel startet in &c" + countdownRemaining + " &eSekunden!");

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                countdownRemaining--;

                if (countdownRemaining <= 0) {
                    finishCountdown();
                    cancel();
                    return;
                }

                // Announce at milestones
                if (countdownRemaining <= 5 || countdownRemaining == 10 || countdownRemaining == 20) {
                    broadcast("&6[Igel-Bingo] &eNoch &c" + countdownRemaining + " &eSekunden!");
                    playCountdownSound(countdownRemaining);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        // Schedule abort option (allows /igelbingo abort during countdown)
        getLogger().info("Countdown started: " + gameConfig.countdownSeconds + "s");
    }

    private void finishCountdown() {
        phase = GamePhase.RUNNING;
        countdownTask = null;

        World world = getServer().getWorlds().getFirst();

        // Give starter kit
        for (Player player : getServer().getOnlinePlayers()) {
            giveStarterKit(player);
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Start elytra replenishment tasks
        startElytraTasks();

        broadcast("&6[Igel-Bingo] &a&lLOS GEHT'S!");
        world.playSound(new Location(world, 0, 100, 0), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);

        // Notify Velocity
        sendPluginMessage("game_started");

        getLogger().info("Game started! Players released.");
    }

    public void abortCountdown() {
        if (phase != GamePhase.COUNTDOWN) return;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        phase = GamePhase.IDLE;

        broadcast("&6[Igel-Bingo] &cCountdown abgebrochen.");
        getLogger().info("Countdown aborted.");
    }

    // =========================================================================
    //    End Game Sequence
    // =========================================================================

    public void endGameSequence() {
        phase = GamePhase.ENDING;

        cancelAllTasks();

        World world = getServer().getWorlds().getFirst();
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(0);
        world.setStorm(false);
        world.setThundering(false);

        // Clear effects
        for (Player player : getServer().getOnlinePlayers()) {
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.setFireTicks(0);
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Notify Velocity that game ended
        sendPluginMessage("game_ended");

        phase = GamePhase.IDLE;

        broadcast("&6[Igel-Bingo] &eSpiel beendet!");

        getLogger().info("Game ended and cleaned up.");
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

        // Fireworks
        ItemStack fireworks = new ItemStack(Material.FIREWORK_ROCKET, 64);
        FireworkMeta fwMeta = (FireworkMeta) fireworks.getItemMeta();
        fwMeta.setPower(3);
        fwMeta.displayName(Component.text("Igel-Bingo Rakete", NamedTextColor.GOLD));
        fireworks.setItemMeta(fwMeta);
        player.getInventory().addItem(fireworks);

        // Shovel
        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        var shovelMeta = shovel.getItemMeta();
        shovelMeta.setUnbreakable(true);
        shovelMeta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        shovel.setItemMeta(shovelMeta);
        player.getInventory().addItem(shovel);
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

        // Firework replenish
        fireworksTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;

                    ItemStack fireworks = new ItemStack(Material.FIREWORK_ROCKET, gameConfig.elytraReplenishAmount);
                    FireworkMeta fwMeta = (FireworkMeta) fireworks.getItemMeta();
                    fwMeta.setPower(3);
                    fireworks.setItemMeta(fwMeta);

                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(fireworks);
                    if (!overflow.isEmpty()) {
                        for (ItemStack item : overflow.values()) {
                            player.getWorld().dropItem(player.getLocation(), item);
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

        dispatchSilently(console, "function blazeandcave:config/msg_task_off");
        dispatchSilently(console, "function blazeandcave:config/msg_goal_off");
        dispatchSilently(console, "function blazeandcave:config/msg_challenge_off");
        dispatchSilently(console, "function blazeandcave:config/msg_super_challenge_off");
        dispatchSilently(console, "function blazeandcave:config/msg_milestone_off");
        dispatchSilently(console, "function blazeandcave:config/msg_set_off");
        dispatchSilently(console, "function blazeandcave:config/msg_set_server1");
        dispatchSilently(console, "function blazeandcave:config/msg_set_vanilla_msg");

        dispatchSilently(console, "function blazeandcave:config/intro_msg_off");

        dispatchSilently(console, "function blazeandcave:config/trophies_off");
        dispatchSilently(console, "function blazeandcave:config/item_rewards_off");
        dispatchSilently(console, "function blazeandcave:config/exp_rewards_off");

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
