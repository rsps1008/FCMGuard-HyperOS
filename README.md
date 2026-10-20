<p align="center">
  <a href="README.md"><strong>繁體中文</strong></a> · <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <img src="docs/icon-fcmguard-plus.svg" width="180" alt="FCM Guard+ 圖示" />
</p>

# FCM Guard+ for HyperOS

**為中國版 HyperOS 設計的 FCM 背景防護工具。** 透過保留小米系統的 GMS 免限制項目，以及管理部分 Aurogon 規則，降低 Google Play 服務背景凍結造成推播延遲或中斷的機會。

[下載最新版本](https://github.com/rsps1008/FCMGuard-HyperOS/releases/latest) · [查看研究報告](docs/powerkeeper-os3-os4-fcm-investigation.md)

> 本工具不能保證每個 App 的通知都即時送達。推播還會受接收 App 自身的自啟動、電池策略、網路及 HyperOS 背景政策影響。

## 專案方向

FCM Guard+ 是獨立的 Android App，重點是以一般 App 可用的系統設定權限，維護 GMS 的重要背景設定，並提供可選的 HyperOS 4 夜間防護及 Aurogon 管理功能。核心 GMS 白名單修復不依賴 Root、常駐 ADB 或 Shizuku。

本專案採取保守的維護方式：保留設定中原有的其他套件、不在狀態正常時重複寫入、將各項防護分開開關，並在可行時讀回設定確認結果。小米私有設定和廣播屬於廠商介面，功能會隨 ROM 版本而異。

## 功能

- **GMS 免限制保護**：檢查 `Settings.System.MILLET_NO_RESTRICT_APP`，只在缺少 `com.google.android.gms` 時加入，保留原有清單項目。
- **自動保護**：監聽目標設定的變更；同時提供 30 分鐘的進程內檢查，服務停止或手機重開後能否恢復仍取決於 Android／HyperOS 排程與使用者設定。
- **Aurogon FCM 防護**：選擇性管理 `Settings.Global.aurogon_enable` 中的 FCM 廣播規則，掃描符合條件的 FCM App，並保留非本功能管理的既有規則。需另外取得 `WRITE_SECURE_SETTINGS`；自動授權設定會在 App 內清楚引導。
- **Aurogon 鬧鐘防護**：管理 `Settings.System.cloud_aurogon_alarm_allow_list`。同步時納入 GMS、符合條件的 FCM App，並保留 ROM 內建的三個套件與其他既有項目；可還原為只含內建項目的預設清單。
- **HyperOS 4 夜間防護（實驗性）**：管理 `com.miui.game.allowlist` 中與 GMS 有關的項目，降低特定中國版 HyperOS 4 夜間凍結路徑影響的機會。此設定可能全天有效，且會增加背景活動；停用時會依記錄的項目所有權清理。
- **FCM App 助手**：依 FCM/GCM manifest 接收元件尋找可能使用 FCM 的 App，並在 ROM 允許時唯讀顯示 Xiaomi 自啟動狀態。無法確認時會顯示未知，不會猜測。
- **可選自啟動控制**：使用者明確授權後，可透過 Shizuku 修改個別 App 的 Xiaomi 自啟動項目；一般掃描仍為唯讀。
- **輕量監控**：設定變更監聽採防抖處理；週期檢查在 App 進程內執行，不使用重複精確鬧鐘或 WakeLock。

## 開始使用

1. 從 [GitHub Releases](https://github.com/rsps1008/FCMGuard-HyperOS/releases/latest) 下載並安裝 APK。
2. 在系統設定中允許 FCM Guard+「修改系統設定」，再按「立即修復」確認 GMS 已在免限制清單中。
3. 開啟「自動保護」。若希望服務在背景較穩定地執行，請依 HyperOS 指引為 FCM Guard+ 開啟自啟動，並選擇合適的電池背景策略。
4. 只啟用你需要的 Aurogon 或夜間防護功能。Aurogon FCM 規則需先完成卡片中的權限設定；鬧鐘清單與夜間防護則需「修改系統設定」權限。
5. 若只有單一 App 收訊延遲，另外檢查該 App 在 HyperOS 的自啟動、電池限制及通知設定。

FCM Guard+ 的應用程式 ID 為 `com.rsps1008.fcmguard`。1.6.2 版的 versionCode 為 32。Android 會把不同應用程式 ID 視為不同 App，因此舊版權限和 App 內設定不會自動移轉。若要移除舊版，先關閉舊版夜間防護並完成其清理程序。

## 各項設定的作用

| 設定 | 管理內容 | 權限與範圍 |
|---|---|---|
| `MILLET_NO_RESTRICT_APP` | 將 GMS 保留在小米免限制清單 | 使用者授予「修改系統設定」；只維護 GMS 項目並保留其他項目 |
| `aurogon_enable` | Aurogon FCM 廣播規則 | 需要 `WRITE_SECURE_SETTINGS`；只更新本功能管理的規則 |
| `cloud_aurogon_alarm_allow_list` | Aurogon 凍結期間暫存鬧鐘的允許清單 | 使用者授予「修改系統設定」；此清單本身不阻止 App 凍結 |
| `com.miui.game.allowlist` | 特定 HyperOS 4 夜間 GMS 防凍結路徑 | 使用者授予「修改系統設定」；實驗性、ROM 限定 |

「鬧鐘防護」只影響 Aurogon 是否在 App 凍結時暫存其鬧鐘，不代表防止凍結，也不會繞過 WakePath。`aurogon_enable` 的廣播規則則影響相應 FCM 廣播能否依小米規則送往 App；這和 GMS 的 `MILLET_NO_RESTRICT_APP` 是不同設定。

## HyperOS 3 與 HyperOS 4 研究

本專案附有根據兩份使用者提供的 `miui-services.jar`、PowerKeeper 相關分析與裝置觀察整理的[研究報告](docs/powerkeeper-os3-os4-fcm-investigation.md)。目前的分析顯示，OS3 與 OS4 的背景限制路徑並不完全相同；部分 HyperOS 4 版本另有夜間凍結 GMS 的程式路徑。靜態程式碼只能說明 ROM 中存在相關機制，不能單獨證明特定手機某次漏訊息就是該路徑所致。

報告也整理使用者實測條件、`miui.os.Build` 區域判定線索、Aurogon 設定差異及證據限制。ROM 樣本位於 [`docs/rom-samples/miui-services/`](docs/rom-samples/miui-services/)。結果只適用於所分析的版本，其他中國版／國際版 ROM 可能不同。

### 上游研究致謝

感謝 [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) 公開其 HyperOS GMS／FCM／Greezer 實機調查與系統程式分析。該專案的研究記錄了 GMS 被 Greezer 凍結的路徑、`MILLET_NO_RESTRICT_APP` 的作用邊界，以及 HyperOS 4 夜間凍結的相關證據，為本專案進一步比較 ROM 行為提供重要參考。

FCM Guard+ 是另一個獨立實作，功能範圍、權限策略與程式碼均自行維護；本專案引用其公開研究作為技術參考，沒有把對方的程式碼當成本專案程式碼。請一併閱讀[上游調查文件](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)與[HyperOS 4 夜間調查](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)。

## 權限與隱私

核心白名單修復、鬧鐘允許清單與夜間防護使用使用者授予的「修改系統設定」能力。Aurogon FCM 規則需額外 `WRITE_SECURE_SETTINGS`。自啟動修改是可選功能，需明確授權 Shizuku；不會由 App 自行開啟 USB 或無線除錯。

FCM Guard+ 不使用 Root、無障礙、VPN、懸浮視窗、裝置管理員、帳號讀取或網路流量擷取，也不要求 `QUERY_ALL_PACKAGES`。App 掃描使用受限的套件可見性查詢。

## 支援與限制

- 需要 Android 6.0（API 23）或更新版本，以及已安裝且可正常使用的 Google Play 服務。
- 主要針對 Xiaomi／Redmi／POCO 中國版 HyperOS；不同機型、系統版本及 PowerKeeper 版本可能有不同結果。
- `MILLET_NO_RESTRICT_APP`、Aurogon 規則、鬧鐘清單及夜間策略均為小米私有介面，系統更新可能改變其格式或行為。
- 自動修復仰賴 FCM Guard+ 進程能執行。省電策略、強制停止、廠商背景限制或系統排程都可能中斷服務。
- 寫入成功或讀回一致只代表設定值符合預期，不等於已驗證系統解除凍結、FCM socket 持續連線，或接收 App 已顯示通知。
- 夜間防護仍屬實驗性；需以目標手機的實際 ROM、長時間熄屏與推播測試驗證耗電及效果。

## 建置

使用 Android Studio 或在 Windows 環境以 JDK 21 建置。專案保留 `targetSdk 22` 以相容小米私有設定的使用者授權路徑；建置時出現 `minSdk 23` 高於 `targetSdk 22` 的警告是目前設定的結果。

```powershell
python tools/check_layout_profiles.py
.\gradlew.bat :app:assembleDebug
```

## 授權

本專案採 MIT License，詳見 [LICENSE](LICENSE)。
