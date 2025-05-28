package LinlinSaya.mc.rayalerts.commands;

import LinlinSaya.mc.rayalerts.XRayAlertsPlugin;
import LinlinSaya.mc.rayalerts.utils.WebhookSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class XRayAlertsCommand implements CommandExecutor {    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0) {
            // 檢查權限
            if (!sender.hasPermission("xrayalerts.admin")) {
                sender.sendMessage("§c您沒有權限執行此命令。");
                return true;
            }
            
            // 處理不同的子命令
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("test")) {
                // 測試 Discord webhook
                sender.sendMessage("§e正在測試 Discord webhook 連接...");
                
                // 非同步執行測試
                XRayAlertsPlugin.getInstance().getServer().getScheduler().runTaskAsynchronously(
                    XRayAlertsPlugin.getInstance(),
                    () -> {
                        boolean testResult = WebhookSender.testWebhook();
                          if (testResult) {
                            // 發送測試消息（使用 Embed 格式）
                            String testMessage = String.format(
                                "**測試訊息**\n" +
                                "這是一條測試訊息，用於確認 Discord Webhook 配置正確。\n\n" +
                                "**發送者:** %s\n" +
                                "**時間:** %s\n" +
                                "**狀態:** 連接成功",
                                sender.getName(),
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                            );
                            WebhookSender.sendEmbedToDiscord(testMessage, "XRayAlerts 系統測試", 0x00FF00); // 綠色表示測試成功
                            XRayAlertsPlugin.getInstance().getServer().getScheduler().runTask(
                                XRayAlertsPlugin.getInstance(),
                                () -> sender.sendMessage("§a測試成功！已發送測試消息到 Discord。")
                            );
                        } else {
                            XRayAlertsPlugin.getInstance().getServer().getScheduler().runTask(
                                XRayAlertsPlugin.getInstance(),
                                () -> sender.sendMessage("§c測試失敗。請檢查控制台獲取更多信息。")
                            );
                        }
                    }
                );
                return true;
            } else if (subCommand.equals("reload")) {
                // 重新載入配置
                XRayAlertsPlugin plugin = XRayAlertsPlugin.getInstance();
                plugin.reloadConfig();
                  // 檢查配置狀態
                boolean webhookEnabled = plugin.getConfig().getBoolean("discord.enabled", false);
                String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
                String serverName = plugin.getConfig().getString("server.name", "分流資訊無法獲取");
                
                sender.sendMessage("§a配置已重新載入!");
                sender.sendMessage("§e Discord Webhook 狀態: " + (webhookEnabled ? "§a已啟用" : "§c未啟用"));
                if (webhookEnabled) {
                    sender.sendMessage("§e Webhook URL: §7" + webhookUrl);
                    
                    // 測試新配置
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        boolean testResult = WebhookSender.testWebhook();
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (testResult) {
                                sender.sendMessage("§a連接測試成功!");
                            } else {
                                sender.sendMessage("§c連接測試失敗，請檢查 URL 是否正確。");
                            }
                        });
                    });
                }
                
                // 顯示分流名稱設定
                sender.sendMessage("§e伺服器分流名稱: §7" + serverName);
                return true;
            }
        }
        
        // 原有的開關通知功能
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令。");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("xrayalerts.toggle")) {
            player.sendMessage("§c您沒有權限執行此命令。");
            return true;
        }

        boolean alertsEnabled = !XRayAlertsPlugin.getInstance().getConfig().getBoolean("alerts." + player.getUniqueId(), false);
        XRayAlertsPlugin.getInstance().getConfig().set("alerts." + player.getUniqueId(), alertsEnabled);
        XRayAlertsPlugin.getInstance().saveConfig();

        String message = alertsEnabled ? "§a已啟用 X-Ray 警報通知。" : "§c已禁用 X-Ray 警報通知。";
        player.sendMessage(message);

        return true;
    }
}
