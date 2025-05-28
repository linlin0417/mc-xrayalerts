package gg.tjr.mc.xrayalerts;

import gg.tjr.mc.xrayalerts.commands.XRayAlertsCommand;
import gg.tjr.mc.xrayalerts.listeners.OreMineListener;
import org.bukkit.plugin.java.JavaPlugin;

public class XRayAlertsPlugin extends JavaPlugin {

    private static XRayAlertsPlugin instance;    @Override
    public void onEnable() {
        instance = this;

        // 儲存預設配置並初始化配置
        initConfig();
        
        getCommand("xrayalerts").setExecutor(new XRayAlertsCommand());

        getServer().getPluginManager().registerEvents(new OreMineListener(), this);

        getLogger().info("Plugin has been enabled.");
        
        // 顯示並測試 Discord Webhook
        checkAndTestDiscordWebhook();
    }
    
    /**
     * 初始化和檢查配置文件
     */
    private void initConfig() {
        // 儲存預設配置
        saveDefaultConfig();
        
        // 重新加載配置以確保獲取最新的配置值
        reloadConfig();
        
        // 確保配置包含所有必要的選項
        checkConfigDefaults();
        
        // 打印當前配置狀態
        logCurrentConfig();
    }
    
    /**
     * 檢查配置預設值
     */    private void checkConfigDefaults() {
        boolean configChanged = false;
        
        if (!getConfig().contains("discord")) {
            getConfig().createSection("discord");
            configChanged = true;
        }
        
        if (!getConfig().contains("discord.enabled")) {
            getConfig().set("discord.enabled", true);
            configChanged = true;
        }
        
        if (!getConfig().contains("discord.webhook-url")) {
            getConfig().set("discord.webhook-url", "");
            configChanged = true;
        }
        
        if (!getConfig().contains("server")) {
            getConfig().createSection("server");
            configChanged = true;
        }
        
        if (!getConfig().contains("server.name")) {
            getConfig().set("server.name", "分流資訊無法獲取");
            configChanged = true;
        }
        
        if (configChanged) {
            saveConfig();
            getLogger().info("已更新配置文件預設值");
        }
    }
      /**
     * 打印當前配置狀態
     */
    private void logCurrentConfig() {
        // 打印重要配置項
        getLogger().info("==== 配置信息 ====");
        getLogger().info("Discord Webhook 整合: " + 
                         (getConfig().getBoolean("discord.enabled") ? "已啟用" : "未啟用"));
        
        String webhookUrl = getConfig().getString("discord.webhook-url", "");
        if (getConfig().getBoolean("discord.enabled") && !webhookUrl.isEmpty()) {
            getLogger().info("Discord Webhook URL: " + webhookUrl);
        } else if (getConfig().getBoolean("discord.enabled") && webhookUrl.isEmpty()) {
            getLogger().warning("警告: Discord Webhook 已啟用但未設置 URL");
        }
        
        // 顯示分流名稱
        String serverName = getConfig().getString("server.name", "分流資訊無法獲取");
        getLogger().info("伺服器分流名稱: " + serverName);
    }
    
    /**
     * 檢查並測試 Discord Webhook
     */
    private void checkAndTestDiscordWebhook() {
        if (getConfig().getBoolean("discord.enabled", false)) {
            String webhookUrl = getConfig().getString("discord.webhook-url", "");
            getLogger().info("Discord Webhook 整合已啟用");
            
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                getLogger().warning("警告: Discord Webhook URL 為空，請在配置中設置有效的 URL");
                return;
            }
            
            // 非同步測試 Webhook 連接
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                getLogger().info("正在測試 Discord Webhook 連接...");
                boolean testResult = gg.tjr.mc.xrayalerts.utils.WebhookSender.testWebhook();
                if (testResult) {
                    getLogger().info("Discord Webhook 連接測試成功");
                } else {
                    getLogger().warning("Discord Webhook 連接測試失敗，請檢查配置");
                    getLogger().warning("URL: " + webhookUrl);
                }
            });
        } else {
            getLogger().info("Discord Webhook 整合未啟用");
        }
    }

    @Override
    public void onDisable() {
        instance = null;

        getLogger().info("Plugin has been disabled.");
    }

    public static XRayAlertsPlugin getInstance() {
        return instance;
    }
}
