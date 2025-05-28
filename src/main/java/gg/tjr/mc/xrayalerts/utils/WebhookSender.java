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
     * 發送純文本消息到 Discord Webhook（保留向下兼容）
     * @param message 要發送的消息內容
     */
    public static void sendToDiscord(String message) {
        // 根據消息類型選擇適當的顏色和標題
        if (message.contains("【警告】")) {
            sendEmbedToDiscord(message, "【警告】疑似 X-Ray 玩家檢測", 0xFF0000); // 紅色，表示最高警告級別
        } else if (message.contains("【極度可疑】")) {
            sendEmbedToDiscord(message, "【極度可疑】疑似 X-Ray 玩家檢測", 0xFFA500); // 橙色
        } else if (message.contains("【可疑】")) {
            sendEmbedToDiscord(message, "【可疑】疑似 X-Ray 玩家檢測", 0xFFA500); // 橙色
        } else {
            // 默認情況下發送普通警告
            sendEmbedToDiscord(message, "X-Ray 警報系統通知", 0xFFFF00); // 黃色
        }
    }
    
    /**
     * 發送帶有 Embed 格式的消息到 Discord Webhook
     * @param message 消息內容
     * @param title 標題
     * @param color 顏色 (十進制格式，例如：0xFF0000 表示紅色)
     */
    public static void sendEmbedToDiscord(String message, String title, int color) {
        FileConfiguration config = plugin.getConfig();
        String webhookUrl = config.getString("discord.webhook-url");
        boolean enabled = config.getBoolean("discord.enabled", false);
        
        // 如果未啟用或URL為空，則不發送
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().fine("Discord webhook 未啟用或URL為空");
            return;
        }
        
        // 進行日誌記錄，便於測試和排除問題
        plugin.getLogger().info("準備發送 Discord webhook embed 消息");

        // 處理消息，轉義特殊字符
        String safeMessage = escapeJsonString(message);
        String safeTitle = escapeJsonString(title);
        
        // 創建當前時間戳
        long timestamp = System.currentTimeMillis();
        
        // Discord Webhook Embed JSON格式
        String jsonMessage = String.format(
            "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d,\"timestamp\":\"%s\"}]}",
            safeTitle,
            safeMessage,
            color,
            getISOTimestamp(timestamp)
        );
        
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
     * 獲取 ISO 8601 格式的時間戳，用於 Discord Embed
     * @param timestamp 時間戳（毫秒）
     * @return ISO 8601 格式的時間戳字串
     */
    private static String getISOTimestamp(long timestamp) {
        java.util.Date date = new java.util.Date(timestamp);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
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
            
            long now = System.currentTimeMillis();
            String formattedTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(now));
            
            // 使用 Embed 格式發送測試消息
            String testMessage = String.format(
                "{\"embeds\":[{\"title\":\"XRayAlerts 系統連線測試\",\"description\":\"這是一條自動發送的測試訊息，用於確認 Webhook 連線狀態正常。\\n\\n**測試時間:** %s\",\"color\":5814783,\"timestamp\":\"%s\"}]}",
                formattedTime,
                getISOTimestamp(now)
            );
            
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
