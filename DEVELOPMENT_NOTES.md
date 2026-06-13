# 开发经验笔记

## 项目概述

杂鱼框架 — 基于 Shizuku 的 Android 插件化应用。用户上传 `.jar` 文件来扩展功能，插件通过 Shizuku 获得 ADB 级别权限。

技术栈：Kotlin + Jetpack Compose + Material3 + Shizuku API + DexClassLoader 动态加载。

---

## 一、踩过的坑 & 解决方案

### 1. DexClassLoader 类名猜测 → 彻底废弃

**问题**：最初用类名猜测机制加载插件，尝试 15+ 种命名模式（`com.plugin.xxx`、`xxxPlugin`、`Main` 等），既不可靠又低效。

**解决**：v2.0 彻底删除 `findPluginClass()` 和 `findPluginMainClass()`（共 ~105 行代码），强制所有插件（.jar 和 .dex）都必须包含 `META-INF/plugin.properties` 声明 `mainClass`。不再有任何降级猜测。

**教训**：不要猜，让插件自己声明身份。一刀切比保留降级更干净。

### 2. TestPlugin 没实现 Plugin 接口

**问题**：`tools/TestPlugin.java` 用反射调用 `ShizukuProxy`，但不 `implements Plugin`，导致 `PluginLoader.isAssignableFrom(Plugin)` 检查失败，插件无法被识别。

**解决**：要么实现接口，要么单独写适配器。最终统一要求所有插件必须实现 `Plugin` 接口。

**教训**：示例代码必须和运行时代码保持一致的契约。

### 3. 示例插件没编译 → 只有 .java 无 .dex

**问题**：`tools/ExamplePlugin.java` 正确实现了 `Plugin` 接口，但没有编译好的 `.dex` 可供测试。

**解决**：写编译脚本或手动用 d8 编译。后续应自动化：`javac → d8 → .dex`。

**教训**：交付物要包含编译好的可运行示例。

### 4. 命令注入风险（wrapper 方法字符串拼接）

**问题**：`installApk`、`uninstallApp`、`launchApp`、`getProp`、`putSetting`、`getSetting` 等方法将用户输入直接拼进 shell 命令字符串，即使底层 `execCommand` 用了 `ProcessBuilder` 传参也不能消除风险。

**解决**：每个 wrapper 方法加参数校验 —— 包名正则 (`^[a-zA-Z0-9._-]+$`)、属性名正则、namespace 白名单 (`system/secure/global`)、value 黑名单过滤（`;&|$` 等 + `\n` `\r`）。

**教训**：安全校验放在最外层，不要依赖底层兜底。

### 5. Shizuku 权限声明名称错误

**问题**：文档写 `rikka.shizuku.API_PERMISSION`，实际正确的权限是：
```xml
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
```
两个名称不对等，会直接导致申请权限失败。

**教训**：权限声明必须去依赖源码/文档核实，不能靠口头传递。

### 6. rish 文件来源与 ABI 兼容

**问题**：`rish` 和 `rish_shizuku.dex` 是从 Shizuku 应用中提取的。不同 Android 版本的 rish 二进制可能不兼容，arm64/x86 架构也不同。

**解决**：assets 中内置 ARM64 版本的 rish，由 `ShizukuProxy` 首次 `execCommand` 时触发懒加载自动复制到私有目录。安装时需确认目标设备是 ARM64。

**教训**：native binary 要在项目里注明来源和适用范围。

### 7. PluginManager 生命周期与 Composable 重组

**问题**：`PluginListScreen` 用 `remember { PluginManager(context) }` 创建单例，但 `PluginManager` 内部持有了回调（`onPluginLoaded`、`onError`），在 `LaunchedEffect` 里赋值。Compose 重组时可能重复赋值导致回调链泄漏。

**解决**：回调在 `remember` 闭包内直接赋值，不再通过 `LaunchedEffect` 间接设置。`PluginManager` 提供 `destroy()` 方法清理 `poolScope` 协程。Compose 回调全部包裹 `scope.launch(Dispatchers.Main)` 确保线程安全。

**教训**：长生命周期对象的回调应在构造阶段绑定，清理阶段显式释放。

### 8. 插件执行结果在主线程等待 → 异步化

**问题**：`PluginCard` 点击执行插件时同步调用 `pluginManager.executePlugin()`，如果插件执行耗时会卡 UI。

**解决**：`scope.launch(Dispatchers.IO)` 包裹执行 + 按钮 loading 状态 + `executingPlugin` 防重标志。

**教训**：耗时操作需要显式 `withContext(Dispatchers.IO)`，UI 需要即时反馈。

### 9. stdout/stderr 顺序读取导致死锁

**问题**：`execCommand` 先读完 `stdout` 再读 `stderr`，若 stderr 写满缓冲区（如 `dumpsys`），进程阻塞等待 stderr 被消费，Kotlin 端在等 stdout 读完，形成死锁。

**解决**：改用 `CompletableFuture` 双线程并发读取 stdout 和 stderr。

**教训**：子进程 IO 流必须并发消费，不能顺序等待。

### 10. ShizukuProxy 构造里做 IO 阻塞

**问题**：`init { setupRishFiles() }` 在构造阶段同步复制 assets 文件到私有目录，延迟高。

**解决**：改为 `by lazy { setupRishFiles() }`，首次 `execCommand` 时触发，并在 `rishFile.exists()` 检查失败时自动调用 `rishReady` 触发初始化。

**教训**：构造器应轻量，IO 操作延迟到首次使用时。

### 11. 插件名与文件名字符不匹配导致卸载残留

**问题**：`uninstallPlugin` 用 `plugin.getName()` 拼文件名（`$pluginName.jar`），但用户上传的文件名可能与 `getName()` 返回值完全不同。导致文件删不掉、残留。

**解决**：在 `PluginLoader` 中维护 `pluginSourceFiles` 映射表（插件名 → 源文件名），卸载时用映射查找并删除确切文件。

**教训**：资源标识与资源路径不能混用，需要显式映射。

### 12. Shizuku 权限结果无人监听

**问题**：`Shizuku.requestPermission()` 调用后没有注册 `OnRequestPermissionResultListener`，用户授权/拒绝无回调。

**解决**：`MainActivity.onCreate` 中 `Shizuku.addRequestPermissionResultListener`，`onDestroy` 中 remove。

**教训**：Shizuku 权限流程必须包含 request → listener → destroy 完整生命周期。

### 13. 无用 IO（rish 文件内容预览）

**问题**：每次 `execCommand` 都读取 rish 文件前 200 字符做日志预览，纯粹浪费。

**解决**：删除该日志行。

**教训**：调试日志不该成为生产代码的一部分。

### 14. ContentResolver query 无 projection

**问题**：`getFileNameFromUri` 和 `AddPluginDialog` 中 `ContentResolver.query` 不传 projection（传 ``），取回全部列，浪费 IO。

**解决**：改为 `arrayOf(OpenableColumns.DISPLAY_NAME)` 精确查询。

**教训**：数据库查询必须限定 projection。

### 15. LazyColumn key 冲突导致闪退

**问题**：`onPluginLoaded` 回调往 `plugins` 列表追加插件，与 `LaunchedEffect` 中的全量赋值同时触发，同一插件被加入两次，LazyColumn 的 `key = { it.getName() }` 冲突。

**解决**：`onPluginLoaded` 中追加后加 `.distinctBy { it.getName() }` 去重。

**教训**：Compose 状态修改需要保证幂等性，LazyColumn key 必须全局唯一。

---

## 二、v2.0 架构升级

### 新增接口

| 接口 | 用途 |
|------|------|
| `BackgroundPlugin : Plugin` | 后台长期运行插件，`runInBackground(proxy, scope, dispatcher)` 入口 |
| `SubPluginDispatcher` | 子插件调度器，`call(subPluginId, args)` 让常驻插件调用执行子插件 |

### 实例池 (Plugin Pool)

- 用 `Mutex` 保护的并发安全池，最多 5 个 clone
- 池满时轮询等待（100ms 间隔）
- 空闲 clone 30 秒后被自动回收
- 多次引用同一插件时自动创建临时实例，用完释放

### 子插件声明

在 `META-INF/plugin.properties` 中扩展：
```properties
uid=com.example.myplugin        # 插件唯一标识（可选，用于升级检测）
version=2.0.0
subPlugins=monitor,kill,skip     # 逗号分隔的子插件 ID 列表
```

### ClassLoader 隔离

`DexClassLoader` 的 parent 从 `context.classLoader` 改为 `Plugin::class.java.classLoader`，防止多个插件的同名类冲突。插件只能访问 `Plugin` 和 `ShizukuProxy` 接口。

### DexClassLoader 目录隔离

每次 `loadPlugin` 创建独立 optimizedDir：`cacheDir/optimized_plugins/${文件名}_${时间戳}`，防止重复加载冲突。

---

## 三、项目注意事项

### 安全
- **永远不要将用户输入拼入 shell 命令**。在 `execCommand` 上层对参数做格式校验（包名正则、文件名防穿越）。
- **只加载可信插件**。DexClassLoader 可以执行任意代码，插件相当于拥有应用全部权限 + Shizuku ADB 权限。
- **禁止应用申请 INTERNET 权限**（当前未声明），减少数据外泄风险。
- **putSetting 值过滤**：`;` `&` `|` `$` `` ` `` `'` `"` `\n` `\r` 均为非法字符。

### 文件结构
- 插件目录：`context.filesDir/plugins/`（内部存储，不需要额外权限）
- rish 目录：`context.filesDir/rish/`（`ShizukuProxy` 首次 execCommand 时懒加载初始化）
- Plugin 接口定义在 `com.java.myapplication` 包中，插件必须 import 此包

### 版本兼容
| 组件 | 最低版本 | 备注 |
|------|---------|------|
| minSdk | 24 (Android 7.0) | Shizuku 至少需要 API 24 |
| targetSdk | 35 | 保持最新 |
| JDK | 17 | AGP 9.x 要求 |
| Kotlin | 2.3.10 | 与 Compose BOM 2026.01.01 配套 |

### 构建注意事项
1. ARM64 环境下需要替换 aapt2（项目已内置 `tools/aapt2` 和 `setup_android_env.sh`）
2. Gradle Version Catalog 管理依赖版本，修改依赖去 `gradle/libs.versions.toml`
3. Debug 构建不启用混淆，Release 需要配置 `proguard-rules.pro`（注意：Shizuku 和插件接口类不能被混淆）

### 插件开发规范
1. 必须实现 `Plugin` 接口（`getName`, `getDescription`, `getVersion`, `execute`, `needsShizuku`）
2. 必须包含 `META-INF/plugin.properties` 声明 `mainClass`（不再支持类名猜测）
3. 可选声明 `uid`（用于升级检测）、`version`、`subPlugins`（逗号分隔子插件 ID）
4. 插件只能通过 `ShizukuProxy` 执行高权限操作，不能直接访问 Android Framework API
5. 插件返回的字符串会直接展示在 UI 中，注意格式化
6. `.dex` 文件同样需要 `plugin.properties`，否则无法加载

### 已知局限
- 插件无法使用 Android 资源系统（R.layout、R.string 等），因为资源 ID 在宿主 APK 中
- 每个插件有独立 ClassLoader，同名类不再冲突
- 卸载插件时通过文件名映射精确删除源文件
- rish 仅支持 ARM64，x86 设备需要替换 rish 文件
- 插件被常驻调用时会自动创建临时实例（最多 5 个 clone），30 秒空闲后自动回收

### 并发安全
- 实例池用 `Mutex` 保护，`acquirePluginInstance`、`releasePluginInstance`、`cleanupExpiredClones` 均加锁
- 子插件调度器用 `Mutex` 排队，同一子插件同时只被一个调用方执行
- Compose 回调全都用 `scope.launch(Dispatchers.Main)` 保证主线程安全

---

## 四、文件职责速查

| 文件 | 职责 | 改动频率 |
|------|------|---------|
| Plugin.kt | Plugin / BackgroundPlugin / SubPluginDispatcher 接口定义 | 低 |
| PluginMeta.kt | 插件元数据（uid, version, subPlugins 等） | 低 |
| ShizukuProxy.kt | 封装 Shizuku 高权限命令，rish 懒加载，参数校验 | 中 |
| PluginLoader.kt | DexClassLoader 加载、plugin.properties 解析、ClassLoader 隔离 | 低 |
| PluginManager.kt | 安装/卸载/执行、实例池、子插件调度、BackgroundPlugin 管理 | 中 |
| PluginListScreen.kt | Compose UI：Tab 切换、列表、置顶、删除确认、执行结果 | 高 |
| MainActivity.kt | Shizuku 权限生命周期 | 低 |
| AndroidManifest.xml | 权限、Activity、Provider 声明 | 低 |
| libs.versions.toml | 依赖版本 | 中 |
| assets/rish + rish_shizuku.dex | Shizuku 运行时文件 | 极低 |

---

## 五、调试命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
cp app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/ && pm install -r /data/local/tmp/app-debug.apk

# 查看日志
logcat -s ShizukuProxy:V PluginLoader:V PluginManager:V ClipStack:V

# 手动推送插件文件
cp MyPlugin.jar /data/local/tmp/ && run-as com.java.myapplication cp /data/local/tmp/MyPlugin.jar files/plugins/
```
