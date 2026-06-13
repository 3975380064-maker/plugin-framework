# Plugin Framework — 基于 Shizuku 的 Android 通用插件框架

通过上传 `.jar` 插件包来扩展功能，插件通过 Shizuku 获得 ADB 级别权限，无需 Root。

---

## 🚀 特性

- **热加载插件** — 上传 jar 包即可用，无需重启应用
- **ADB 级权限** — 通过 Shizuku + rish 执行高权限 Shell 命令
- **后台常驻** — 支持 `BackgroundPlugin` 长期运行，配合子插件调度实现自动化
- **实例池** — 并发调用时自动创建临时实例，空闲后自动回收
- **ClassLoader 隔离** — 不同插件的同名类不冲突
- **安全校验** — 所有 Shell 调用入口加参数格式校验，防止命令注入

---

## 📦 快速开始

### 安装

1. 安装 [Shizuku](https://github.com/RikkaApps/Shizuku) 并启动
2. 安装本应用
3. 在 Shizuku 中授权本应用
4. 通过应用内 `+` 按钮导入插件 jar 包

### 编写插件

插件是一个实现 `Plugin` 接口的 Java/Kotlin 类，打包为 jar。必须包含 `META-INF/plugin.properties`：

```properties
mainClass=com.example.MyPlugin
```

```java
package com.example;

import com.java.myapplication.Plugin;
import com.java.myapplication.ShizukuProxy;
import java.util.Map;

public class MyPlugin implements Plugin {
    public String getName() { return "我的插件"; }
    public String getDescription() { return "示例插件"; }
    public String getVersion() { return "1.0"; }
    public boolean needsShizuku() { return true; }
    
    public String execute(ShizukuProxy proxy, Map<String, Object> args) {
        return proxy.execCommand("echo Hello World!");
    }
}
```

编译打包：
```bash
javac -cp android.jar MyPlugin.java
jar cf MyPlugin.jar com/ META-INF/
```

### 后台插件

实现 `BackgroundPlugin` 接口，应用会自动识别并提供"长期运行"Tab：

```java
public class MyBgPlugin implements BackgroundPlugin {
    // ... Plugin 接口方法 ...
    
    public void runInBackground(ShizukuProxy proxy, CoroutineScope scope, SubPluginDispatcher dispatcher) {
        while (scope.isActive()) {
            // 通过 dispatcher.call("subPluginId") 调用同包或全局子插件
            String result = dispatcher.call("monitor_activity");
            delay(1000);
        }
    }
}
```

---

## 🏗️ 架构

```
┌──────────────────────────────────────────────────────────┐
│  PluginListScreen (Compose UI)                            │
│  ├─ [手动执行] Tab  ── 列出手动执行插件                     │
│  └─ [长期运行] Tab  ── 列出后台插件，启动/停止               │
├──────────────────────────────────────────────────────────┤
│  PluginManager                                            │
│  ├─ 安装 / 卸载 / 执行插件                                  │
│  ├─ 实例池 (Clone Pool) ── 并发保护, 最多5个临时实例          │
│  ├─ SubPluginDispatcher ── 子插件调用, Mutex 排队           │
│  └─ BackgroundPlugin 生命周期管理                           │
├──────────────────────────────────────────────────────────┤
│  PluginLoader                                             │
│  ├─ DexClassLoader 动态加载                                 │
│  ├─ plugin.properties 解析 → PluginMeta                    │
│  └─ ClassLoader 隔离 (parent = Plugin.class.classLoader)    │
├──────────────────────────────────────────────────────────┤
│  ShizukuProxy                                             │
│  ├─ rish 懒加载初始化                                       │
│  ├─ execCommand (并发读取 stdout/stderr, 死锁保护)            │
│  └─ 6 个 wrapper 方法加参数校验                              │
└──────────────────────────────────────────────────────────┘
```

---

## 📁 插件声明规范 (plugin.properties)

| key | 必需 | 说明 |
|-----|------|------|
| `mainClass` | ✅ | 插件入口类的全限定名 |
| `uid` | 否 | 插件唯一标识，用于升级检测 |
| `version` | 否 | 版本号 |
| `description` | 否 | 描述（优先使用此值） |
| `subPlugins` | 否 | 逗号分隔的子插件 ID 列表 |

---

## 🔒 安全

- **命令行参数校验**：`installApk`、`uninstallApp`、`launchApp`、`getProp`、`putSetting`、`getSetting` 均对参数做正则/白名单/黑名单校验
- **只加载可信插件**：DexClassLoader 可执行任意代码，插件拥有应用全部权限
- **未声明 INTERNET 权限**：减少数据外泄风险

---

## ⚠️ 已知限制

- 仅支持 ARM64（rish 二进制为 ARM64 编译）
- 插件无法使用 Android 资源系统（R.layout 等）
- `.dex` 文件同样需要 `plugin.properties`，不支持类名猜测
- rish 文件每次首次执行时懒加载复制到私有目录

---

## 🔧 构建

```bash
chmod +x ./setup_android_env.sh
./setup_android_env.sh       # 初始化 ARM64 aapt2 + Gradle 环境
./gradlew assembleDebug       # 构建 Debug APK
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

安装：
```bash
cp app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/ && pm install -r /data/local/tmp/app-debug.apk
```

推送插件：
```bash
cp MyPlugin.jar /data/local/tmp/ && run-as com.java.myapplication cp /data/local/tmp/MyPlugin.jar files/plugins/
```

---

## 📝 日志调试

```bash
logcat -s ShizukuProxy:V PluginLoader:V PluginManager:V ClipStack:V
```

---

## 📊 版本兼容

| 组件 | 版本 |
|------|------|
| minSdk | 24 (Android 7.0) |
| targetSdk | 35 |
| JDK | 17 |
| Kotlin | 2.3.10 |
| Compose BOM | 2026.01.01 |
| Shizuku API | 13.1.5 |

