# CPU Policy Guard v1.3.0 · 一加 12 / Android 16

## 本版设计
- **统一调速器**：扫描各 policy 支持的调速器，求交集，在一个下拉框里统一选择，一次应用至全部策略组；只写 `scaling_governor`。
- **频率分组独立**：根据扫描到的 policy 最大硬件频率升序分组；小核、中核、大核各有独立应用按钮；只写对应组的 `scaling_min_freq` 与 `scaling_max_freq`。
- **无开机自动写入**：删除 BootReceiver 注册，XposedEntry 仅兼容入口不做 hook；不生成 apply.sh。操作时自动删除旧版本遗留的脚本，防止旧版本的配置被再次应用。
- **只读设备报告**：应用里点击「复制设备与 CPU 内核检测报告」，得到设备指纹、SoC、内核和全部 policy 的频率/调速器/可用频点以便精确适配。
- **安全限制**：频率必须落在扫描到的硬件范围内，不自动解锁硬件上下限；写入后回读。CPU / vendor power HAL 可能覆盖设置。

## 重要限制
本项目无法从开发环境直接读取你的手机，真正的分组和可用频点必须在手机上生成报告确认。所谓“大中小核”是界面方便称谓：实际 CPU policy 可多于三组，因此相同级别可能有多条 policy。请先用默认值测试，避免直接锁死最低/最高频率。Root 下 sysfs 修改仍有设备不稳定风险；不能保证完全不会重启或无法开机。新版不在重启阶段执行任何 CPU 写入，因此能降低因持久化导致的开机循环风险。

GitHub Actions workflow 已保留不使用有问题的 `setup-android@v3`；上传到仓库根目录即可尝试构建。


## v1.3.1 构建修复

修正 MainActivity.java 频率写入逻辑中 Shell 变量的 Java 字符串双引号转义错误。GitHub Actions 使用 plain 输出以便发现 javac 具体报错。未在 Android SDK 环境下完成实际 APK 编译验证。
