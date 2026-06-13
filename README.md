# Plugin Framework — 基于 Shizuku 的 Android 通用插件框架

通过上传 `.jar` 插件包来扩展 Android 系统功能，插件通过 **Shizuku** 获得 ADB 级别权限，无需 Root。

---

## 🚀 核心特性

- **热加载插件** — 选择 jar 文件即可安装，无需重启应用
- **ADB 级权限** — 通过 Shizuku + rish 执行高权限 Shell 命令
- **后台常驻** — `BackgroundPlugin` 接口支持长期运行任务，配合子插件调度实现自动化
- **实例池** — 并发调用时自动创建临时实例，空闲后自动回收，Mutex 保护无竞态
- **ClassLoader 隔离** — 每个插件独立 ClassLoader，同名类不冲突
- **安全防护** — 所有 Shell 调用入口加参数格式校验，防命令注入

---

## 📦 安装

1. 安装 [Shizuku](https://github.com/RikkaApps/Shizuku) 并启动
2. 下载本应用 [APK](https://github.com/3975380064-maker/plugin-framework/releases/latest)
3. 在 Shizuku 中授权本应用
4. 通过应用内 `+` 按钮导入插件 jar 包

---

## 🏗️ 架构

```
┌──────────────────────────────────────────────────────────┐
│  PluginListScreen (Compose UI)                            │
│  ├─ [手动执行] Tab  ── 列出所有插件，点击执行               │
│  └─ [长期运行] Tab  ── 列出 BackgroundPlugin，启动/停止     │
├──────────────────────────────────────────────────────────┤
│  PluginManager                                            │
│  ├─ 安装 / 卸载 / 执行插件                                  │
│  ├─ 实例池 (Clone Pool) ── 并发保护, 最多 5 个临时实例       │
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
│  ├─ execCommand (并发读取 stdout/stderr, 死锁保护)           │
│  └─ 6 个 wrapper 方法加参数校验                              │
└──────────────────────────────────────────────────────────┘
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

---

## 📝 日志调试

```bash
logcat -s ShizukuProxy:V PluginLoader:V PluginManager:V
```

---

## 🛠️ 构建

```bash
chmod +x ./setup_android_env.sh
./setup_android_env.sh        # ARM64 aapt2 + Gradle 环境
./gradlew assembleDebug       # 构建 Debug APK
```

---

## 🧩 插件开发

插件是一个实现 `Plugin` 接口的 Java/Kotlin 类，打包为 jar 后导入框架即可运行。支持手动执行、后台常驻、子插件调度等模式。

> **完整教程和示例代码**：[plugin-framework-examples](https://github.com/3975380064-maker/plugin-framework-examples)

---

## ⚠️ 已知限制

- 仅支持 ARM64（rish 二进制为 ARM64 编译）
- 插件无法使用 Android 资源系统（R.layout 等）
- 插件必须包含 `META-INF/plugin.properties` 声明 `mainClass`

---

## 📄 License

Apache License 2.0
