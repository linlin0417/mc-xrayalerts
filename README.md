# XRayAlerts

**XRayAlerts** 是一款適用於 Paper/Spigot 伺服器的插件，可在玩家發現特定礦物或物品時通知管理人員（或任何具有相應權限的玩家）。它支援自訂通知、可配置的監控模式和基於權限的訪問控制。此插件與執行 Minecraft 1.21+ 的 Paper 和 Spigot 伺服器相容。

---

## 功能

- **可自訂通知**：自定義當玩家找到受監控的物品或礦物時發送給管理員的訊息。訊息支援顏色和格式代碼。
- **礦脈偵測模式**：可選擇兩種模式：
    - **方塊模式**：為每個被挖掘的方塊發送通知，顯示掉落物品的數量。
    - **礦脈模式**：當礦脈中的第一個方塊被挖掘時發送一次通知，顯示整個礦脈。
- **權限控制**：管理切換通知、接收通知和忽略通知的權限。

## 指令

### `/xrayalerts toggle`
切換執行指令玩家的 x-ray 通知開關。\
**權限**：`xrayalerts.toggle`\
**用法**：`/xrayalerts toggle`

## 權限

- **`xrayalerts.ignore`**：防止該玩家觸發 x-ray 通知。
- **`xrayalerts.receive`**：允許玩家接收 x-ray 通知。
- **`xrayalerts.toggle`**：允許玩家切換 x-ray 通知的開關。

## 配置

### `config.yml`
```yaml
# 挖礦模式設定: "block" (每個方塊獨立檢測) 或 "vein" (檢測整個礦脈)
mode: "block" # 選項: "block", "vein"

# Discord Webhook 設定
discord:
  enabled: false # 是否啟用 Discord Webhook 通知
  webhook-url: "" # 在此填入你的 Discord Webhook URL

# 伺服器資訊
server:
  name: "分流資訊無法獲取" # 伺服器分流名稱，會顯示在警告消息中

monitored-blocks:
  - ANCIENT_DEBRIS
  - DEEPSLATE_DIAMOND_ORE
  - DIAMOND_ORE
```

---
## 授權條款
版權所有 © 2024 [Tyler Richards](https://github.com/tjrgg)。[MIT 授權](LICENSE).
