package de.igelbingo;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Replaces vanilla piglin bartering drops with the 20w20a–20w27a table,
 * matching the full releases of Minecraft 1.16 and 1.16.1.
 *
 * Only active when IGELBINGO_GAME_MODIFIED_LOOTTABLES (env MODIFIED_LOOTTABLES)
 * resolves to true; otherwise this listener is never registered and vanilla
 * bartering applies.
 */
public final class PiglinBarterListener implements Listener {

    private record Entry(int weight, Supplier<ItemStack> factory) {}

    // Single pool, one roll — weights sum to 423 (matching the 1.16.1 loot table).
    private static final Entry[] ENTRIES = {
        new Entry(5, PiglinBarterListener::soulSpeedBook),
        new Entry(8, PiglinBarterListener::soulSpeedBoots),
        new Entry(10, () -> firePotion(Material.SPLASH_POTION)),
        new Entry(10, () -> firePotion(Material.POTION)),
        new Entry(10, () -> stack(Material.IRON_NUGGET, 9, 36)),
        new Entry(20, () -> stack(Material.QUARTZ, 8, 16)),
        new Entry(20, () -> stack(Material.GLOWSTONE_DUST, 5, 12)),
        new Entry(20, () -> stack(Material.MAGMA_CREAM, 2, 6)),
        new Entry(20, () -> stack(Material.ENDER_PEARL, 4, 8)),
        new Entry(20, () -> stack(Material.STRING, 8, 24)),
        new Entry(40, () -> stack(Material.FIRE_CHARGE, 1, 5)),
        new Entry(40, () -> stack(Material.GRAVEL, 8, 16)),
        new Entry(40, () -> stack(Material.LEATHER, 4, 10)),
        new Entry(40, () -> stack(Material.NETHER_BRICK, 4, 16)),
        new Entry(40, () -> new ItemStack(Material.OBSIDIAN, 1)),
        new Entry(40, () -> stack(Material.CRYING_OBSIDIAN, 1, 3)),
        new Entry(40, () -> stack(Material.SOUL_SAND, 4, 16)),
    };

    private static final int TOTAL_WEIGHT;
    static {
        int sum = 0;
        for (Entry entry : ENTRIES) sum += entry.weight();
        TOTAL_WEIGHT = sum;
    }

    @EventHandler
    public void onBarter(PiglinBarterEvent event) {
        List<ItemStack> outcome = event.getOutcome();
        outcome.clear();
        outcome.add(roll());
    }

    private static ItemStack roll() {
        int target = ThreadLocalRandom.current().nextInt(TOTAL_WEIGHT);
        int cumulative = 0;
        for (Entry entry : ENTRIES) {
            cumulative += entry.weight();
            if (target < cumulative) return entry.factory().get();
        }
        return ENTRIES[ENTRIES.length - 1].factory().get();
    }

    private static ItemStack stack(Material material, int min, int max) {
        int count = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        return new ItemStack(material, count);
    }

    private static ItemStack firePotion(Material material) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.FIRE_RESISTANCE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack soulSpeedBook() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(Enchantment.SOUL_SPEED, randomSoulSpeedLevel(), true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack soulSpeedBoots() {
        ItemStack item = new ItemStack(Material.IRON_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SOUL_SPEED, randomSoulSpeedLevel(), true);
        item.setItemMeta(meta);
        return item;
    }

    // Soul Speed has levels 1–3, chosen uniformly (enchant_randomly).
    private static int randomSoulSpeedLevel() {
        return ThreadLocalRandom.current().nextInt(1, 4);
    }
}
