package gg.tjr.mc.xrayalerts.listeners;

import gg.tjr.mc.xrayalerts.XRayAlertsPlugin;
import gg.tjr.mc.xrayalerts.utils.WebhookSender;
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

public class OreMineListener implements Listener {    private final Plugin plugin = XRayAlertsPlugin.getInstance();
    private final FileConfiguration config = plugin.getConfig();
    private final Map<Block, Long> processedBlocks = new HashMap<>();
    private final long processedBlocksCleanupInterval = 1000*60*5;
    
    // 記錄玩家每分鐘挖掘的礦物數量，按礦物組分類
    private final Map<UUID, Integer> playerDiamondCount = new HashMap<>();
    private final Map<UUID, Integer> playerAncientDebrisCount = new HashMap<>();
    // 記錄玩家挖掘時間，按礦物組分類
    private final Map<UUID, Long> playerDiamondLastTime = new HashMap<>();
    private final Map<UUID, Long> playerAncientDebrisLastTime = new HashMap<>();
      // 鑽石組礦物警告閾值
    private final int DIAMOND_WARNING_LEVEL_1 = 10; // 第一階段警告，每分鐘10個
    private final int DIAMOND_WARNING_LEVEL_2 = 20; // 第二階段警告，每分鐘20個
    private final int DIAMOND_WARNING_LEVEL_3 = 30; // 第三階段警告，每分鐘30個
    
    // 遠古遺骸組礦物警告閾值
    private final int ANCIENT_DEBRIS_WARNING_LEVEL_1 = 5; // 第一階段警告，每分鐘5個
    private final int ANCIENT_DEBRIS_WARNING_LEVEL_2 = 10; // 第二階段警告，每分鐘10個
    private final int ANCIENT_DEBRIS_WARNING_LEVEL_3 = 15; // 第三階段警告，每分鐘15個
    
    // 礦物組定義
    private final List<String> DIAMOND_GROUP = Arrays.asList("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE");
    private final List<String> ANCIENT_DEBRIS_GROUP = Collections.singletonList("ANCIENT_DEBRIS");

    public OreMineListener() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupProcessedBlocks();
            }        }.runTaskTimer(plugin, 20 * 60, 20 * 60);
        
        // 每分鐘清理玩家挖掘記錄
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupPlayerMineRecords();
            }        }.runTaskTimer(plugin, 20 * 60, 20 * 60);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockMaterial = block.getType();
        String materialName = blockMaterial.name();

        if (player.hasPermission("xrayalerts.ignore")) {
            return;
        }        // 只處理指定礦物類型
        boolean isDiamondGroup = DIAMOND_GROUP.contains(materialName);
        boolean isAncientDebrisGroup = ANCIENT_DEBRIS_GROUP.contains(materialName);
        
        if (!isDiamondGroup && !isAncientDebrisGroup) {
            return;
        }
        
        // 如果是鑽石系礦物，檢查Y座標，只有Y座標 <= 16 的才計算
        if (isDiamondGroup && block.getY() > 16) {
            return;
        }

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
          UUID playerUUID = player.getUniqueId();
        
        // 檢查是否達到了新的階段
        int previousCount = 0;        int currentCount = 0;
        int warningLevel1 = 0;
        int warningLevel2 = 0;
        int warningLevel3 = 0;
        String mineralsType = "";
        
        // 根據礦物組更新計數並取得警告級別
        if (isDiamondGroup) {
            previousCount = playerDiamondCount.getOrDefault(playerUUID, 0);
            updatePlayerDiamondCount(playerUUID, count);
            currentCount = playerDiamondCount.getOrDefault(playerUUID, 0);
            warningLevel1 = DIAMOND_WARNING_LEVEL_1;
            warningLevel2 = DIAMOND_WARNING_LEVEL_2;
            warningLevel3 = DIAMOND_WARNING_LEVEL_3;
            mineralsType = "鑽石";
        } else { // isAncientDebrisGroup
            previousCount = playerAncientDebrisCount.getOrDefault(playerUUID, 0);
            updatePlayerAncientDebrisCount(playerUUID, count);
            currentCount = playerAncientDebrisCount.getOrDefault(playerUUID, 0);
            warningLevel1 = ANCIENT_DEBRIS_WARNING_LEVEL_1;
            warningLevel2 = ANCIENT_DEBRIS_WARNING_LEVEL_2;
            warningLevel3 = ANCIENT_DEBRIS_WARNING_LEVEL_3;
            mineralsType = "遠古遺骸";
        }
        
        // 只在達到警告階段或超過警告階段時發送消息
        // 判斷是否是初次達到或突破階段
        boolean reachedLevel1 = previousCount <= warningLevel1 && currentCount > warningLevel1;
        boolean reachedLevel2 = previousCount <= warningLevel2 && currentCount > warningLevel2;
        boolean reachedLevel3 = previousCount <= warningLevel3 && currentCount > warningLevel3;
        
        // 如果沒有達到任何警告階段，則不發送通知
        if (!reachedLevel1 && !reachedLevel2 && !reachedLevel3) {
            return;
        }
          // 將總數調整為 5 的倍數 (向下取整)
        int displayCount = (int) (Math.floor(currentCount / 5.0) * 5);// 獲取座標信息
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String worldName = block.getWorld().getName();
        
        // 從配置文件讀取分流名稱
        String serverName = config.getString("server.name", "分流資訊無法獲取");        // 根據達到的階段創建消息
        final String finalMessage;
        final String title;
        final int color;
        
        if (reachedLevel3) {
            title = "【警告】疑似 X-Ray 玩家檢測";
            finalMessage = String.format(
                "**玩家:** %s\n" +
                "**礦物類型:** %s\n" +
                "**目前挖掘數量:** %d 個\n" +
                "**本次挖掘:** %d 個\n" +
                "**位置:** %s 的 x:%d y:%d z:%d\n" +
                "**分流:** %s\n\n" +
                "該玩家過去一分鐘內挖掘的礦物數量非常異常，幾乎確定使用 X-Ray!",
                player.getName(),
                mineralsType,
                displayCount,
                count,
                worldName,
                x, y, z,
                serverName
            );
            color = 0xFF0000; // 紅色，表示警告
        } else if (reachedLevel2) {
            title = "【極度可疑】疑似 X-Ray 玩家檢測";
            finalMessage = String.format(
                "**玩家:** %s\n" +
                "**礦物類型:** %s\n" +
                "**目前挖掘數量:** %d 個\n" +
                "**本次挖掘:** %d 個\n" +
                "**位置:** %s 的 x:%d y:%d z:%d\n" +
                "**分流:** %s\n\n" +
                "該玩家過去一分鐘內挖掘的礦物數量極度異常，極有可能使用 X-Ray!",
                player.getName(),
                mineralsType,
                displayCount,
                count,
                worldName,
                x, y, z,
                serverName
            );
            color = 0xFFA500; // 橙色，表示極度可疑
        } else if (reachedLevel1) {
            title = "【可疑】疑似 X-Ray 玩家檢測";
            finalMessage = String.format(
                "**玩家:** %s\n" +
                "**礦物類型:** %s\n" +
                "**目前挖掘數量:** %d 個\n" +
                "**本次挖掘:** %d 個\n" +
                "**位置:** %s 的 x:%d y:%d z:%d\n" +
                "**分流:** %s\n\n" +
                "該玩家過去一分鐘內挖掘的礦物數量異常，可能使用 X-Ray!",                player.getName(),
                mineralsType,
                displayCount,
                count,
                worldName,
                x, y, z,
                serverName
            );
            color = 0xFFA500; // 橙色，表示可疑
        } else {
            // 這個情況實際上不會發生，因為已經在上面返回了
            return;
        }
        
        // 不再發送到遊戲內（關閉 OP 通知）
        
        // 發送到 Discord Webhook (使用 Embed 格式)
        WebhookSender.sendEmbedToDiscord(finalMessage, title, color);
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
        adjacent.add(block.getRelative(0, 0, -1));        return adjacent;
    }
    
    private void cleanupProcessedBlocks() {
        long currentTime = System.currentTimeMillis();        processedBlocks.entrySet().removeIf(entry -> currentTime - entry.getValue() > processedBlocksCleanupInterval);
    }
    
    /**
     * 更新玩家挖掘鑽石礦物的計數
     * @param playerUUID 玩家UUID
     * @param count 挖掘數量
     */
    private void updatePlayerDiamondCount(UUID playerUUID, int count) {
        long currentTime = System.currentTimeMillis();
        long lastTime = playerDiamondLastTime.getOrDefault(playerUUID, 0L);
        
        // 如果超過一分鐘，重置計數
        if (currentTime - lastTime > 60000) {
            playerDiamondCount.put(playerUUID, count);
        } else {
            // 否則累加計數
            int currentCount = playerDiamondCount.getOrDefault(playerUUID, 0);
            playerDiamondCount.put(playerUUID, currentCount + count);
        }
        
        // 更新最後挖掘時間
        playerDiamondLastTime.put(playerUUID, currentTime);
    }
    
    /**
     * 更新玩家挖掘遠古遺骸礦物的計數
     * @param playerUUID 玩家UUID
     * @param count 挖掘數量
     */
    private void updatePlayerAncientDebrisCount(UUID playerUUID, int count) {
        long currentTime = System.currentTimeMillis();
        long lastTime = playerAncientDebrisLastTime.getOrDefault(playerUUID, 0L);
        
        // 如果超過一分鐘，重置計數
        if (currentTime - lastTime > 60000) {
            playerAncientDebrisCount.put(playerUUID, count);
        } else {
            // 否則累加計數
            int currentCount = playerAncientDebrisCount.getOrDefault(playerUUID, 0);
            playerAncientDebrisCount.put(playerUUID, currentCount + count);
        }
        
        // 更新最後挖掘時間
        playerAncientDebrisLastTime.put(playerUUID, currentTime);
    }
    
    /**
     * 清理超過一分鐘未活動的玩家記錄
     */
    private void cleanupPlayerMineRecords() {
        long currentTime = System.currentTimeMillis();
        // 清理超過2分鐘未活動的玩家記錄
        playerDiamondLastTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > 120000);
        playerAncientDebrisLastTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > 120000);
        
        // 清理不在時間記錄中的玩家挖掘計數
        playerDiamondCount.keySet().removeIf(uuid -> !playerDiamondLastTime.containsKey(uuid));
        playerAncientDebrisCount.keySet().removeIf(uuid -> !playerAncientDebrisLastTime.containsKey(uuid));
    }
}
