# 插件通过类名引用以下接口与类，R8 不得重命名或裁剪，
# 否则已编译好的插件 jar 在 DexClassLoader 中无法链接。

-keep interface com.java.myapplication.Plugin { *; }
-keep interface com.java.myapplication.BackgroundPlugin { *; }
-keep interface com.java.myapplication.SubPluginDispatcher { *; }
-keep class com.java.myapplication.ShizukuProxy {
    public *;
}

# Shizuku Provider 由 manifest 引用
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
