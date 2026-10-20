# Xiaomi HyperOS 3／4 的 GMS／FCM 背景限制研究報告

更新日期：2026-10-20

範圍：使用者提供的兩份 PowerKeeper APK、兩份 `miui-services.jar`、`dingwen07/hyperos-fcm-fix` 公開調查，以及本次對話提供的實機觀察。本文只對證據支持的範圍下結論，不把設定寫入成功等同於 FCM 必定送達。

## 摘要

目前最符合資料的解釋是：HyperOS 3 與 HyperOS 4 的差異不只是「自啟動」設定，而是 GMS 及接收 App 在背景／熄屏後還會受到的系統策略不同。使用者回報在已處理 `MILLET_NO_RESTRICT_APP` 的情況下，OS3 用「自啟動＋智慧」即可穩定收到 FCM；OS4 同條件不可靠，另回報「自啟動＋無限制」或「自啟動並加入 `aurogon_enable` 規則」可改善／收到通知。後兩項是替代做法的實機觀察，尚未以固定裝置與單變因輪替實驗確認等效性或穩定度。

上游研究已在 HyperOS 4 的特定中國版 ROM 上確認 Greezer 可凍結 GMS；其中夜間 Deep Doze 路徑甚至會讓已列入 `MILLET_NO_RESTRICT_APP` 的 GMS 遭 `nightDoze` 凍結。這支持 OS4 存在額外背景限制的解釋，但不能單憑此研究斷定使用者每一次漏訊息都是夜間凍結造成。

兩份本地 PowerKeeper APK 都包含 `aurogon_enable` 雲端 feature 與 `GlobalFeatureTable` provider 路徑。新提供的 `miui-services.jar` 進一步證明 system_server 直接讀取 `Settings.Global.aurogon_enable`；兩份 framework 也都讀取 `com.miui.game.allowlist`、`MILLET_NO_RESTRICT_APP`。OS4 JAR 有明確的夜間 no-restrict 凍結路徑（`NIGHT_FREEZE` → `nightDoze`），OS3 JAR 未見相同路徑。PowerKeeper 的內部 `GlobalFeatureTable` 與 Android Global key 仍是不同儲存介面；本次沒有找到它們彼此同步的直接呼叫。

## 使用者提供的實機對照

以下是本次研究採用的使用者實測前提，不是由本次 APK 靜態分析重新測出的結果：

| 實機設定／觀察 | HyperOS 3 | HyperOS 4 |
|---|---|---|
| 自啟動＋智慧策略，並已處理 `MILLET_NO_RESTRICT_APP` | 使用者回報可穩定收到 FCM | 使用者回報仍會受干涉，收訊不可靠 |
| 自啟動＋無限制策略 | 使用者表示不必切換成無限制也能收訊 | 使用者回報此組合可收到通知 |
| 自啟動＋`aurogon_enable` FCM 廣播規則 | 使用者表示 OS3 不需額外規則 | 使用者回報此組合也能改善／收到通知；穩定度未量化 |

本對話沒有記錄「自啟動／智慧／無限制」具體是替 GMS 還是目標接收 App 設定，也未提供各組測試的 ROM build、持續時間、訊息樣本及重複次數。以下分析因此把它們當成使用者觀察到的成功組合，不據此指定是哪一個 package 的 PowerKeeper 狀態造成結果。

這些結果顯示「自啟動」和「背景不受限制」是兩個不同條件。自啟動允許系統在適當情況啟動 App；它本身不表示 GMS 長連線不會被省電、網路管理或凍結機制中斷。`MILLET_NO_RESTRICT_APP` 已處理也只證明該設定值包含 GMS，不足以證明所有 OEM 背景限制均已解除。

目前的實測不是在同一台裝置、同一 ROM、固定網路和固定閒置條件下輪替單一變因的完整實驗。因此它能描述實際差異與改善方向，但不能單獨定位每次失敗的唯一原因。

## 本地 APK 靜態分析

分析工具為 Apktool 3.0.3，解包結果留在使用者電腦暫存目錄：

| 檔案 | SHA-256 | APK 內 PowerKeeper 版本資訊 |
|---|---|---|
| `E:\Windows\Downloads\com.miui.powerkeeper_A16.apk` | `29F4192EACCBC51749A3A6DEF73268B3EA138BA4ED881909724D5C5D36D8CE37` | `4.2.00`，`versionCode 40200` |
| `E:\Windows\Downloads\電量和性能_4.2.00.apk` | `164F2B6271EEF11AFAF5C97984011F01329173099ACCF69BADA3B815A26CBD44` | `4.2.00`，`versionCode 40200` |

依使用者確認，第一份是 HyperOS 3 的 PowerKeeper，第二份是 HyperOS 4 的 PowerKeeper；兩者版本碼相同。Manifest 建置平台／SDK 資訊不同：第一份編譯平台 API 36、`minSdk 36`、`targetSdk 35`；第二份編譯平台 API 37、`minSdk 37`、`targetSdk 36`。這些欄位描述 APK 建置資訊，不單獨說明 FCM 策略差異。

### `aurogon_enable` 和 `GlobalFeatureTable`

兩份 APK 都有以下可確認路徑：

- `com.miui.powerkeeper.provider.GlobalFeatureConfigure` 定義 `AUROGON_ENABLE = "aurogon_enable"` 及 `TABLE = "GlobalFeatureTable"`。
- `GlobalFeatureConfigureHelper.getCloudAurogonEnable()` 先查詢 `GlobalFeatureTable`，再以 `aurogon_enable` 作為 Bundle key 取值。
- PowerKeeper provider authority 為 `com.miui.powerkeeper.configure`，表 URI 由基底 URI 附加 `GlobalFeatureTable`，即 `content://com.miui.powerkeeper.configure/GlobalFeatureTable`。
- Manifest 將此 Provider 設為 exported，但要求 `miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER`；該權限是 `signatureOrSystem` 保護。
- `LocalUpdateUtils`／`CloudUpdateHideMode` 將雲端 feature `aurogon_enable` 寫入這個內部設定 Provider。

兩份 APK 內都找不到 `GlobalFeatureConfigureHelper.getCloudAurogonEnable()` 的其他呼叫者（除定義位置外），也沒有找到這個 feature 直接呼叫 `Settings.Global.getString/putString` 的路徑。因此可確認的是「PowerKeeper 有自己的同名雲端 feature 儲存流程」，不能由此宣稱它就是 FCMGuard 寫入的 Android Global setting，也不能宣稱兩種值在 ROM 中必定互相同步。

### GMS observer 的版本差異

兩份 APK 的 `com.miui.powerkeeper.utils.GmsObserver` 確有程式碼架構差異：

- HyperOS 3 APK 有 `gms_control` 狀態、對 PowerKeeper `UserConfigure` 的 observer、`gms_wall` 網路 chain，以及更新 GMS 網路／鬧鐘狀態的邏輯。
- HyperOS 4 APK 的 `GmsObserver` 會檢查 GMS 是否在任一使用者中 active，並透過 `greezer` Binder 介面呼叫 `updateGmsNetStatus()`／`getGmsNetStatus()`，依 Google 可達狀態同步 framework GMS 網路限制狀態。

這些程式碼證明 PowerKeeper 對 GMS 網路管理的整合方式有變化，是可能相關的背景；但它不足以證明這就是「智慧」與「無限制」收訊差異的原因。本地 APK 只覆蓋 PowerKeeper，真正的凍結策略還在系統服務中。上游針對其他裝置的同版本 PowerKeeper 比較，也指出工作／故障裝置間 `GmsObserver` 的語意差異很小，而更明確的成敗差別位於 `miui-services.jar` 的 Greezer 實作。故此處應視為線索，不能當作因果定論。

### MILLET 清單產生方式

兩份 PowerKeeper APK 的 `ActiveStateController.dealNoRestrictApp()` 都會呼叫 `UserConfigureHelper.getNoRestrictApps()`，再以逗號清單寫入 `Settings.System.MILLET_NO_RESTRICT_APP`。也就是說，此值是 PowerKeeper 私有使用者策略資料的投影，而不是可由 App 任意持有的永久白名單。使用者將 App 設成「無限制」會影響這份來源策略；直接追加 GMS 可能在 PowerKeeper 重建清單時被移除。

本次兩份 APK 的輸出都可看到上述相同資料流；兩份 APK 都沒有證明「智慧模式」下 FCM 的完整策略。相應版本的 `miui-services.jar` 現已取得並靜態比對，結果見下節；SecurityCenter 策略及實際 runtime 命中仍需裝置記錄交叉驗證。

## 本地 `miui-services.jar` 靜態比對

使用 JADX 1.5.6 對兩個 JAR 內的 DEX 做反編譯與差異搜尋。以下 hash 用於固定樣本身分；版本標籤依檔名記錄，尚未從相應手機 `getprop` 或系統分區讀回核對。

| 樣本 | SHA-256 |
|---|---|
| `docs/rom-samples/miui-services/miui-services-OS3.0.319.0-WPBCNXM.jar` | `0CA8BEE568F2D4607AA6393764338D5B52FCD0101CDCE49C39D8051A3FDC4C70` |
| `docs/rom-samples/miui-services/miui-services-OS4.0.2.0.XPBCNXM.jar` | `F1EB7926D86CE4A4F41AB35FFCB90D9D52D89CE8A3E42BA4D737691C1E000693` |

### 中國版判定來源

- OS3 的 `PolicyManager.CN_MODEL` 是靜態常數，實作為 `"CN".equals(SystemProperties.get("ro.miui.region", "unknown"))`。也就是讀取 `ro.miui.region`，只有值精確等於大寫 `CN` 才回 true；缺值時使用 `unknown`，結果為 false。
- OS4 的 `PolicyManager.isCnModel()` 轉呼叫 `PolicyManagerConfig.isCnModel()`；初始值為 `"CN".equals(Build.getRegion())`。`GreezeManagerService.onDeviceProvisionedChanged()` 也會重新讀 `Build.getRegion()` 並呼叫 `setCnModel()`，因此 OS4 這個快取判斷可在該 callback 被更新。setter 同時設 `ConfigManager.isGlobal = !cnModel`。
- 在這兩份 JAR 顯示的直接判斷式中，輸入是 region 字串，條件是精確字串比較；沒有直接讀取語言、SIM 或 GPS。OS4 的 `miui.os.Build.getRegion()` 實作不在這兩份 `miui-services.jar` 中，因此仍不能由此確認它底層使用哪個來源。
- 在使用者提供的 OS3／OS4 ROM 檔名中，`CNXM` 是版本識別資訊；它本身不等於 runtime 已讀到 `CN` 的證據。

#### ADB 能確認的範圍

用唯讀的 ADB property 查詢記錄每台目標裝置的 property：

```powershell
adb devices -l
adb shell getprop ro.miui.region
```

若同時連接多台裝置，先從 `adb devices -l` 選定序號，再執行：

```powershell
adb -s <serial> shell getprop ro.miui.region
```

- OS3 JAR 直接以這個 property 判定；只有輸出精確為 `CN` 才代表該段 predicate 為 true。空值或其他字串都不是 CN。
- OS4 查詢可確認 property 當前值，但因 framework 走 `Build.getRegion()` wrapper，單獨的 `getprop` 輸出不能證明 wrapper 最終回傳相同值。還需要取得該 ROM 提供 `miui.os.Build` 的 framework class，檢查 `getRegion()` 實作；或在 runtime 取得該方法回傳值。該 class 不在目前提供的 `miui-services.jar` 反編譯輸出中。
- 以上查詢不會切換 region 或修改設定。尚未連接手機執行，因此本報告沒有任何實機 property 值，也未確認 OS3／OS4 裝置實際採用哪個分支。

#### 對 Greezer 的影響

OS4 `AurogonImmobulusMode.isNoRestrictFreezeable()` 以 `PolicyManager.isCnModel()` 為條件之一：只有 CN model 且 `EventReceiver.isInSleepMode()` 時，才進一步檢查 `greezer_no_restrict_ctrl_white_list`；不在該清單內的 no-restrict package 會被判為仍可凍結。這是 OS4 no-restrict freeze 例外中的一個 gating condition。單有 `ro.miui.region=CN` 不足以證明該分支已實際凍結 GMS，還需 sleep mode、resource membership、freeze 決策與 UID 狀態等證據。

OS4 的獨立 night Deep Idle 路徑檢查夜間、device idle 與螢幕狀態，並對 MILLET entries 發出 `nightDoze` freeze request。這條夜間路徑和 `isNoRestrictFreezeable()` 中的 CN model + sleep mode filter 是不同條件鏈，不應把兩者合併成一個「中國版睡眠模式」開關。

### no-restrict 清單與凍結過濾器

- OS3 `AurogonFilterManager.filter()` 在 `MSG_FILTER_NO_RESTRICT_CASE` 命中時，只要 package 在 `MILLET_NO_RESTRICT_APP` 解析出的集合內，就略過該次凍結候選。
- OS4 同一分支改為：若 package 在 no-restrict 集合，只有 `isNoRestrictFreezeable()` 為 false 才略過凍結。該方法在中國版且 `EventReceiver.isInSleepMode()` 為真時，會讓不在 `ListAppsManager.getNoRestrictCtrlWhitelist()` 的 no-restrict package 仍可凍結。
- OS4 的 whitelist 是 framework resource `greezer_no_restrict_ctrl_white_list` 載入；JADX 輸出未還原此資源的實際 package 清單，因此不能僅憑此 JAR 宣稱 GMS 必在或不在該 whitelist。若 GMS 不在其中、並命中中國版睡眠條件，OS4 這條 filter 會移除 MILLET no-restrict 的豁免效果。

### OS4 夜間 Deep Idle 凍結路徑

- OS4 `EventReceiver` 在本地時間 23:00 排下一次 `NIGHT_FREEZE` alarm；收到該事件或偵測到 night + device Deep Idle + 螢幕關閉時，若 `isNightDeepIdleStationary()` 仍成立，就送出 handler message 25。
- `PowerStrategyMode` 處理 message 25 時呼叫 `ActionExecute.dealNightFreeze()`。該方法取 `MILLET_NO_RESTRICT_APP` 中的所有 package，解析其 app UID，並以 reason `nightDoze` 呼叫 `delayFreeze(..., true)`。
- 這條路徑在呼叫點明確以 no-restrict 清單作為夜間 freeze 的候選來源；是否成功凍結某 UID 還受 `delayFreeze` 內部條件、政策與執行時狀態影響。JAR 靜態碼證明 OS4 有這條路徑，不能單獨證明特定 GMS UID 在某台裝置上實際被凍結。
- OS3 的 `EventReceiver`／`PowerStrategyMode`／`ActionExecute` 未找到對應的 night-freeze message、`dealNightFreeze()` 或 `nightDoze` 字串；其 `AurogonFilterManager` 的 no-restrict filter 是直接豁免。這是兩份 framework 樣本可確認的版本差異。

### `com.miui.game.allowlist` 與 `aurogon_enable`

- OS3、OS4 的 `AurogonImmobulusMode` 都觀察 `Settings.System.com.miui.game.allowlist`，以底線分隔更新 allowlist；`GreezeManagerService.preCheckInterest()` 對命中的 UID 跳過該次 Greezer interest/freeze 檢查。這確認該 key 在兩份 framework 都有消費者，但此靜態比對不量化它對其他 freeze path 的覆蓋範圍。
- OS3、OS4 的 Greezer 都讀取 `Settings.Global.aurogon_enable`。`broadcastctrl:true#package/action` 會令 `parseAurogonEnable()` 開啟 broadcast control 並把 package/action 寫入 map；`isAllowBroadcast()` 會依被呼叫端 package 及 action 查這個 map，進而影響特定廣播的放行、解凍或延後。FCMGuard 自動同步的規則對象是具有 FCM receive handler 的接收 App package，不是 GMS 的 transport socket；它寫入 `com.google.android.c2dm.intent.RECEIVE` 規則，和 framework parser 的格式吻合。
- 因而 Aurogon broadcast rule 有可追蹤的 framework 消費路徑，可改善特定 receive broadcast 的處理；它不等同 no-restrict/night-freeze 豁免，也不能證明 GMS socket 長連線不再中斷。不能單以加入該規則後收訊改善，推論它阻止了 Greezer 凍結。
- OS3、OS4 另都有 GMS network limiter 程式碼，OS4 路徑在螢幕關閉時會從 Aurogon allowlist 移除 GMS 並對 GMS UID 執行限制處理。此設定與 no-restrict/night freeze 是不同控制面；觸發狀態和裝置是否走到該分支仍須 runtime 記錄確認。

### 靜態比對結論

本地 framework 樣本使 OS3／OS4 的差異比原先只看 PowerKeeper APK 更清楚：OS3 的 no-restrict filter 是直接豁免；OS4 加入中國版 sleep-mode 例外及獨立夜間 Deep Idle no-restrict freeze 流程。這與使用者描述的「OS3 智慧可收、OS4 需要無限制」相容，並提供了具體候選機制，但仍不是該裝置漏訊息的因果證明。GMS 是否在 OS4 資源 whitelist、當時 sleep/deep-idle 條件、freeze 是否成功、socket 是否掉線、以及受控 FCM 是否送達，都要在目標裝置逐層驗證。

## 上游研究提供的系統層證據

### 一般 GMS／Greezer 調查

上游調查以三台中國版 HyperOS 裝置做 ADB 實測，並分析裝置上的 `miui-services.jar`、PowerKeeper APK 和 GMS APK。研究指出有多條獨立 Greezer 路徑：GMS 專用 quick-freeze limiter、一般 `PowerStrategyMode` 背景凍結，以及其他網路／Doze 控制。`MILLET_NO_RESTRICT_APP` 會參與部分凍結過濾，但不是所有控制的總開關。把 Play Store 設為無限制也不會自動把 GMS 加入 MILLET，因為策略是套用到各自 package。

研究的設備比較另指出，即使 PowerKeeper APK 完整雜湊不同，若主要 DEX 與相關程式語意相同，仍不能把 FCM 成敗差異歸因於 PowerKeeper；當時最清楚的差異在 `miui-services.jar` 的 Greezer 程式碼。這對本次比較很重要：不同 PowerKeeper 程式碼可能參與背景管理，但真正攔截 FCM 的元件可能是 framework Greezer。

### HyperOS 4 夜間凍結調查

夜間研究在特定中國 ROM（HyperOS 4.0.21.0、Android 17、PowerKeeper 4.2.00）確認：熄屏、夜間（23:00–07:59）、Deep Doze 條件下，`AurogonImmobulusMode` 會令部分 no-restrict App 再次具有夜間凍結資格，並由 `NIGHT_FREEZE`／deep-idle 路徑要求凍結，原因標記為 `nightDoze`。研究當時的 GMS 即使已在 `MILLET_NO_RESTRICT_APP`、舊夜間省電開關關閉且有 Android Doze exemption，仍曾被凍結，FCM socket 也消失。

該研究測得將 GMS 加入 `Settings.System["com.miui.game.allowlist"]` 後，後續測試的 deep-idle 階段保持 GMS 未凍結及 FCM socket 存續；移除豁免則重新出現凍結。這是特定裝置與 ROM 的內部 workaround。它全天有效，不等同關閉 Deep Doze，也不保證所有 Greezer freeze path 都被排除。解凍廣播與 `GCM_RECONNECT` 是分開步驟；廣播發出本身不等於 FCM 推送已送達。

### MILLET 重寫調查中的 Aurogon 註記

上游 MILLET 重寫報告明確區分 `Settings.Global.aurogon_enable` 與 `MILLET_NO_RESTRICT_APP` 的 writer／生命週期：該報告觀察的 framework 會在 Greeze／system-server 初始化或相關 Xiaomi CloudData 變化後重新套用 Aurogon 設定；保留的紀錄未見像 MILLET 那樣由輪詢發現的頻繁覆寫。因此其實作只在保護／設定操作時同步 Aurogon，並以既有 15 分鐘 recovery worker 作補救，不把 Aurogon 放進 MILLET 的高頻監看迴圈。

但三份公開調查沒有說明作者最初如何發現 `aurogon_enable`，也沒有在文件中記錄最初探索該 key 的完整步驟。更沒有說明 PowerKeeper 的內部 `GlobalFeatureTable` 和 `Settings.Global.aurogon_enable` 在不同 HyperOS 版本的橋接關係。

## 對 OS3／OS4 FCM 差異的判讀

依現有證據，最合理但仍待裝置級驗證的模型如下：

1. 自啟動開關控制 App 被啟動的資格，不單獨保證 GMS 長連線存活。
2. 「智慧」與「無限制」的差異由 PowerKeeper／SecurityCenter 的 per-app 策略及其衍生策略共同形成；MILLET 只反映其中 no-restrict package 投影。
3. 本地 OS4 framework code 有兩條與 no-restrict 相關的凍結機制：中國版 sleep-mode filter 例外，以及夜間 Deep Idle 對 MILLET package 發起 `nightDoze` freeze。OS3 樣本未見同一例外／排程。這使「OS4 背景策略比 OS3 強」有本機靜態證據；GMS 是否命中仍未知。
4. `aurogon_enable` 的 `broadcastctrl` 在兩份 framework 都有直接 parser 與 broadcast decision consumer。加入規則可解釋特定 broadcast 被允許的改善；它不是對 freeze 的豁免。收訊改善可能來自 broadcast dispatch，不能據此認定 Greezer 沒有凍結 GMS。
5. 因此「OS3 不需 Aurogon、OS4 加上後改善」與現有研究相容，但還不能證明這是唯一原因，也沒有量化 OS4 的夜間可靠度。

特別修正一項容易混淆的推論：雖然本地兩份 PowerKeeper APK 的 `GmsObserver` 變化很明顯，不能因此直接說「OS4 的 PowerKeeper GmsObserver 就是自啟動＋智慧失效的原因」。上游研究將其同版本裝置的成敗主要定位到 `miui-services.jar` Greezer，故要把責任落到 PowerKeeper 或 Greezer，必須用使用者這兩台／兩版系統的實際 framework 證據驗證。

## 已確認、推論與未驗證事項

| 項目 | 狀態 | 證據界線 |
|---|---|---|
| 兩份 APK 都含 PowerKeeper `aurogon_enable` 雲端 feature | 已確認 | APK 常數、helper、CloudUpdate 寫入路徑 |
| PowerKeeper 有受限的 `GlobalFeatureTable` ContentProvider | 已確認 | Authority、URI 與 Manifest 權限 |
| 兩份 APK 都從私有 UserConfigure 產生 MILLET 清單 | 已確認 | `getNoRestrictApps()` → `Settings.System.putString()` |
| 本地 OS4 framework 可對 MILLET no-restrict entries 發起夜間 `nightDoze` freeze | 本機 JAR 靜態確認 | GMS runtime 是否在候選/凍結結果內未知；需核對 resource whitelist 與 freeze log |
| OS3 no-restrict filter 直接豁免、OS4 加入 sleep-mode freeze 例外 | 兩份本機 JAR 靜態確認 | 不能證明使用者裝置當時命中該條件 |
| OS4 特定 ROM 可在 MILLET 已含 GMS 時凍結 GMS | 上游特定裝置已實測 | 不可外推為所有 OS4 ROM 或每次漏訊息 |
| OS3 自啟動＋智慧穩定收、OS4 同條件較差 | 使用者實機觀察 | 本報告未重新執行該實驗 |
| OS4 加入 `aurogon_enable` 後改善 | 使用者實機觀察 | 改善幅度與長時間穩定度未知 |
| PowerKeeper 內部 Aurogon feature 與 Android Global key 同步 | 未證實 | 本次 APK 靜態分析未見直接連結 |
| OS4 每次 FCM 失敗都由 nightDoze 造成 | 未證實 | 需事件時間、凍結日誌與 socket 狀態對照 |
| `aurogon_enable` broadcastctrl 是否能改善對應 receive broadcast 的處理 | framework parser/decision path 已確認 | 實際 GMS / 接收 App 是否命中須看設定及 runtime log |
| `aurogon_enable` 是透過阻止凍結而改善 FCM | 未證實 | framework 顯示 broadcast control 與 freeze policy 是不同路徑 |

## 建議的裝置驗證方式

若之後要把可能原因收斂到單一機制，應固定網路、GMS 版本、通知來源、螢幕關閉／閒置時間，並分別記錄：

1. 系統電池策略（智慧／無限制）、自啟動狀態、`MILLET_NO_RESTRICT_APP`、`aurogon_enable` 及 game allowlist 的測試前後值。
2. FCM 訊息發送時間、裝置收到時間，以及螢幕解鎖前後差異；每種條件多次重複，測試順序交替以減少時間因素。
3. Greezer 的 GMS 凍結／解凍事件及原因（例如 `nightDoze`），與 GMS UID 狀態、FCM socket 連線狀態的時間線。
4. Aurogon 廣播規則單獨開／關的比較，避免同時變更 battery mode、MILLET、game allowlist 或網路設定。
5. 重新開機及 CloudData／PowerKeeper 更新後重新讀取規則，確認設定持續性；不能把 Settings 值仍存在視為 framework 已載入或 FCM 已有效。

在沒有合法測試訊息來源、無法取得凍結記錄或無法觀測 GMS socket 時，應把相應項目標記為未驗證，而不是用「收到一次」推定整夜穩定。

## 深入機制：自啟動、電池策略、MILLET、Greezer、FCM 是不同層

### 自啟動不等於背景豁免

使用者介面中的「自啟動」通常表示允許系統在開機、廣播或其他背景事件中啟動 App。這只處理「能不能被啟動」的一部分；它不保證以下事項：

- GMS 的既有行程不會被 freezer 凍結。
- GMS 的 UID 不會被 Xiaomi 網路管理鏈限制。
- GMS 到 FCM/MCS 伺服器的既有 TCP socket 一直存活。
- FCM 到達後，目標 App 能順利建立 process、執行 receiver 或顯示通知。

FCM 是送到共享 GMS transport，再由 GMS 將訊息分派給各個 App。當 GMS 被凍結或 socket 消失，多個 App 可能同時延遲；反過來，GMS socket 存活也不保證每個 App 的通知一定顯示，因為目標 App 的通知權限、channel、App 自己的電池限制及訊息優先級仍會影響結果。

### 「智慧」與「無限制」在 PowerKeeper 資料裡的表示

上游 GMS/Greezer 調查記錄了 SecurityCenter 每 App 電池策略送進 PowerKeeper 的值：

| SecurityCenter 選項 | 傳遞的值 | PowerKeeper `userTable.bgControl` |
|---|---|---|
| 智慧／電池 saver／推薦 | `miui_auto` | `miuiAuto` |
| 無限制 | `no_restrict` | `noRestrict` |
| 限制背景 App | `restrict_bg` | `restrictBg` |
| 限制背景活動 | `no_bg` | `noBg` |

PowerKeeper 對 `noRestrict` 的來源列生成 `MILLET_NO_RESTRICT_APP`。因此，「智慧」不是「已加入無限制清單但另有不同顯示名稱」；它是另一種策略值。只要 GMS 在該資料列仍是 `miuiAuto`，由 `getNoRestrictApps()` 生成的 no-restrict 投影就不應被視為 GMS 已有 `noRestrict` 使用者策略。直接把 GMS 寫到 Settings 中，只是改了下游投影，沒有改 PowerKeeper 私有來源列；PowerKeeper 重建時可將手動加入的 GMS 刪掉。

這是目前最直接解釋「OS4 要明確切無限制」的資料模型。但還需注意，上游調查指出 PowerKeeper 也可能在特定舊版 policy logic 中把 GMS 視為不受某些限制，而新版本／不同 framework 未必一樣；本地 APK 靜態分析沒有把 SecurityCenter 點選、PowerKeeper userTable 及所有 Greezer 路徑完整連成一次 OS3／OS4 同裝置對照。因此這個模型與使用者觀察相符，仍需用實際 userTable、MILLET 值及凍結原因驗證。

## Greezer 凍結路徑詳解

上游調查強調 HyperOS 中有多種名稱近似但彼此獨立的「白名單」或「GMS control」。不能把其中一份清單視為整個系統的總豁免。

### No restrictions 投影與一般 freeze filter

在受測 ROM，SecurityCenter 將特定 App 的策略寫到 PowerKeeper `UserConfigure`。PowerKeeper 的 `ActiveStateController` 監聽該 Provider，當列變更時重建 `MILLET_NO_RESTRICT_APP`。Greezer 再監聽這個 Settings key，按逗號分割並存入 package set。

在一般快速凍結路徑中，`AurogonImmobulusMode` 對命中 `mNoRestrictAppSet` 的 package 提前返回；在 `PowerStrategyMode` 路徑中，`AurogonFilterManager` 的 bit 64 也讓 `PolicyMaker` 對此 package 回傳 `CANNOT_FREEZE`。因此在受測 build 的一般路徑，把 GMS 放入這份清單確實能擋住多種 Greezer freeze 請求。

限制是這不是無條件、跨所有模式的永久豁免。上游資料指出某些 Extreme mode bulk freeze 路徑刻意忽略該 set；PowerKeeper 也可以重建並覆寫清單。此外夜間凍結是另一條特例路徑，須依夜間研究的實際 framework 程式判斷。

### GMS 專用 quick-freeze limiter

上游研究在兩個失敗 build 的 `GreezeManagerService` 中看到 `mGmsLimitEnabled` 初始為 true。熄屏時 GMS UID 變為 active，observer 延遲約 10 秒送出 GMS limiter 動作。該動作會先從 Aurogon 一般記憶體 allowlist 移除 GMS，再對各 Android user 的執行中 GMS UID 請求 quick freeze。

這個一般 Aurogon `mAllowList` 與 `MILLET_NO_RESTRICT_APP` 對應的 `mNoRestrictAppSet` 不是同一份清單。研究指出，即使 GMS 被從前者移除，快速凍結檢查仍會檢查後者。因此在該測試路徑中，MILLET 項目比只恢復一般 Aurogon allowlist 更具保護力。這也說明為什麼某個名字含 allowlist 的設定值存在，不能直接推論凍結一定會被阻止。

### PowerStrategyMode 背景路徑

前景 Activity 消失後，Greezer 可用 `tobg` 原因排程背景 freeze；上游找到的延遲常數約 5 秒。系統元件 thaw 某 UID 後，也可能用 `from system` 原因重新走相同 `PolicyMaker` 判斷及 freeze 流程。研究在一台裝置上看到，即使關閉 GMS 專用 limiter 並把 GMS 加回一般 Aurogon allowlist，鎖定／解鎖仍發生 `tobg` 與 `from system` 凍結；共用的 no-restrict filter 才能擋住這些已測路徑。

因此「關掉一個 GMS limiter」、「加入一般 Aurogon 許可清單」、「處理 MILLET」分別作用於不同層；某一步成功不代表其他層已經不會操作 GMS。

### 夜間 Deep Doze 路徑

夜間研究指出，受測 OS4 framework 的條件大致是：

1. 本地時間在 23:00–07:59。
2. 螢幕關閉。
3. `PowerManager.isDeviceIdleMode()` 為 true。
4. `AurogonImmobulusMode.isNightDeepIdleStationary()` 判定夜間 deep-idle 情境。

這條路徑不依賴舊 PowerKeeper 夜間省電開關；研究也說明該方法本身沒有直接檢查是否移動或正在充電。每天 23:00 有 `NIGHT_FREEZE` 排程，進入夜間 deep-idle 時也會觸發；`ActionExecute.dealNightFreeze()` 最終請求 freeze，日誌原因是 `nightDoze`。

這就能解釋一種表面矛盾：GMS 已出現在 `MILLET_NO_RESTRICT_APP`，但夜間仍被凍結。上游受測 framework 的 `isNoRestrictFreezeable()` 會依 `greezer_no_restrict_ctrl_white_list` 決定哪些 no-restrict package 在睡眠模式仍保持豁免。本地 OS4 JAR 確認使用同名 resource，但 JADX 輸出沒有還原其實際內容；需從 ROM resource 或裝置查明 GMS 是否列入，不能假定所有 GMS 身分／使用者空間都會走相同判定。

## `com.miui.game.allowlist` 的格式、效果及限制

受測 framework 以底線 `_` 連接 package name，並透過 Java `String.split("_")` 解析後做完整 package 比對。它不會 trim token；JSON、逗號、子字串都不是有效替代格式。尾端底線造成的空 token 會在 Java split 行為下被丟棄；package name 本身若包含底線則無法完整表示，是格式先天限制。

`GreezeManagerService.freezeUids()` 的 `preCheckInterest()` 會呼叫 `isGameAllowListApp(uid)`；命中遊戲豁免清單時，該次較低層 freeze 請求會被拒絕。這個豁免沒有夜間條件，所以清單生效期間白天也可能生效。它不會關閉 Deep Doze，也不阻止更高層其他 component 嘗試管理 GMS，更不會自行 thaw 已凍結的 UID。

參考研究中的 controlled probe 很能說明「設定存在」與「實際恢復」是不同階段：寫入 game allowlist 後、執行 thaw 前，GMS 仍 frozen、socket 仍為 0；targeted thaw 後 GMS 已不 frozen，但 socket 仍為 0；發出 reconnect 後數秒 socket 仍未建立；在後續 idle entry 約 12 秒後才觀察到 established FCM socket。研究在隨後兩次 idle entry 也觀察到未凍結與一條 socket，移除豁免則再次看到 OEM freeze 與 socket 消失。這是短時段受控觀察，研究明確沒有據此宣稱整夜可靠或實際測試推播已送達。

同一調查把三個結果分開：清單 membership 防止後續特定 freeze；解凍廣播嘗試恢復已凍結 UID；`GCM_RECONNECT` 要求重連。前一項不會自動完成後兩項。任何 App 發送廣播後，最多只能說已呼叫／派送廣播；如果沒有可讀的 frozen state 或 socket 證據，解凍與 FCM 恢復都應報為未知。

## `aurogon_enable`：兩種同名資料來源及其用途

### Android Settings Global

FCMGuard 本地程式目前讀寫 `Settings.Global["aurogon_enable"]`。`AurogonProtection` 以目前 App UID 執行 `Settings.Global.getString()`／`putString()`。手動「清空設定」與「同步」按鈕在缺少 `WRITE_SECURE_SETTINGS` 時會禁用；授權後才可從 UI 操作，並依 `putString` 結果和讀回候選值判定成功或失敗。GuardService 的週期同步也會先檢查該權限。底層手動寫入方法仍依平台寫入與讀回結果判定，UI 不提供無權限時的觸發入口。

`AurogonConfig` 把 Aurogon 值視為分號分隔的多個 section；enabled broadcast-control section 形如 `broadcastctrl:true#package/action#package/action`。FCMGuard 以自有 marker `com.rsps1008.fcmguard/__FCMGUARD_AUROGON_AUTOSTART_V1__` 標示自己管理的 section，並嘗試保留其他 section。規則中 package 與 action 以 `/` 分隔；`ALL` 或明確 `com.google.android.c2dm.intent.RECEIVE` 可匹配該 package 的 FCM receive 規則。

本專案目前自動同步會從可讀取的 Xiaomi Autostart AppOps 與可見 FCM receiver/service 掃描結果取交集；只對符合條件的 package 新增規則。不可讀狀態不會擴大新增範圍，對先前已管理但現在狀態未知的項目採保留既有規則方式處理。Engine 在變更前重讀 Settings，若第一次讀與寫前讀回不同，回報偵測到競態並停止寫入；寫入後再次讀取，必須 `putString` 回 true 且讀回等於預期候選值才標為 verified。Settings 沒有 CAS，故這仍無法消除「最後一次讀取和 PUT 之間」的極短競態。

GuardService 的 Aurogon fallback 間隔為 30 分鐘，只在 Aurogon 開關啟用且權限仍存在時執行；這是進程內 Handler，不是精確鬧鐘，也不會喚醒休眠 CPU。若 App process 被系統停止，沒有服務在背景存活的保證。手動同步和週期同步共用同一個 App UID scan/write path，但手動操作現在會在 Secure 權限缺失時仍嘗試寫入。

### PowerKeeper 私有 GlobalFeatureTable

兩份 PowerKeeper APK 中的同名 feature 是 PowerKeeper 內部 CloudData 設定欄位。Provider 的 Manifest 權限是 `signatureOrSystem`，普通第三方 App 沒有理由假設可以呼叫它。PowerKeeper helper 用 `ContentResolver.call()` 查詢 `GlobalFeatureTable`，更新 helper 也透過 provider 寫入；這是 PowerKeeper 自己管理 CloudData feature 的介面。

名稱相同只代表都叫 `aurogon_enable`，不代表 storage、schema、權限或 writer 相同。本次靜態搜尋沒有找到 `getCloudAurogonEnable()` 被其他 PowerKeeper 類別呼叫，也沒有找到它和 Android `Settings.Global` 之間的橋接。新比對的兩份 `miui-services.jar` 確認 Greezer 直接讀取 Android `Settings.Global.aurogon_enable`，但沒有證明 PowerKeeper 私有 `GlobalFeatureTable` 會同步到該 key；跨元件橋接仍未知。

### Aurogon 與 Greezer 的關係

術語容易混淆：一方面，Aurogon 是 Greezer 冻結程式碼中的類別／allowlist 概念；另一方面，`aurogon_enable` 是 PowerKeeper cloud feature 名稱及 FCMGuard 寫入的 Global Settings key。本地兩份 framework 都證實 `AurogonImmobulusMode.parseAurogonEnable()` 讀取這個 key，並把 `broadcastctrl` 設定交給 `GreezeManagerService` 的廣播控制與 package/action map。這確認 broadcast consumer；本次沒有發現它會改寫夜間 freeze eligibility，兩者應視為不同 policy path。

因此 OS4 上 `aurogon_enable` 加入後改善 FCM，可能是廣播控制規則增加了 GMS／接收 App 的分派許可，也可能與其他恢復步驟同時發生。除非同時觀察 framework 配置、GMS frozen state、FCM socket 與實際 push，不能把改善直接寫成「已阻止 Greezer 凍結」。

## `MILLET_NO_RESTRICT_APP` 與自動修復的耐久性

MILLET 是逗號分隔 package list。PowerKeeper 查詢 owner user 的私有 `UserConfigure` rows，篩出 `bgControl=noRestrict`，再建立完整結果覆寫 Settings；它不是先讀目前值再合併。所以 FCMGuard／其他工具追加的 GMS 可能在使用者更改另一個 App 策略、開機 reconciliation、package inventory reconciliation 或首次查詢某個 App 時被整份重寫。

上游調查記錄了實際覆寫測試：shell append GMS，使用者切換另一 App 的 Xiaomi 背景策略，PowerKeeper 立即以私有 rows 生成完整新值，GMS 手動項目消失。保留的追蹤還看到某些覆寫／修復事件以 16–108 秒間距成串出現，另一些時間長達數小時平靜；不是穩定的週期 timer。報告分析到 Greezer 某個延遲 freeze 約 5 秒，所以其 Shizuku 實作採 2.5 秒 app-process watchdog。這個頻率是該實作對其 shell service 與 ROM 事件時序的選擇，不能直接移植到普通 App 的高頻喚醒排程。

Aurogon 則不同：同一重寫調查表示沒有觀察到類似的短週期 overwrite，故採 protection/config 操作時同步，加 15 分鐘 recovery worker。這是該調查 build 的觀察，PowerKeeper／CloudData 在其他 ROM 版本仍可能改變。

## FCMGuard 專案現況與能力邊界

本次報告對照的專案程式位置（供接手研究者定位）：

| 元件 | 責任 |
|---|---|
| `AurogonConfig.java` | 解析／合併 Aurogon sections，marker 管理、App/package 規則匹配與 Autostart AppOps 結果處理 |
| `AurogonProtectionEngine.java` | Settings 讀取、重讀競態檢查、寫入、讀回驗證與錯誤結果 |
| `AurogonProtection.java` | 權限檢查、App UID Global I/O、同步狀態持久化、掃描並同步目前 Autostart FCM Apps |
| `GuardService.java` | 在服務存活期間執行 30 分鐘 Aurogon fallback；不等同永遠背景存活 |
| `BootReceiver.java`／`MainActivity.java` | 根據使用者開關與權限處理服務啟動、手動同步與狀態 UI |

`WRITE_SECURE_SETTINGS` 對一般 App 不是一般 runtime permission。此專案提供由使用者透過 Shizuku 授權 App 自身權限的流程；手動 `Settings.Global` 操作現在直接嘗試，最後以例外、`putString` 回傳值與讀回確認平台實際是否允許。這不把 `Settings.System.canWrite()` 視為 `Settings.Global` 寫入授權。背景週期同步仍要求 Secure 權限。

本地程式碼的 Aurogon「清空設定」按鈕會將整個 `Settings.Global.aurogon_enable` 寫成 null，成功後才清除本地管理記錄。它不是只刪 FCMGuard marker section，會移除其他 App／使用者既有規則；UI 需有清楚的破壞性操作確認。自動同步則會嘗試保留非 FCMGuard sections，停用自動同步不等同清空整個 Global key。

即使寫入已讀回驗證成功，最多證明 SettingsProvider 儲存值與候選字串一致；不能證明 PowerKeeper／system_server 已載入、GMS 已 thaw、FCM socket 已建立、或通知已到達。整條驗證鏈至少有下列獨立階段：

| 階段 | 可以證明什麼 | 不能證明什麼 |
|---|---|---|
| App 權限授予 | FCMGuard UID 有呼叫敏感 Global 寫入的權限 | 某個 ROM feature 接受或使用該值 |
| Settings 寫入及讀回 | 資料庫目前讀回預期值 | framework 記憶體快取已重新載入 |
| framework 規則／allowlist 命中 | 某一條特定 policy 應採排除／允許 | 其他 freeze、網路、process policy 不會操作 GMS |
| GMS 未 frozen | 觀察時 UID/process 未被凍結 | FCM TCP socket 已存活 |
| FCM socket established | GMS 當下有到 FCM 埠的 established socket | 每個 App 的訊息／通知必定成功 |
| 收到受控測試訊息 | 該時刻該 App 的端到端測試成功 | 長時間或整夜可靠度 |

## 本次研究執行紀錄與可重現線索

這次本機工作只做靜態解包與搜尋，沒有對手機寫入 Settings、切換電池模式、測試 ADB/Shizuku 權限或發送 FCM 訊息。PowerKeeper APK 由 Apktool 3.0.3 解包；framework JAR 使用 JADX 1.5.6 反編譯。JADX 對 OS3 樣本回報 4 個反編譯錯誤、OS4 樣本回報 26 個錯誤；本文只採用成功輸出的目標類別／方法，並以 DEX 字串搜尋交叉確認 key 名稱。JADX 輸出留在 `%TEMP%\fcmguard_os3_jadx` 與 `%TEMP%\fcmguard_os4_jadx`。已檢查的重點包括：

- Manifest 的 package、SDK 建置資訊、PowerKeeper Provider authority 及權限。
- `GlobalFeatureConfigure`、`GlobalFeatureConfigureHelper`、`PowerKeeperConfigureProvider`。
- `LocalUpdateUtils`、`CloudUpdateHideMode`、`CloudDatabaseHelper` 中的 Aurogon cloud feature 路徑。
- `ActiveStateController`、`UserConfigureHelper` 的 no-restrict rows 到 MILLET Settings 寫入路徑。
- `GmsObserver` 的欄位、方法、PowerKeeper GMS chain 與 Greezer Binder 行為。
- 對 PowerKeeper smali 搜尋 `com.miui.game.allowlist`、`nightDoze`、`NIGHT_FREEZE`；兩份 APK 均未命中。
- 對 framework `AurogonFilterManager`、`AurogonImmobulusMode`、`ActionExecute`、`EventReceiver`、`PowerStrategyMode`、`ListAppsManager`、`GreezeManagerService` 檢查 no-restrict freeze filter、夜間 freeze dispatch、allowlist 與 broadcast control call path。

本地 APK 解包時的臨時輸出曾存放於 `%TEMP%\powerkeeper_A16_analysis` 和 `%TEMP%\powerkeeper_4_2_00_b5126b1c7ae24dbf9346ebd451ea7d71`。這些是分析過程中的暫存，不是專案原始碼或可攜式來源；跨電腦重現應使用原 APK 和上述工具重新解包。

## 更嚴謹的實驗設計

### 先確認裝置與系統樣本

OS3、OS4 各自記錄完整 ROM build、Android API、PowerKeeper、SecurityCenter、Joyose、GMS 版本及 APK／framework hash。因為同為 PowerKeeper `4.2.00` 不代表 system framework、雲端設定、SecurityCenter 或 GMS 完全相同。每次開始前讀取並備份：

- `Settings.System.MILLET_NO_RESTRICT_APP`
- `Settings.Global.aurogon_enable`
- `Settings.System.com.miui.game.allowlist`
- 對應 App／GMS 的 PowerKeeper 背景策略（智慧或無限制）
- Autostart 狀態、Android user/profile、GMS UID
- 當下 Doze、螢幕、充電、電池 saver、網路連線條件

比較 OS3／OS4 時不可只對照同一個 APK versionCode；應同時識別 framework 和 SecurityCenter。不要在研究中改動無關的 LINE／App 自啟動或電池政策；先記錄，再只變更一項條件。

### 因子隔離矩陣

以可重複的受控 push 為結果，將下列條件分開做 A/B 測試；每組至少重複多次並交錯順序。若某項設定需改變，改完先讀回確認：

| 因子 | A | B | 主要要回答的問題 |
|---|---|---|---|
| PowerKeeper 電池策略 | 智慧 | 無限制 | 這一項本身是否改變 GMS freeze/socket/FCM？ |
| Aurogon 設定 | 原始／未加入目標規則 | 加入目標規則 | 是規則值還是伴隨的其他操作造成改善？ |
| game allowlist | 沒有 GMS | 有 GMS | 是否只影響夜間 Greezer pre-freeze check？ |
| MILLET | 不含 GMS | 含 GMS | 對一般 no-restrict 路徑的效果及是否被重寫 |
| 系統時段／idle | 螢幕亮／非 Doze | 熄屏／Deep Doze，跨夜間 | 是否只有特定狀態觸發凍結？ |
| GMS 狀態恢復 | 不發 thaw/reconnect | 按順序 thaw，再 reconnect | 已凍結後是否需兩個恢復操作？ |

為判斷 Aurogon 和 freeze 的關係，Aurogon 規則 A/B 組不得同時改 game allowlist；若 Aurogon 改善 push 但 frozen state 不變，較可能是 broadcast policy 而非直接防凍結。若 GMS 未凍結但 socket 不存在，須轉查網路限制或 GMS 自身連線重試。若 socket 存活但測試 App 沒通知，則查目標 App dispatch、通知權限與通知 channel。

### 每次測試要記錄的時間線

每一個事件以同一時鐘記錄毫秒或秒級時間，至少包含：設定變更前後、螢幕熄滅、Doze state 進入 idle、發送測試訊息、GMS freeze/thaw、socket 狀態、通知到達及解鎖。應保存原始輸出並遮蔽帳號、私有網路資訊及其他 App 無關資料。

結果分類應使用四層標籤：

1. **設定層**：值存在、寫入拒絕、被覆寫，或無法讀取。
2. **Framework 層**：查到相應 policy/observer 已載入，或無法檢查。
3. **GMS 層**：UID frozen/unfrozen、socket count、網路 chain；不可取得就填未知。
4. **推播層**：受控訊息立即到達、延遲到達、解鎖後到達、或逾時未達。

只有最後一層的實際訊息測試才是 push delivery evidence；它仍需多輪重複才能估算穩定性。Socket 或 Settings 單一快照不能替代端到端測試。

## 補充追查：既有三項保護以外的攔截機制

本節回答「MILLET、夜間保護、Aurogon 廣播規則以外，還有哪些機制會使 GMS 休眠或阻擋接收 App 被喚醒」。以下是兩份 JAR 的靜態證據，沒有操作手機或證明新增設定能改善實際到達率。行號指本次 JADX 暫存輸出，重新反編譯後可能改變。

### WakePath 與接收 App 的冷啟動審核

- OS4 `com/android/server/am/BroadcastQueueModernStubImpl.java:352` 的 `checkApplicationAutoStart()` 同時涉及 `checkBroadcastWakePath()`、自啟動 AppOps 與其他廣播派送條件；`checkSkipReceiver()` 在程序尚未啟動且 receiver 為 ResolveInfo 時走這條路。Aurogon 的 `isRestrictReceiver()` 是另一個檢查入口。
- 這裡對 `com.google.android.c2dm.intent.RECEIVE` 的直接豁免條件包含 `Build.IS_INTERNATIONAL_BUILD`。不能把國際版的豁免套到中國版，也不能把這個 Build 旗標直接等同前文的 `PolicyManager.isCnModel()`。
- OS3 同檔 `checkApplicationAutoStart():328` 也有上述國際版／C2DM 條件。這不是已證實的 OS4 新增退化。
- OS4 `com/miui/server/WakePathChecker.java:228` 的 `checkBroadcastWakePath()` 取 caller、callee、action、class、UID 與 userId，對跨 App 廣播呼叫 `isAllowedByWakePathRule(..., 2, userId)`。後者包含 AppRunningControl 的禁止執行檢查（約 284）與 WakePath 規則拒絕分支；部分規則也涉及已存活程序。
- 因此「GMS 沒被凍結」及「Aurogon 放行廣播」不等於「接收 App 必然能啟動」。自啟動狀態、WakePath 規則、程序存活狀態仍需分開判斷；也不能宣稱開啟自啟動會繞過所有 WakePath 規則。
- OS4 `SecurityManagerService.java:788` 的 `checkWakePathPermission()` 強制要求 `android.permission.UPDATE_APP_OPS_STATS`；`pushWakePathData()`、`pushWakePathWhiteList()` 等寫入入口呼叫這個檢查。目前未找到 FCMGuard 普通 UID 可以直接維護這些規則的入口。使用者透過系統 UI 調整個別 App 設定仍可不依賴 Shizuku。
- 裝置調查可尋找 `MIUILOG-WAKEPATH: call was rejected by wakepath`、`AppRunningControl, Reject`，並核對 caller 是否 GMS、callee 是否受測 App、action 與訊息時間。沒有日誌不能反證沒有攔截。

注意：`checkApplicationAutoStart()` 的 JADX 輸出含異常控制流程，以上只引用可辨識的呼叫與條件，不把整份 Java 輸出當成精確可編譯的原始碼。判定某個 App 的最後拒絕分支仍需 DEX 指令或實機證據。

### Aurogon Alarm 暫存：新的低權限候選入口

- OS4 `GreezeManagerService.java:4013` 的 `isNeedCachedAlarmForAurogonInner()` 對 `FULLY_FROZEN` UID 進行判斷；一般應用可能返回 true 並記錄 `cached alarm!`。
- `mAurogonAlarmAllowList` 命中的套件在相應分支返回 false，表示不要求走此項 Alarm 暫存。它不代表永久免凍結，也不直接表示解除其他 Alarm、背景執行或網路政策。
- 約 759 行的 observer 讀取 `Settings.System.cloud_aurogon_alarm_allow_list`，以逗號分隔套件名稱，並用讀到的清單取代記憶體清單。預設清單約 753 行為 `com.tencent.mm`、`com.tencent.mobileqq`、`com.xtc.watch`。OS3 同樣存在這個設定鍵與 Alarm 暫存邏輯。
- 因為屬於 Settings.System，這是值得驗證是否能沿用 FCMGuard WRITE_SETTINGS 模式的候選。僅從讀取端不能證明普通 App 寫入會被 ROM 接受、重開機會載入，或 GMS 的相關 Alarm 正受此路徑影響。
- 若後續實作，必須保留原有清單及內建預設的效果，不能直接覆寫成只有 GMS；先確認 observer 的載入與重開機行為。若現有保護已讓 GMS 不進入 FULLY_FROZEN，此白名單對 GMS 可能沒有額外收益。
- 目前分類為「找到機制與候選設定入口，未證實額外效益」，不宜直接預設開啟或宣稱第四層已完成。

### GMS 專用限制觸發

- OS4 `GreezeManagerService.java:4740` 的 `triggerGMSLimitAction`（JADX 呈現為 `lambda$updateGmsNetStatus$3`）在中國版、`mGmsLimitEnabled` 為 true、熄屏時，移除 GMS 的 Aurogon allowlist 項目，並對正在執行的 GMS UID 要求 `triggerQuickFreeze()`。
- `updateGmsNetStatus(boolean status):7003` 在 status 為 true 時排入上述動作。僅憑名稱無法把 true 解釋為「Google 網路正常」或「Google 網路故障」，需繼續追上游呼叫者的語義。
- OS3 也有 `triggerGMSLimitAction()`。它是另一個可要求凍結的來源，但不代表一定繞過後續共用免凍結檢查，也不能直接推論既有夜間／game allowlist 保護失效。

### 對目前產品的結論

目前最明確的剩餘風險是「GMS 通道健康，但目標 App 的啟動／喚醒政策仍拒絕派送」。可先沿用系統設定導引處理個別接收 App；普通 UID 尚無已確認可自動改寫 WakePath 的方式。Alarm allowlist 是新增非 Shizuku 保護的研究候選，但在現有方案已穩定的情況下，應先找到該暫存分支命中的證據再決定是否加入。此次只補充研究報告，未修改 App 行為、未執行裝置設定寫入或推播測試。

### 實機 ADB 試寫紀錄（2026-10-20）

- 使用者提供的連線位址 `192.168.2.3` 目前解析為 ADB serial `192.168.2.3:41237`；同時 adb 還列出其他裝置，因此後續命令均應明確指定此 serial。
- 裝置回報 `ro.miui.region=CN`、`ro.build.version.incremental=OS4.0.4.0.XPBCNXM`，GMS 已安裝，套件 UID 為 10138。
- 寫入前 `settings get system cloud_aurogon_alarm_allow_list` 回報 `null`；框架 dump 顯示 Greezer 已啟用。依靜態程式內建值，先前使用的預設為 `com.tencent.mm`、`com.tencent.mobileqq`、`com.xtc.watch`。
- 已執行 `settings put system cloud_aurogon_alarm_allow_list 'com.tencent.mm,com.tencent.mobileqq,com.xtc.watch,com.google.android.gms'`，讀回完全相同，表示 Settings provider 接受並保存此值。這不是框架讀取分支或功能改善的端到端證明。
- 試寫時 `dumpsys deviceidle` 顯示 `mState=ACTIVE`、`mScreenOn=false`、`mCharging=true`，因此未處於 Doze；GMS 主程序 PID 6909 存活，Alarm dump 可見 `gcm.HEARTBEAT_ALARM`、`gcm.TRIGGER_IDLE`、`gcm.ACTION_CHECK_QUEUE`。這些狀態不能證明其 Alarm 曾被 Aurogon 暫存或設定已改變推播結果。
- 本機 `dumpsys greezer` 可用，`dumpsys greeze` 不存在。尚未用可確認的介面取得 GMS 當下凍結狀態，也沒有做受控推播 A/B。因此本次只驗證了設定可寫／可讀回。
- 修改前值為 `null`。若需還原原本的設定狀態，可執行 `adb -s 192.168.2.3:41237 shell settings delete system cloud_aurogon_alarm_allow_list`；刪除會回到 framework 內建預設清單。試寫值保留原來三個預設項目並追加 GMS。

### FCM Guard 主頁管理卡片

- 新增預設關閉的 Aurogon Alarm 管理卡，維護 `Settings.System[cloud_aurogon_alarm_allow_list]`；與 Aurogon FCM 卡使用相同的 FCM/GCM 元件掃描與兩項 Xiaomi Autostart AppOps 都為 ENABLED 才新增的規則。
- 每次同步固定寫入 `com.tencent.mm`、`com.tencent.mobileqq`、`com.xtc.watch` 三個 ROM 內建項目。同步保留其他既有項目，只移除 SharedPreferences 有記錄、且目前已不符合條件的本功能新增項目；掃描不完整時不改 Settings。寫入前比對原值、寫後讀回驗證。
- 自動與立即同步共用 `syncCurrentAutostartApps()`，每次都固定加入 `com.google.android.gms`，即使 FCM client 掃描刻意排除 GMS；同時保留三個內建套件與非本功能擁有的項目。操作區第一列並排「還原預設設定」與「同步自啟動APP」，第二列為滿寬「查看目前被 Aurogon 鬧鐘保護的 APP」。還原會將清單重設為 `com.tencent.mm`、`com.tencent.mobileqq`、`com.xtc.watch`，移除 GMS 與其他現有項目；寫前核對、寫後讀回。還原後若自動同步仍開啟，之後同步會重新加入 GMS 與符合條件的 FCM App；卡片狀態會顯示目前已還原為三項。
- 使用者需授予 `WRITE_SETTINGS`。不需要 Shizuku。開關開啟時併入 GuardService 共用的 30 分鐘進程內兜底；另有手動同步和目前清單檢視。關閉開關不清除規則，與 Aurogon 自動同步卡行為一致。
- 這只維護 Aurogon 暫存 Alarm 的清單；不解凍 App、不處理 WakePath，且未證明改善端到端 FCM 到達。同步結果文案統一為「另加入 FCM 與預設服務」。本次含「還原預設設定」按鈕與指定排列的 release APK 已用使用者提供的 stockify.jks、alias key 簽署，並成功更新安裝於 192.168.2.3:41237 的 OS4.0.4 裝置（versionName 1.6.1、versionCode 31；SHA-256 `69FD33C55374D431DF2E09DC21F986B0BC43BBA7E8D3CCC9387E912DFB31E46D`）。本次已在手機啟動主畫面，修正後未再出現啟動閃退；未實際點擊還原或觸發同步，也未做推播 A/B。

## 來源

- [Xiaomi HyperOS GMS／FCM screen-off disconnection investigation](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)
- [HyperOS 4 nighttime GMS freezing and a non-root workaround](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)
- [Xiaomi MILLET_NO_RESTRICT_APP rewrite investigation](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-millet-no-restrict-app-rewrite-investigation.md)
- 本地 PowerKeeper APK：`E:\Windows\Downloads\com.miui.powerkeeper_A16.apk`
- 本地 PowerKeeper APK：`E:\Windows\Downloads\電量和性能_4.2.00.apk`
