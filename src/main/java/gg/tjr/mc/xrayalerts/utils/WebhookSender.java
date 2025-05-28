package gg.tjr.mc.xrayalerts.utils;

import gg.tjr.mc.xrayalerts.XRayAlertsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Discord Webhook 發送工具
 */
public class WebhookSender {

    private static final XRayAlertsPlugin plugin = XRayAlertsPlugin.getInstance();

    /**
     * 發送消息到 Discord Webhook
     * @param message 要發送的消息內容
     */
    public static void sendToDiscord(String message) {
        FileConfiguration config = plugin.getConfig();
        String webhookUrl = config.getString("discord.webhook-url");
        boolean enabled = config.getBoolean("discord.enabled", false);
        
        // 如果未啟用或URL為空，則不發送
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().fine("Discord webhook 未啟用或URL為空");
            return;
        }
        
        // 進行日誌記錄，便於測試和排除問題
        plugin.getLogger().info("準備發送 Discord webhook 消息");

        // 處理消息，轉義特殊字符
        String safeMessage = escapeJsonString(message);
        
        // Discord Webhook JSON格式
        String jsonMessage = String.format("{\"content\":\"%s\"}", safeMessage);
        
        // 非同步發送請求，避免阻塞主線程
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("正在發送 Discord webhook 消息: " + safeMessage);
                plugin.getLogger().info("Webhook URL: " + webhookUrl);
                
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "XRayAlerts-Plugin");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                
                // 寫入 JSON 數據
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonMessage.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                // 獲取回應代碼
                int responseCode = connection.getResponseCode();
                
                // 讀取回應內容（對偵錯有幫助）
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                }
                
                if (responseCode != 204) { // Discord 成功回應代碼為 204 No Content
                    plugin.getLogger().warning("Discord webhook 發送失敗，回應代碼: " + responseCode + ", 回應內容: " + response);
                } else {
                    plugin.getLogger().info("Discord webhook 發送成功");
                }
                
                // 關閉連接
                connection.disconnect();
                
            } catch (IOException e) {
                plugin.getLogger().warning("Discord webhook 發送失敗: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 移除 Minecraft 色碼
     * @param text 包含色碼的文字
     * @return 移除色碼後的文字
     */
    public static String stripColor(String text) {
        if (text == null) {
            return null;
        }
        // 移除 § 色碼和 & 色碼
        return text.replaceAll("§[0-9a-fklmnor]", "").replaceAll("&[0-9a-fklmnor]", "");
    }
    
    /**
     * 轉義 JSON 字串中的特殊字元
     * @param input 原始字串
     * @return 轉義後的字串
     */
    private static String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (c < 32 || c > 127) {
                        escaped.append(String.format("\\u%04x", (int)c));
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
    
    /**
     * 測試 Discord Webhook 連線
     * @return 連線測試結果 (true: 成功, false: 失敗)
     */
    public static boolean testWebhook() {
        FileConfiguration config = plugin.getConfig();
        String webhookUrl = config.getString("discord.webhook-url");
        boolean enabled = config.getBoolean("discord.enabled", false);
        
        // 詳細記錄配置狀態
        plugin.getLogger().info("Discord webhook 測試 - 配置狀態:");
        plugin.getLogger().info("- 已啟用: " + (enabled ? "是" : "否"));
        plugin.getLogger().info("- URL: " + (webhookUrl != null ? webhookUrl : "未設置"));
        
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().warning("Discord webhook 未啟用或URL為空，無法進行測試");
            return false;
        }
        
        try {
            // 檢查URL格式
            if (!webhookUrl.startsWith("https://discord.com/api/webhooks/")) {
                plugin.getLogger().warning("Discord webhook URL 格式可能不正確，應以 https://discord.com/api/webhooks/ 開頭");
            }
            
            // 發送測試消息
            plugin.getLogger().info("正在嘗試連接到 Discord webhook...");
            URL url = new URL(webhookUrl);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "XRayAlerts-Plugin");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(10000); // 10秒連線超時
            connection.setReadTimeout(10000);    // 10秒讀取超時
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String testMessage = "{\"content\":\"XRayAlerts 插件連接測試 - " + timestamp + "\"}";
            
            // 寫入請求內容
            plugin.getLogger().info("正在發送測試消息...");
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = testMessage.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 獲取回應
            int responseCode = connection.getResponseCode();
            plugin.getLogger().info("收到 Discord 回應代碼: " + responseCode);
            
            // 讀取回應內容
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            
            // 關閉連接
            connection.disconnect();
            
            // 處理回應結果
            if (responseCode == 204) {
                plugin.getLogger().info("Discord webhook 測試成功");
                return true;
            } else {
                plugin.getLogger().warning("Discord webhook 測試失敗，回應代碼: " + responseCode);
                plugin.getLogger().warning("回應內容: " + response);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook 測試失敗: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
