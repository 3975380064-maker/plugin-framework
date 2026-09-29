# plugin-framework

基于 Shizuku 的 Android 插件框架。导入 `.jar` 插件包即可扩展功能，插件通过 Shizuku 获得 ADB 级权限，无需 Root。

## 特性

- 热加载：选择 `.jar` 文件即可安装，无需重启应用
- ADB 级权限：通过 Shizuku + rish 执行高权限 shell 命令
- 后台常驻：`BackgroundPlugin` 接口支持长期运行任务，可调度子插件
- 实例池：并发调用时创建临时实例，空闲后回收
- ClassLoader 隔离：每个插件独立 ClassLoader，同名类不冲突
- 命令注入防护：所有 shell 调用入口做参数白名单校验

## 安装

1. 安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku)
2. 下载本应用的 [APK](https://github.com/3975380064-maker/plugin-framework/releases/latest)
3. 在 Shizuku 中授权本应用
4. 通过应用内 `+` 按钮导入插件 `.jar`

## 架构

```
PluginListScreen (Compose UI)
├─ 手动执行 Tab   列出所有插件，点击执行
└─ 长期运行 Tab   列出 BackgroundPlugin，启动 / 停止

PluginManager（对外入口，负责编排）
├─ PluginLoader          扫描目录、解析 plugin.properties、DexClassLoader 加载
├─ PluginInstaller       插件文件的安装与卸载
├─ PluginInstancePool    并发实例池，空闲实例超时回收
├─ SubPluginDispatcherImpl  子插件调用，同一 ID 串行
└─ BackgroundPluginHost  后台常驻插件生命周期

ShizukuProxy
├─ rish 懒加载初始化
├─ execCommand   并发读取 stdout / stderr，避免管道阻塞
└─ 参数白名单校验后执行 pm / am / settings / getprop
```

依赖方向单向：UI → PluginManager → 各子系统 → ShizukuProxy。

## 版本兼容

| 组件 | 版本 |
|------|------|
| minSdk | 24 (Android 7.0) |
| targetSdk | 35 |
| JDK | 17 |
| Kotlin | 2.3.10 |
| Compose BOM | 2026.01.01 |
| Shizuku API | 13.1.5 |

## 构建

```bash
chmod +x ./setup_android_env.sh
./setup_android_env.sh        # ARM64 aapt2 + Gradle 环境
./gradlew assembleDebug
```

## 插件开发

插件是一个实现 `Plugin` 接口的 Java/Kotlin 类，打包为 `.jar`（必须包含 `META-INF/plugin.properties` 声明 `mainClass`）后导入框架即可运行。支持手动执行、后台常驻、子插件调度三种模式。

完整教程与示例代码见 [plugin-framework-examples](https://github.com/3975380064-maker/plugin-framework-examples)。

## 已知限制

- 仅支持 ARM64：rish 二进制为 ARM64 编译
- 仅支持 `.jar`：`.dex` 无法携带 `META-INF/plugin.properties`，因此不支持
- 插件无法使用 Android 资源系统（R.layout、R.string 等）
- 插件可执行任意代码且持有本应用与 Shizuku 权限，只应加载可信来源的插件

## 日志调试

```bash
logcat -s ShizukuProxy:V PluginLoader:V PluginManager:V BackgroundPluginHost:V
```

## License

Apache License 2.0