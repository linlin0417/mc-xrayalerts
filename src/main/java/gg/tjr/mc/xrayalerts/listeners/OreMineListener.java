package gg.tjr.mc.xrayalerts.listeners;

import gg.tjr.mc.xrayalerts.XRayAlertsPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class OreMineListener implements Listener {

    private final Plugin plugin = XRayAlertsPlugin.getInstance();
    private final FileConfiguration config = plugin.getConfig();

    private final Map<Block, Long> processedBlocks = new HashMap<>();
    private final long processedBlocksCleanupInterval = 1000*60*5;

    public OreMineListener() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupProcessedBlocks();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockMaterial = block.getType();

        if (player.hasPermission("xrayalerts.ignore")) {
            return;
        }

        List<String> monitoredBlocks = config.getStringList("monitored-blocks");

        if (monitoredBlocks.contains(blockMaterial.name())) {
            String mode = config.getString("mode", "block");
            int count;

            if (mode.equalsIgnoreCase("vein")) {
                if (processedBlocks.containsKey(block)) {
                    return;
                }

                Set<Block> vein = findVein(block, blockMaterial);
                count = vein.size();

                long currentTime = System.currentTimeMillis();
                vein.forEach(b -> processedBlocks.put(b, currentTime));
            } else {
                count = event.getBlock().getDrops(player.getInventory().getItemInMainHand()).size();
            }

            String messageFormat = config.getString("alert-message", "&c&lX-Ray&r &7%player% found &6x%count% %item%.");
            String message = messageFormat
                    .replace("%count%", String.valueOf(count))
                    .replace("%item%", blockMaterial.name().toLowerCase().replace("_", " "))
                    .replace("%player%", player.getName())
                    .replace("&", "§");

            plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("xrayalerts.receive"))
                    .filter(p -> config.getBoolean("alerts." + p.getUniqueId(), true))
                    .forEach(p -> p.sendMessage(message));
        }
    }

    private Set<Block> findVein(Block startBlock, Material material) {
        Set<Block> vein = new HashSet<>();
        Set<Block> toCheck = new HashSet<>();

        toCheck.add(startBlock);

        while (!toCheck.isEmpty()) {
            Block block = toCheck.iterator().next();
            toCheck.remove(block);

            if (block.getType() == material && vein.add(block)) {
                for (Block relative : getAdjacentBlocks(block)) {
                    if (!vein.contains(relative) && !processedBlocks.containsKey(relative)) {
                        toCheck.add(relative);
                    }
                }
            }
        }

        return vein;
    }

    private Set<Block> getAdjacentBlocks(Block block) {
        Set<Block> adjacent = new HashSet<>();
        adjacent.add(block.getRelative(1, 0, 0));
        adjacent.add(block.getRelative(-1, 0, 0));
        adjacent.add(block.getRelative(0, 1, 0));
        adjacent.add(block.getRelative(0, -1, 0));
        adjacent.add(block.getRelative(0, 0, 1));
        adjacent.add(block.getRelative(0, 0, -1));
        return adjacent;
    }

    private void cleanupProcessedBlocks() {
        long currentTime = System.currentTimeMillis();
        processedBlocks.entrySet().removeIf(entry -> currentTime - entry.getValue() > processedBlocksCleanupInterval);
    }
}
