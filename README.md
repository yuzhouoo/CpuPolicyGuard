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
