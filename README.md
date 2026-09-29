# plugin-framework

基于 Shizuku 的 Android 插件框架。把 `.jar` 插件包导入应用即可扩展功能，插件通过 Shizuku 获得 ADB 级权限，无需 Root。

- 当前版本：v2.1
- 插件开发示例与教程：[plugin-framework-examples](https://github.com/3975380064-maker/plugin-framework-examples)

## 特性

- 热加载：选择 `.jar` 文件即可安装，无需重启应用
- ADB 级权限：通过 Shizuku + rish 执行高权限 shell 命令
- 后台常驻：`BackgroundPlugin` 接口支持长期运行任务，并可调度子插件
- 实例池：并发调用同一插件时创建临时实例，空闲 30 秒后回收
- ClassLoader 隔离：每个插件独立 ClassLoader，同名类不冲突
- 命令注入防护：`ShizukuProxy` 的所有 shell 入口都做参数白名单校验

## 安装

1. 安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku)
2. 下载 [v2.1 APK](https://github.com/3975380064-maker/plugin-framework/releases/latest)
3. 在 Shizuku 应用中授权本应用
4. 打开应用，点右下角 `+`，选择插件 `.jar`

仅支持 ARM64 设备（rish 二进制为 ARM64 编译）。

## 使用

界面分两个 Tab：

- **手动执行**：列出全部已加载插件，点“执行”运行一次，结果以弹窗展示
- **长期运行**：列出实现了 `BackgroundPlugin` 的插件，可启动 / 停止

支持置顶常用插件、卸载插件。

## 构建

```bash
chmod +x ./setup_android_env.sh
./setup_android_env.sh      # 准备 ARM64 aapt2 与 Gradle 环境
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

| 组件 | 版本 |
|------|------|
| minSdk | 24 (Android 7.0) |
| targetSdk | 35 |
| JDK | 17 |
| Kotlin | 2.3.10 |
| Compose BOM | 2026.01.01 |
| Shizuku API | 13.1.5 |

## 插件开发

插件包是一个 `.jar`，内含：

1. 一个实现 `Plugin` 接口的类
2. `META-INF/plugin.properties`，用 `mainClass` 声明入口类

```java
package com.example;

import com.java.myapplication.Plugin;
import com.java.myapplication.ShizukuProxy;
import java.util.Map;

public class MyPlugin implements Plugin {

    @Override
    public String getName() { return "MyPlugin"; }

    @Override
    public String getDescription() { return "示例插件"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String execute(ShizukuProxy proxy, Map<String, ?> args) {
        return proxy.getProp("ro.product.model");
    }
}
```

注意 `execute` 的参数类型必须是 `Map<String, ?>`。宿主侧对应 Kotlin 的 `Map<String, Any>?`，编译后是 `Map<String, ? extends Object>`；写成 `Map<String, Object>` 会报 name clash 编译错误。

打包：

```bash
mkdir -p META-INF
echo "mainClass=com.example.MyPlugin" > META-INF/plugin.properties
jar cf MyPlugin.jar com/ META-INF/
```

`META-INF/plugin.properties` 支持以下键：

| 键 | 必填 | 说明 |
|----|------|------|
| `mainClass` | 是 | 插件入口类全名 |
| `uid` | 否 | 插件唯一标识 |
| `version` | 否 | 版本号，缺省取 `Plugin.getVersion()` |
| `description` | 否 | 描述，缺省取 `Plugin.getDescription()` |
| `subPlugins` | 否 | 子插件 ID 列表，逗号分隔 |

编译插件时需要宿主的接口类作为 classpath（Android Studio 里可作为 compileOnly 依赖引入）。

## 插件 API

### Plugin

```kotlin
interface Plugin {
    fun getName(): String
    fun getDescription(): String
    fun getVersion(): String
    fun execute(proxy: ShizukuProxy, args: Map<String, Any>? = null): String
    fun needsShizuku(): Boolean = true
}
```

`execute` 的返回值会直接显示在结果弹窗中。

### 后台常驻：BackgroundPlugin

```kotlin
interface BackgroundPlugin : Plugin {
    suspend fun runInBackground(
        proxy: ShizukuProxy,
        scope: CoroutineScope,
        dispatcher: SubPluginDispatcher
    )
}
```

`runInBackground` 是 Kotlin 的 `suspend fun`，因此**后台常驻插件必须用 Kotlin 编写**。Java 编译后的签名会多出一个 `Continuation<? super Unit>` 参数，无法用普通方法实现。

### 子插件调度：SubPluginDispatcher

```kotlin
interface SubPluginDispatcher {
    suspend fun call(subPluginId: String, args: Map<String, String> = emptyMap()): String
}
```

常驻插件通过它在 `plugin.properties` 声明的 `subPlugins` 中调用子插件，同一子插件 ID 串行执行。

### ShizukuProxy

| 方法 | 说明 |
|------|------|
| `execCommand(cmd)` | 执行任意 shell 命令 |
| `getProp(prop)` | 读取系统属性 |
| `installApk(path)` | 静默安装 APK |
| `uninstallApp(packageName)` | 卸载应用 |
| `launchApp(packageName, activityName?)` | 启动应用 |
| `getSetting(namespace, key)` | 读取系统设置 |
| `putSetting(namespace, key, value)` | 修改系统设置 |
| `isShizukuAvailable()` | Shizuku 服务是否可用 |
| `checkPermission()` | 是否已授权 |

`namespace` 仅接受 `system` / `secure` / `global`。所有参数都会先做格式校验，非法输入直接返回 `Error: ...` 而不会执行命令。

## 架构

```
PluginListScreen (Compose UI)
├─ 手动执行 Tab
└─ 长期运行 Tab

PluginManager            对外入口，负责编排
├─ PluginLoader          扫描目录、解析 plugin.properties、DexClassLoader 加载
├─ PluginInstaller       插件文件的安装与卸载
├─ PluginInstancePool    并发实例池，空闲实例超时回收
├─ SubPluginDispatcherImpl  子插件调用，同一 ID 串行
├─ BackgroundPluginHost  后台常驻插件生命周期
└─ PluginUriResolver     URI 展示名与安全文件名解析

ShizukuProxy
├─ rish 懒加载初始化
├─ execCommand   并发读取 stdout / stderr，避免管道阻塞
└─ 参数白名单校验后执行 pm / am / settings / getprop
```

依赖方向单向：UI → PluginManager → 各子系统 → PluginLoader / ShizukuProxy。

插件目录：`context.filesDir/plugins/`（应用内部存储，无需额外权限）。

## 已知限制

- 仅支持 ARM64 设备
- 仅支持 `.jar`。`.dex` 无法携带 `META-INF/plugin.properties`，从 v2.1 起不再支持
- 插件无法使用宿主 Android 资源系统（`R.layout`、`R.string` 等）
- 后台常驻插件必须用 Kotlin 编写
- 插件以宿主进程身份运行，可执行任意代码并持有 Shizuku 权限，只应加载可信来源的插件

## 排错

插件没出现在列表里，按顺序检查：

1. 文件是否为 `.jar` 且扩展名正确
2. jar 内是否包含 `META-INF/plugin.properties`
3. `mainClass` 是否与实际类名一致
4. 该类是否实现了 `Plugin` 接口
5. 编译插件时是否用了 `Map<String, ?>` 签名

命令执行返回 `Error: 未获取Shizuku权限，请先授权` 时，打开 Shizuku 应用重新授权。

查看日志：

```bash
logcat -s ShizukuProxy:V PluginLoader:V PluginManager:V BackgroundPluginHost:V
```

## License

Apache License 2.0
