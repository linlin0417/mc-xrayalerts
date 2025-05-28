package LinlinSaya.mc.rayalerts;

import LinlinSaya.mc.rayalerts.commands.XRayAlertsCommand;
import LinlinSaya.mc.rayalerts.listeners.OreMineListener;
import org.bukkit.plugin.java.JavaPlugin;

public class XRayAlertsPlugin extends JavaPlugin {

    private static XRayAlertsPlugin instance;    @Override
    public void onEnable() {
        instance = this;
        
        // 顯示插件資訊橫幅
        showBanner();

        // 儲存預設配置並初始化配置
        initConfig();
        
        getCommand("xrayalerts").setExecutor(new XRayAlertsCommand());

        getServer().getPluginManager().registerEvents(new OreMineListener(), this);

        getLogger().info("XRayAlerts 插件已成功啟用");
        
        // 顯示並測試 Discord Webhook
        checkAndTestDiscordWebhook();
    }
      /**
     * 顯示插件資訊橫幅
     */
    private void showBanner() {
        // 從 plugin.yml 獲取資訊
        String version = "2.0.0"; 
        String author = "linlin";  
        String description = "檢測並警報可能使用 X-Ray 作弊的玩家"; 
                      
        getServer().getConsoleSender().sendMessage("§a╔═══════════════════════════════════════════════");
        getServer().getConsoleSender().sendMessage("§a║ §b✦ §fXRayAlerts §b" + version + " §f已啟動");
        getServer().getConsoleSender().sendMessage("§a║ §b✦ §f作者: §b" + author);
        getServer().getConsoleSender().sendMessage("§a║ §b✦ §f描述: §f" + description);
        getServer().getConsoleSender().sendMessage("§a╚═══════════════════════════════════════════════");
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
                boolean testResult = LinlinSaya.mc.rayalerts.utils.WebhookSender.testWebhook();
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
    }    @Override
    public void onDisable() {
        instance = null;

        // 關閉時顯示訊息
        getServer().getConsoleSender().sendMessage("§c╔═══════════════════════════════════════════════");
        getServer().getConsoleSender().sendMessage("§c║ §b✦ §fXRayAlerts §b插件已經關閉");
        getServer().getConsoleSender().sendMessage("§c║ §b✦ §f感謝您的使用！");
        getServer().getConsoleSender().sendMessage("§c╚═══════════════════════════════════════════════");
        
        getLogger().info("XRayAlerts 插件已成功關閉");
    }

    public static XRayAlertsPlugin getInstance() {
        return instance;
    }
}
