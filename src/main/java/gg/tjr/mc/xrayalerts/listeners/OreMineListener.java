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
    
    // 記錄玩家每分鐘挖掘的礦物數量
    private final Map<UUID, Integer> playerMineCount = new HashMap<>();
    // 記錄玩家挖掘時間
    private final Map<UUID, Long> playerLastMineTime = new HashMap<>();
    // 每分鐘可挖掘礦物的最大數量
    private final int MAX_MINE_PER_MINUTE = 30;

    public OreMineListener() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupProcessedBlocks();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60);
        
        // 每分鐘清理玩家挖掘記錄
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupPlayerMineRecords();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60);
    }    @EventHandler
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
            
            // 更新玩家挖掘記錄
            updatePlayerMineCount(player.getUniqueId(), count);
            
            // 檢查是否超過每分鐘挖掘限制
            int currentCount = playerMineCount.getOrDefault(player.getUniqueId(), 0);
              // 創建基本的信息
            String messageFormat = config.getString("alert-message", "&c&lX-Ray&r &7%player% found &6x%count% %item%.");
            final String basicMessage = messageFormat
                    .replace("%count%", String.valueOf(count))
                    .replace("%item%", blockMaterial.name().toLowerCase().replace("_", " "))
                    .replace("%player%", player.getName())
                    .replace("&", "§");
            
            // 如果玩家挖掘數量超過限制，添加警告信息
            final String finalMessage;
            if (currentCount > MAX_MINE_PER_MINUTE) {
                finalMessage = basicMessage + " &c&l[警告] 該玩家每分鐘挖掘了 " + currentCount + " 個礦物，可能使用X-Ray!".replace("&", "§");
            } else {
                finalMessage = basicMessage;
            }

            plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("xrayalerts.receive"))
                    .filter(p -> config.getBoolean("alerts." + p.getUniqueId(), true))
                    .forEach(p -> p.sendMessage(finalMessage));
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
    }    private void cleanupProcessedBlocks() {
        long currentTime = System.currentTimeMillis();
        processedBlocks.entrySet().removeIf(entry -> currentTime - entry.getValue() > processedBlocksCleanupInterval);
    }
    
    /**
     * 更新玩家挖掘礦物的計數
     * @param playerUUID 玩家UUID
     * @param count 挖掘數量
     */
    private void updatePlayerMineCount(UUID playerUUID, int count) {
        long currentTime = System.currentTimeMillis();
        long lastTime = playerLastMineTime.getOrDefault(playerUUID, 0L);
        
        // 如果超過一分鐘，重置計數
        if (currentTime - lastTime > 60000) {
            playerMineCount.put(playerUUID, count);
        } else {
            // 否則累加計數
            int currentCount = playerMineCount.getOrDefault(playerUUID, 0);
            playerMineCount.put(playerUUID, currentCount + count);
        }
        
        // 更新最後挖掘時間
        playerLastMineTime.put(playerUUID, currentTime);
    }
    
    /**
     * 清理超過一分鐘未活動的玩家記錄
     */
    private void cleanupPlayerMineRecords() {
        long currentTime = System.currentTimeMillis();
        // 清理超過2分鐘未活動的玩家記錄
        playerLastMineTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > 120000);
        
        // 清理不在時間記錄中的玩家挖掘計數
        playerMineCount.keySet().removeIf(uuid -> !playerLastMineTime.containsKey(uuid));
    }
}
