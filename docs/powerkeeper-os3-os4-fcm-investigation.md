# Xiaomi HyperOS 3／4 的 GMS／FCM 背景限制研究報告

更新日期：2026-10-20

範圍：使用者提供的兩份 PowerKeeper APK、`dingwen07/hyperos-fcm-fix` 公開調查，以及本次對話提供的實機觀察。本文只對證據支持的範圍下結論，不把設定寫入成功等同於 FCM 必定送達。

## 摘要

目前最符合資料的解釋是：HyperOS 3 與 HyperOS 4 的差異不只是「自啟動」設定，而是 GMS 在背景／熄屏後還會受到的系統策略不同。使用者提供的實機對照為：在已處理 `MILLET_NO_RESTRICT_APP` 的情況下，OS3 設「自啟動＋智慧」可穩定收到 FCM；OS4 同樣設定仍會被干涉，需改成「自啟動＋無限制」才可收到。另有 OS4 加入 `aurogon_enable` 規則後比原先改善的觀察，但穩定度尚未量化。

上游研究已在 HyperOS 4 的特定中國版 ROM 上確認 Greezer 可凍結 GMS；其中夜間 Deep Doze 路徑甚至會讓已列入 `MILLET_NO_RESTRICT_APP` 的 GMS 遭 `nightDoze` 凍結。這支持 OS4 存在額外背景限制的解釋，但不能單憑此研究斷定使用者每一次漏訊息都是夜間凍結造成。

兩份本地 PowerKeeper APK 都包含 `aurogon_enable` 雲端 feature 與 `GlobalFeatureTable` provider 路徑。PowerKeeper APK 中的這筆內部雲端設定，不能直接等同於 App 寫入的 `Settings.Global.aurogon_enable`；本次靜態檢查沒有找到兩者間的直接讀取連結，也沒有找到 `com.miui.game.allowlist`、`nightDoze` 或 `NIGHT_FREEZE` 實作。夜間凍結證據位於 `miui-services.jar`，而不在這兩份 PowerKeeper APK。

## 使用者提供的實機對照

以下是本次研究採用的使用者實測前提，不是由本次 APK 靜態分析重新測出的結果：

| 條件 | HyperOS 3 | HyperOS 4 |
|---|---|---|
| 自啟動＋智慧策略 | 在 `MILLET_NO_RESTRICT_APP` 已處理的情況下，可穩定收到 FCM | 仍會受到干涉，未能維持相同收訊結果 |
| 自啟動＋無限制策略 | 不需要切換到此策略即可正常收訊 | 使用者確認切換到此策略後才能收到 FCM |
| `aurogon_enable` | 不需要此規則即可收訊 | 使用者另觀察到加入規則後改善；穩定度未確認 |

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

本次兩份 APK 的輸出都可看到上述相同資料流；兩份 APK 都沒有證明「智慧模式」下 FCM 的完整策略。要判定 OS3／OS4 的實際凍結／網路路徑，仍須取得相應版本的 `miui-services.jar`、SecurityCenter 等元件，並與運行時記錄交叉驗證。

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
3. HyperOS 4 的 framework Greezer 存在已被實測的 GMS 凍結路徑，並可能在夜間 Deep Doze 覆蓋一般 no-restrict 豁免。這能解釋 OS4 為何可能需要較強策略或額外豁免。
4. 使用者觀察到 OS4 加入 `aurogon_enable` 後有所改善，表示這項規則可能放行／修復 FCM 廣播控制路徑；目前尚不能確定它直接阻止 Greezer 凍結，或只是在 GMS 恢復後讓 FCM broadcast 更容易到達。
5. 因此「OS3 不需 Aurogon、OS4 加上後改善」與現有研究相容，但還不能證明這是唯一原因，也沒有量化 OS4 的夜間可靠度。

特別修正一項容易混淆的推論：雖然本地兩份 PowerKeeper APK 的 `GmsObserver` 變化很明顯，不能因此直接說「OS4 的 PowerKeeper GmsObserver 就是自啟動＋智慧失效的原因」。上游研究將其同版本裝置的成敗主要定位到 `miui-services.jar` Greezer，故要把責任落到 PowerKeeper 或 Greezer，必須用使用者這兩台／兩版系統的實際 framework 證據驗證。

## 已確認、推論與未驗證事項

| 項目 | 狀態 | 證據界線 |
|---|---|---|
| 兩份 APK 都含 PowerKeeper `aurogon_enable` 雲端 feature | 已確認 | APK 常數、helper、CloudUpdate 寫入路徑 |
| PowerKeeper 有受限的 `GlobalFeatureTable` ContentProvider | 已確認 | Authority、URI 與 Manifest 權限 |
| 兩份 APK 都從私有 UserConfigure 產生 MILLET 清單 | 已確認 | `getNoRestrictApps()` → `Settings.System.putString()` |
| OS4 特定 ROM 可在 MILLET 已含 GMS 時凍結 GMS | 上游特定裝置已實測 | 不可外推為所有 OS4 ROM 或每次漏訊息 |
| OS3 自啟動＋智慧穩定收、OS4 同條件較差 | 使用者實機觀察 | 本報告未重新執行該實驗 |
| OS4 加入 `aurogon_enable` 後改善 | 使用者實機觀察 | 改善幅度與長時間穩定度未知 |
| PowerKeeper 內部 Aurogon feature 與 Android Global key 同步 | 未證實 | 本次 APK 靜態分析未見直接連結 |
| OS4 每次 FCM 失敗都由 nightDoze 造成 | 未證實 | 需事件時間、凍結日誌與 socket 狀態對照 |
| `aurogon_enable` 是透過阻止凍結而改善 FCM | 未證實 | 可能涉及廣播規則，需分層實驗 |

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

這就能解釋一種表面矛盾：GMS 已出現在 `MILLET_NO_RESTRICT_APP`，但夜間仍被凍結。受測 framework 的 `isNoRestrictFreezeable()` 會允許已識別的 messaging packages 保持豁免，卻讓其他 no-restrict App 在夜間條件下重新可凍結。應以裝置上的 package 分類和 framework 版本核對，不能假定所有 GMS 身分／使用者空間都會走相同判定。

## `com.miui.game.allowlist` 的格式、效果及限制

受測 framework 以底線 `_` 連接 package name，並透過 Java `String.split("_")` 解析後做完整 package 比對。它不會 trim token；JSON、逗號、子字串都不是有效替代格式。尾端底線造成的空 token 會在 Java split 行為下被丟棄；package name 本身若包含底線則無法完整表示，是格式先天限制。

`GreezeManagerService.freezeUids()` 的 `preCheckInterest()` 會呼叫 `isGameAllowListApp(uid)`；命中遊戲豁免清單時，該次較低層 freeze 請求會被拒絕。這個豁免沒有夜間條件，所以清單生效期間白天也可能生效。它不會關閉 Deep Doze，也不阻止更高層其他 component 嘗試管理 GMS，更不會自行 thaw 已凍結的 UID。

參考研究中的 controlled probe 很能說明「設定存在」與「實際恢復」是不同階段：寫入 game allowlist 後、執行 thaw 前，GMS 仍 frozen、socket 仍為 0；targeted thaw 後 GMS 已不 frozen，但 socket 仍為 0；發出 reconnect 後數秒 socket 仍未建立；在後續 idle entry 約 12 秒後才觀察到 established FCM socket。研究在隨後兩次 idle entry 也觀察到未凍結與一條 socket，移除豁免則再次看到 OEM freeze 與 socket 消失。這是短時段受控觀察，研究明確沒有據此宣稱整夜可靠或實際測試推播已送達。

同一調查把三個結果分開：清單 membership 防止後續特定 freeze；解凍廣播嘗試恢復已凍結 UID；`GCM_RECONNECT` 要求重連。前一項不會自動完成後兩項。任何 App 發送廣播後，最多只能說已呼叫／派送廣播；如果沒有可讀的 frozen state 或 socket 證據，解凍與 FCM 恢復都應報為未知。

## `aurogon_enable`：兩種同名資料來源及其用途

### Android Settings Global

FCMGuard 本地程式目前讀寫 `Settings.Global["aurogon_enable"]`。在專案程式碼中，`AurogonProtection` 會以目前 App UID 執行 `Settings.Global.getString()`／`putString()`，要求 `WRITE_SECURE_SETTINGS` 已授予；按鈕手動同步和 GuardService 內的週期同步使用相同 App-side 路徑。沒有授權時不執行 Global 寫入。

`AurogonConfig` 把 Aurogon 值視為分號分隔的多個 section；enabled broadcast-control section 形如 `broadcastctrl:true#package/action#package/action`。FCMGuard 以自有 marker `com.rsps1008.fcmguard/__FCMGUARD_AUROGON_AUTOSTART_V1__` 標示自己管理的 section，並嘗試保留其他 section。規則中 package 與 action 以 `/` 分隔；`ALL` 或明確 `com.google.android.c2dm.intent.RECEIVE` 可匹配該 package 的 FCM receive 規則。

本專案目前自動同步會從可讀取的 Xiaomi Autostart AppOps 與可見 FCM receiver/service 掃描結果取交集；只對符合條件的 package 新增規則。不可讀狀態不會擴大新增範圍，對先前已管理但現在狀態未知的項目採保留既有規則方式處理。Engine 在變更前重讀 Settings，若第一次讀與寫前讀回不同，回報偵測到競態並停止寫入；寫入後再次讀取，必須 `putString` 回 true 且讀回等於預期候選值才標為 verified。Settings 沒有 CAS，故這仍無法消除「最後一次讀取和 PUT 之間」的極短競態。

GuardService 的 Aurogon fallback 間隔為 30 分鐘，只在 Aurogon 開關啟用且權限仍存在時執行；這是進程內 Handler，不是精確鬧鐘，也不會喚醒休眠 CPU。若 App process 被系統停止，沒有服務在背景存活的保證。手動同步和週期同步共用同一個 App UID scan/write path。

### PowerKeeper 私有 GlobalFeatureTable

兩份 PowerKeeper APK 中的同名 feature 是 PowerKeeper 內部 CloudData 設定欄位。Provider 的 Manifest 權限是 `signatureOrSystem`，普通第三方 App 沒有理由假設可以呼叫它。PowerKeeper helper 用 `ContentResolver.call()` 查詢 `GlobalFeatureTable`，更新 helper 也透過 provider 寫入；這是 PowerKeeper 自己管理 CloudData feature 的介面。

名稱相同只代表都叫 `aurogon_enable`，不代表 storage、schema、權限或 writer 相同。本次靜態搜尋沒有找到 `getCloudAurogonEnable()` 被其他 PowerKeeper 類別呼叫，也沒有找到它和 Android `Settings.Global` 之間的橋接。若要證明兩者同步，需直接觀察 ROM framework／provider 寫入行為，或從 `miui-services.jar` 找到讀取 `Settings.Global` key 的消費點；只看 PowerKeeper APK 不足。

### Aurogon 與 Greezer 的關係

術語容易混淆：一方面，Aurogon 是 Greezer 冻結程式碼中的類別／allowlist 概念；另一方面，`aurogon_enable` 是 PowerKeeper cloud feature 名稱及 FCMGuard 寫入的 Global Settings key。參考研究對 `com.miui.game.allowlist` 的凍結前檢查有具體 framework 證據；對 `Settings.Global.aurogon_enable` 則只說明 writer/lifecycle，沒有在三份調查中完整記載作者如何發現 key、哪個 framework 元件消費它、或它是否直接影響 Greezer freeze 判斷。

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

`WRITE_SECURE_SETTINGS` 對一般 App 不是一般 runtime permission。此專案的設計是由使用者透過 Shizuku 授權 App 自身的該項權限，授權後後續 `Settings.Global` 呼叫由 FCMGuard App UID 直接執行；不需要每次同步都呼叫 Shizuku。若權限不存在，程式應明確回報並停止寫入，不能把 `canWrite Settings.System` 或普通 Settings 權限當成足夠。

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

這次本機 APK 工作只做靜態解包與搜尋，沒有對手機寫入 Settings、切換電池模式、測試 ADB/Shizuku 權限或發送 FCM 訊息。APK 由 Apktool 3.0.3 解包；使用 SHA-256 留下樣本識別值。已檢查的重點包括：

- Manifest 的 package、SDK 建置資訊、PowerKeeper Provider authority 及權限。
- `GlobalFeatureConfigure`、`GlobalFeatureConfigureHelper`、`PowerKeeperConfigureProvider`。
- `LocalUpdateUtils`、`CloudUpdateHideMode`、`CloudDatabaseHelper` 中的 Aurogon cloud feature 路徑。
- `ActiveStateController`、`UserConfigureHelper` 的 no-restrict rows 到 MILLET Settings 寫入路徑。
- `GmsObserver` 的欄位、方法、PowerKeeper GMS chain 與 Greezer Binder 行為。
- 對 PowerKeeper smali 搜尋 `com.miui.game.allowlist`、`nightDoze`、`NIGHT_FREEZE`；兩份 APK 均未命中。

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

## 來源

- [Xiaomi HyperOS GMS／FCM screen-off disconnection investigation](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)
- [HyperOS 4 nighttime GMS freezing and a non-root workaround](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)
- [Xiaomi MILLET_NO_RESTRICT_APP rewrite investigation](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-millet-no-restrict-app-rewrite-investigation.md)
- 本地 PowerKeeper APK：`E:\Windows\Downloads\com.miui.powerkeeper_A16.apk`
- 本地 PowerKeeper APK：`E:\Windows\Downloads\電量和性能_4.2.00.apk`
