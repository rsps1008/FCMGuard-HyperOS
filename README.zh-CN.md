<p align="center">
  <a href="README.md">繁體中文</a> · <a href="README.zh-CN.md"><strong>简体中文</strong></a>
</p>

<p align="center">
  <img src="docs/icon.svg" width="180" alt="FCM Guard 图标" />
</p>

# FCM Guard for HyperOS

**为中国版 HyperOS 设计的 FCM 后台防护工具。** 通过保留小米系统的 GMS 免限制项目，并管理部分 Aurogon 规则，降低 Google Play 服务后台冻结造成推送延迟或中断的机会。

[下载最新版本](https://github.com/rsps1008/FCMGuard-HyperOS/releases/latest) · [查看研究报告](docs/powerkeeper-os3-os4-fcm-investigation.md)

> 本工具不能保证每个 App 的通知都即时送达。推送还会受到接收 App 自身的自启动、电池策略、网络以及 HyperOS 后台政策影响。

## 项目方向

FCM Guard 是独立的 Android App，重点是使用普通 App 可用的系统设置权限，维护 GMS 的重要后台设置，并提供可选的 HyperOS 4 夜间防护及 Aurogon 管理功能。核心 GMS 白名单修复不依赖 Root、常驻 ADB 或 Shizuku。

本项目采用保守的维护方式：保留设置中已有的其他套件、不在状态正常时重复写入、将各项防护分别开关，并在可行时读回设置确认结果。小米私有设置和广播属于厂商接口，功能会随 ROM 版本而异。

## 功能

- **GMS 免限制保护**：检查 `Settings.System.MILLET_NO_RESTRICT_APP`，只在缺少 `com.google.android.gms` 时加入，并保留原有清单项目。
- **自动保护**：监听目标设置变化，并提供 30 分钟的进程内检查。服务停止或手机重启后能否恢复，仍取决于 Android／HyperOS 调度与用户设置。
- **Aurogon FCM 防护**：选择性管理 `Settings.Global.aurogon_enable` 中的 FCM 广播规则，扫描符合条件的 FCM App，并保留非本功能管理的已有规则。需要另外取得 `WRITE_SECURE_SETTINGS`；自动授权设置会在 App 内清楚引导。
- **Aurogon 闹钟防护**：管理 `Settings.System.cloud_aurogon_alarm_allow_list`。同步时加入 GMS、符合条件的 FCM App，并保留 ROM 内置的三个套件及其他已有项目；可还原为仅含内置项目的默认清单。
- **HyperOS 4 夜间防护（实验性）**：管理 `com.miui.game.allowlist` 中与 GMS 有关的项目，降低特定中国版 HyperOS 4 夜间冻结路径的影响机会。此设置可能全天有效并增加后台活动；停用时会根据记录的项目所有权清理。
- **FCM App 助手**：根据 FCM/GCM manifest 接收组件寻找可能使用 FCM 的 App，并在 ROM 允许时只读显示 Xiaomi 自启动状态。无法确认时会显示未知，不会猜测。
- **可选自启动控制**：用户明确授权后，可通过 Shizuku 修改个别 App 的 Xiaomi 自启动项目；一般扫描仍为只读。
- **轻量监控**：设置变更监听采用防抖处理；周期检查在 App 进程内执行，不使用重复精确闹钟或 WakeLock。

## 开始使用

1. 从 [GitHub Releases](https://github.com/rsps1008/FCMGuard-HyperOS/releases/latest) 下载并安装 APK。
2. 在系统设置中允许 FCM Guard“修改系统设置”，再按“立即修复”确认 GMS 已在免限制清单中。
3. 开启“自动保护”。若希望服务在后台较稳定地运行，请根据 HyperOS 指引为 FCM Guard 开启自启动，并选择合适的电池后台策略。
4. 只启用需要的 Aurogon 或夜间防护功能。Aurogon FCM 规则需先完成卡片中的权限设置；闹钟清单与夜间防护需要“修改系统设置”权限。
5. 若只有单一 App 收讯延迟，请另外检查该 App 在 HyperOS 中的自启动、电池限制和通知设置。

FCM Guard 的应用 ID 为 `com.rsps1008.fcmguard`。1.6.2 版的 versionCode 为 32。Android 会把不同应用 ID 视为不同 App，因此旧版权限和 App 内设置不会自动迁移。若要移除旧版，请先关闭旧版夜间防护并完成清理程序。

## 各项设置的作用

| 设置 | 管理内容 | 权限与范围 |
|---|---|---|
| `MILLET_NO_RESTRICT_APP` | 将 GMS 保留在小米免限制清单 | 用户授予“修改系统设置”；只维护 GMS 项目并保留其他项目 |
| `aurogon_enable` | Aurogon FCM 广播规则 | 需要 `WRITE_SECURE_SETTINGS`；只更新本功能管理的规则 |
| `cloud_aurogon_alarm_allow_list` | Aurogon 冻结期间暂存闹钟的允许清单 | 用户授予“修改系统设置”；该清单本身不会阻止 App 冻结 |
| `com.miui.game.allowlist` | 特定 HyperOS 4 夜间 GMS 防冻结路径 | 用户授予“修改系统设置”；实验性、限 ROM |

“闹钟防护”只影响 Aurogon 是否在 App 冻结时暂存其闹钟，并不代表防止冻结，也不会绕过 WakePath。`aurogon_enable` 广播规则会影响相应 FCM 广播能否按小米规则发送给 App；这与 GMS 的 `MILLET_NO_RESTRICT_APP` 是不同设置。

## HyperOS 3 与 HyperOS 4 研究

本项目附有根据两份用户提供的 `miui-services.jar`、PowerKeeper 相关分析及设备观察整理的[研究报告](docs/powerkeeper-os3-os4-fcm-investigation.md)。目前的分析显示，OS3 与 OS4 的后台限制路径并不完全相同；部分 HyperOS 4 版本另有夜间冻结 GMS 的程序路径。静态代码只能说明 ROM 中存在相关机制，不能单独证明特定手机某次漏消息就是该路径所致。

报告也整理用户实测条件、`miui.os.Build` 区域判定线索、Aurogon 设置差异及证据限制。ROM 样本位于 [`docs/rom-samples/miui-services/`](docs/rom-samples/miui-services/)。结果只适用于所分析的版本，其他中国版／国际版 ROM 可能不同。

### 上游研究致谢

感谢 [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) 公开其 HyperOS GMS／FCM／Greezer 实机调查与系统代码分析。该项目的研究记录了 GMS 被 Greezer 冻结的路径、`MILLET_NO_RESTRICT_APP` 的作用边界，以及 HyperOS 4 夜间冻结的相关证据，为本项目进一步比较 ROM 行为提供重要参考。

FCM Guard 是另一个独立实现，功能范围、权限策略与代码均自行维护；本项目引用其公开研究作为技术参考，没有把对方的代码作为本项目代码。请同时阅读[上游调查文件](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)与[HyperOS 4 夜间调查](https://github.com/dingwen07/hyperos-fcm-fix/blob/main/docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)。

## 权限与隐私

核心白名单修复、闹钟允许清单与夜间防护使用用户授予的“修改系统设置”能力。Aurogon FCM 规则需要额外 `WRITE_SECURE_SETTINGS`。自启动修改是可选功能，需要明确授权 Shizuku；App 不会自行开启 USB 或无线调试。

FCM Guard 不使用 Root、无障碍、VPN、悬浮窗、设备管理员、账号读取或网络流量抓取，也不要求 `QUERY_ALL_PACKAGES`。App 扫描使用受限的套件可见性查询。

## 支持与限制

- 需要 Android 6.0（API 23）或更新版本，以及已安装且可正常使用的 Google Play 服务。
- 主要针对 Xiaomi／Redmi／POCO 中国版 HyperOS；不同机型、系统版本及 PowerKeeper 版本可能有不同结果。
- `MILLET_NO_RESTRICT_APP`、Aurogon 规则、闹钟清单及夜间策略均为小米私有接口，系统更新可能改变其格式或行为。
- 自动修复依赖 FCM Guard 进程能够运行。省电策略、强行停止、厂商后台限制或系统调度都可能中断服务。
- 写入成功或读回一致只代表设置值符合预期，不等于已验证系统解除冻结、FCM socket 持续连接，或接收 App 已显示通知。
- 夜间防护仍属实验性；需以目标手机的实际 ROM、长时间熄屏与推送测试验证耗电及效果。

## 构建

使用 Android Studio 或在 Windows 环境中使用 JDK 21 构建。项目保留 `targetSdk 22`，以兼容小米私有设置的用户授权路径；构建时出现 `minSdk 23` 高于 `targetSdk 22` 的警告是当前设置的结果。

```powershell
python tools/check_layout_profiles.py
.\gradlew.bat :app:assembleDebug
```

## 许可

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
