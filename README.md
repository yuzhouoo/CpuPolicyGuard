# CPU 策略控制：一加 12 / Android 16 / ColorOS 16

**版本 1.1.0，提供 GitHub Actions 构建入口。** 一加 12 使用 Snapdragon 8 Gen 3，但 CPU policy 编号、频率表、调速器取决于当前设备内核。应用从 `/sys/devices/system/cpu/cpufreq/policy*` 动态检测，不硬编码频点。

## 在 GitHub Actions 上编译 APK（无需本机 Android Studio）

1. 在 GitHub 新建仓库，建议名称 `CpuPolicyGuard`，将本压缩包中 `CpuPolicyGuard-LSPosed/` **内的所有文件与隐藏的 `.github/` 目录**上传到仓库根目录。不要再套一层文件夹。
2. 仓库 `Actions` 页签，允许 Actions 工作流，选择 `Build Android APK` → `Run workflow` → `Run workflow`。也可推送至 `main`/`master` 自动触发。
3. 构建成功后，点击当次 workflow 的 `Artifacts` → `CpuPolicyGuard-OnePlus12-Debug-APK` 下载 ZIP，解压获得 `app-debug.apk`。
4. 下载的是 debug 签名 APK，只适合测试；不同构建签名变化会影响升级安装。若正式发布请配置独立签名密钥。

工作流使用 JDK 17 + Gradle 8.10.2 + Android SDK 35 + AGP 8.6.1。Android 16 手机**不要求**应用 compileSdk 恰好为 36；但低 targetSdk 可能受系统安装限制。`targetSdk 28` 是旧模块兼容取舍，并非保证 ColorOS 16 一定允许安装，遇到旧目标 SDK 阻拦需使用官方允许的安装方式或调整 targetSdk 测试。

## 安装和运行

- 需 Root（Magisk/APatch/KernelSU 等）及兼容的 LSPosed；授予 **本应用** Root 权限。
- 启用 LSPosed 模块时，作用域**仅选 SystemUI**，不要选择 Android 系统框架。钩子为开机重启时的**可选**补偿；即使不启用 LSPosed，手动修改功能也可尝试使用。
- 打开 APP → `扫描 CPU 策略组` → 选择 policy → governor 与 min/max（单位 kHz）→ `保存当前策略并立即应用` → `查看实际生效状态`。
- 调速器可能只有 `walt` / `schedutil` 等有限选项；如果内核未提供其他选项，应用无法创造新的 governor。
- 性能服务、温控及系统电源 HAL 可能覆盖频率；该模块不能绕过硬件/温控保护。
- 修改频率有卡顿、耗电、稳定性风险，首次先做保守幅度尝试，并保留 ADB / 恢复环境。

## 已知限制

- 没有真实的一加 12 / ColorOS 16 设备供验证，兼容性待实机测试。
- 开机广播在 ColorOS 限制后台运行时可能不执行。**不要为提高成功率给 SystemUI 授予 Root 权限**；如果不开启该作用域，仍可手动使用 APP。
- 恢复的是首次扫描值，而非工厂默认。清除设置后重启才能最大程度让厂商策略重新接管。
- 无持续写入与 GPU 调整功能。此版本仅用于个人测试。

---

# CPU 策略控制（LSPosed + Root）

一个新的独立 Android LSPosed 模块，控制每个 CPU cpufreq policy 的 `scaling_governor`、`scaling_min_freq` 和 `scaling_max_freq`。

## 编译

1. 使用 Android Studio（JDK 17）打开项目，安装 Android SDK 35。
2. Gradle Sync，执行 `:app:assembleDebug`，生成 `app/build/outputs/apk/debug/app-debug.apk`。
3. 安装 APK，首次启动允许 Root 管理器授权。
4. 在 LSPosed 中启用模块，**仅勾选 SystemUI（com.android.systemui）作用域**，重启 SystemUI 或设备。
5. 进入应用，扫描策略，选择 governor/上下限，点击“保存当前策略并立即应用”。

## 功能与实现

- 自动发现 `/sys/devices/system/cpu/cpufreq/policy*`；不假定 CPU 大小核分组。
- 仅提供内核公开的 governor；频率界限使用 cpuinfo_min_freq/max_freq 验证（可用频点列表有则展示）。
- Root 写入 `/data/adb/cpuguard/apply.sh`，应用当前已保存各策略。启动接收器在开机广播时尝试应用；LSPosed 模块挂钩 SystemUI `Application.attach()` 并另起线程触发脚本，是补充触发途径。
- “恢复”会删除开机脚本，并尝试还原首次扫描记录的 governor 和频率上下限。
- 读取后的数值仅反映**当时**的 sysfs 状态，电源 HAL / thermal / vendor 服务可能立即覆盖，不能绕过温控。

## 风险与限制

- **需要 Root**，LSPosed 本身不赋予 sysfs 写权限。部分设备 SELinux / 内核不允许 Root 修改这些节点。
- 第一次扫描不一定是出厂默认值；恢复功能是“首次扫描值”，**不是内核出厂值**。若需恢复系统默认控制，删除脚本后重启通常更可靠。
- 太低的最高频率可能导致 UI 卡顿、开机变慢；过高的最低频率增加耗电发热。不要禁用温控。
- 若开机时 Root 管理器不允许后台授权，开机应用可能失败；Root 管理器需允许本 App 和（启用 LSPosed 补偿时）SystemUI 的授权，后者可能存在额外安全风险。可不启用 LSPosed 作用域仅用 App 手动应用。
- 该项目使用兼容性更高的旧版 Xposed API 82；不同 LSPosed 分支支持情况可能不同。
- 当前版本不提供 GPU/核心离线控制、周期强制覆盖和调速器底层参数编辑。
