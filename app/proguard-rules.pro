# 本应用是插件宿主：外部插件按类名/方法名引用宿主导出的符号，
# R8 混淆会重命名 kotlinx.coroutines 等宿主自带库，
# 导致 Kotlin 插件在 release 版运行时 NoSuchMethodError。
# 因此只做 shrink（删无用代码），不做 obfuscate。
-dontobfuscate

# 插件通过类名引用以下接口与类，R8 不得重命名或裁剪，
# 否则已编译好的插件 jar 在 DexClassLoader 中无法链接。

-keep interface com.java.myapplication.Plugin { *; }
-keep interface com.java.myapplication.BackgroundPlugin { *; }
-keep interface com.java.myapplication.SubPluginDispatcher { *; }
-keep class com.java.myapplication.ShizukuProxy {
    public *;
}

# 插件侧运行常驻逻辑要用到的协程 API。宿主自身不一定引用到它们
#（例如 delay / isActive），不 keep 会被 shrink 删掉，
# 导致插件在 release 版运行时报 NoClassDefFoundError。
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# Kotlin 编译产物会引用 stdlib 的一堆符号（例如 suspend 函数的
# kotlin.ResultKt.throwOnFailure）。宿主自身可能完全用不到，
# shrink 掉之后 Kotlin 插件一进 runInBackground 就 NoClassDefFoundError。
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Shizuku Provider 由 manifest 引用
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
