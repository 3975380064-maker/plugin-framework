# 通用插件框架 - Android 插件系统

## 🎯 框架简介

这是一个基于 Shizuku 的 Android 通用插件框架，允许用户通过上传 `.jar` 或 `.dex` 文件来扩展应用功能。插件可以使用 Shizuku 获得 ADB 级别的权限，执行高权限操作。

## 📁 项目结构

```
app/src/main/java/com/java/myapplication/
├── Plugin.kt            # 插件接口定义
├── ShizukuProxy.kt      # Shizuku 权限代理
├── PluginLoader.kt      # 插件动态加载器
├── PluginManager.kt     # 插件管理器
├── MainActivity.kt      # 主界面入口
└── ui/
    └── PluginListScreen.kt  # 插件管理界面
```

## 🔧 核心组件说明

### 1. Plugin 接口
所有插件必须实现此接口：
```java
public interface Plugin {
    String getName();           // 插件名称
    String getDescription();    // 插件描述
    String getVersion();        // 插件版本
    String execute(ShizukuProxy proxy, Map<String, Object> args);  // 执行入口
    boolean needsShizuku();    // 是否需要 Shizuku 权限
}
```

### 2. ShizukuProxy
封装了 Shizuku 的高权限操作：
- `execCommand(String cmd)` - 执行 shell 命令
- `installApk(String path)` - 静默安装 APK
- `uninstallApp(String pkg)` - 卸载应用
- `launchApp(String pkg)` - 启动应用
- `getProp(String prop)` - 获取设备属性
- `putSetting(ns, key, value)` - 修改系统设置

### 3. PluginLoader
负责动态加载 `.jar`/`.dex` 文件中的插件类。

### 4. PluginManager
插件生命周期管理，支持：
- 安装/卸载插件
- 执行插件
- 刷新插件列表

## 📝 如何编写插件

### 步骤 1：创建插件类

```java
package com.plugin;

import com.java.myapplication.Plugin;
import com.java.myapplication.ShizukuProxy;
import java.util.Map;

public class MyPlugin implements Plugin {
    
    @Override
    public String getName() {
        return "MyPlugin";
    }
    
    @Override
    public String getDescription() {
        return "我的自定义插件";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean needsShizuku() {
        return true;  // 需要 Shizuku 权限
    }
    
    @Override
    public String execute(ShizukuProxy proxy, Map<String, Object> args) {
        // 执行你的逻辑
        String result = proxy.execCommand("echo Hello World!");
        return "执行结果: " + result;
    }
}
```

### 步骤 2：编译插件

#### 方法一：使用 d8 工具（推荐）

1. **准备依赖**
   - 将宿主应用编译后的 `classes.jar` 或接口定义文件作为依赖

2. **编译为 .class 文件**
   ```bash
   javac -cp host-app.jar MyPlugin.java
   ```

3. **转换为 .dex 文件**
   ```bash
   # 使用 Android SDK 的 d8 工具
   d8 MyPlugin.class --output .
   # 会生成 classes.dex，重命名为 MyPlugin.dex
   ```

#### 方法二：使用 Android Studio

1. 创建一个 Android Library 模块
2. 实现 Plugin 接口
3. 编译后从 `build/intermediates/dex/release/` 获取 `.dex` 文件

### 步骤 3：上传插件

将编译好的 `.dex` 或 `.jar` 文件复制到：
```
/sdcard/Android/data/com.java.myapplication/files/plugins/
```

或者在应用内使用"+"按钮选择文件上传。

## 🚀 使用 Shizuku

### 前置条件

1. **安装 Shizuku 应用**
   - 从 Google Play 或 GitHub 下载 Shizuku
   - 地址：https://github.com/RikkaApps/Shizuku

2. **启动 Shizuku 服务**
   - 方法一：通过 ADB 启动
     ```bash
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
     ```
   - 方法二：通过无线调试启动（Android 11+）

3. **授权应用**
   - 打开 Shizuku 应用
   - 找到"插件框架"应用
   - 授予权限

### 权限说明

应用在 `AndroidManifest.xml` 中声明了：
```xml
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
```

## 📦 支持的文件格式

| 格式 | 说明 | 推荐度 |
|------|------|--------|
| `.dex` | Dalvik 可执行文件 | ⭐⭐⭐⭐⭐ |
| `.jar` | Java 归档文件 | ⭐⭐⭐⭐ |

## ⚠️ 注意事项

1. **安全性**
   - 只加载可信来源的插件
   - 插件拥有 ADB 级别权限，请谨慎使用

2. **兼容性**
   - 最低支持 Android 7.0 (API 24)
   - 需要 Shizuku 服务运行

3. **限制**
   - 插件无法直接访问 Android Framework API
   - 仅能通过 ShizukuProxy 执行 shell 命令

## 🔨 示例插件

查看 `tools/ExamplePlugin.java` 文件，这是一个展示设备信息的示例插件。

## 📚 API 参考

### ShizukuProxy 方法列表

| 方法 | 说明 | 示例 |
|------|------|------|
| `execCommand(cmd)` | 执行 shell 命令 | `execCommand("ls /sdcard")` |
| `installApk(path)` | 安装 APK | `installApk("/sdcard/app.apk")` |
| `uninstallApp(pkg)` | 卸载应用 | `uninstallApp("com.example.app")` |
| `launchApp(pkg)` | 启动应用 | `launchApp("com.example.app")` |
| `getProp(prop)` | 获取属性 | `getProp("ro.product.model")` |
| `putSetting(ns, k, v)` | 设置系统值 | `putSetting("system", "screen_brightness", "128")` |
| `getSetting(ns, k)` | 获取系统值 | `getSetting("system", "screen_brightness")` |

## 🐛 常见问题

### Q: 提示"Shizuku服务不可用"
A: 请确保 Shizuku 应用已安装并正在运行。

### Q: 插件加载失败
A: 检查插件文件是否正确实现了 Plugin 接口，包名是否正确。

### Q: 执行命令返回空
A: 某些命令需要 root 权限，Shizuku 只提供 ADB 级别权限。

## 📄 License

MIT License

---

**作者**: AI助手  
**版本**: 1.0.0  
**日期**: 2024