package de.igelbingo;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

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
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.FREEZE_DAMAGE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
        world.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, false);
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
        world.setGameRule(GameRule.DISABLE_RAIDS, false);

        // Set time to day
        world.setTime(1000);
        world.setStorm(false);
        world.setThundering(false);

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
        meta.setDisplayName("§6Igel-Bingo Elytra");
        meta.addEnchant(Enchantment.PROTECTION, 0, true);
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        // Fireworks
        ItemStack fireworks = new ItemStack(Material.FIREWORK_ROCKET, 64);
        FireworkMeta fwMeta = (FireworkMeta) fireworks.getItemMeta();
        fwMeta.setPower(3);
        fwMeta.setDisplayName("§6Igel-Bingo Rakete");
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
        meta.setDisplayName("§6Igel-Bingo Elytra");
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
    //    Plugin Messaging (Velocity Bridge)
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
        if (!getServer().getOnlinePlayers().isEmpty()) {
            Player first = getServer().getOnlinePlayers().iterator().next();
            first.sendPluginMessage(this, CHANNEL, message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        getServer().broadcastMessage(colored);
    }
}
