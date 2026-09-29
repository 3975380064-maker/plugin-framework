package com.java.myapplication

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Shizuku代理类 - 使用.rish文件执行高权限命令
 * 封装Shizuku的高权限操作
 */
class ShizukuProxy(private val context: Context) {

    private val rishDir: File = File(context.filesDir, "rish")

    // L13: 懒加载，不在构造里做 IO
    private val rishReady: Boolean by lazy { setupRishFiles() }

    /**
     * 从assets复制rish文件到应用私有目录
     */
    private fun setupRishFiles(): Boolean {
        return try {
            if (!rishDir.exists()) {
                rishDir.mkdirs()
            }

            // 复制rish文件
            val rishFile = File(rishDir, "rish")
            if (!rishFile.exists()) {
                context.assets.open("rish").use { input ->
                    rishFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                rishFile.setExecutable(true)
            }

            // 复制rish_shizuku.dex文件
            val dexFile = File(rishDir, "rish_shizuku.dex")
            if (!dexFile.exists()) {
                context.assets.open("rish_shizuku.dex").use { input ->
                    dexFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            android.util.Log.d("ShizukuProxy", "rish文件初始化完成: ${rishDir.absolutePath}")
            true
        } catch (e: Exception) {
            android.util.Log.e("ShizukuProxy", "初始化rish文件失败: ${e.message}", e)
            false
        }
    }

    /**
     * 检查Shizuku服务是否可用
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查Shizuku权限
     */
    fun checkPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求Shizuku权限
     * @param requestCode 请求码
     */
    fun requestPermission(requestCode: Int = 0) {
        if (!isShizukuAvailable()) {
            android.util.Log.w("ShizukuProxy", "Shizuku服务不可用，无法请求权限")
            return
        }

        try {
            if (checkPermission()) {
                android.util.Log.d("ShizukuProxy", "已有权限，无需重复请求")
                return
            }

            android.util.Log.d("ShizukuProxy", "请求Shizuku权限，requestCode=$requestCode")
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            android.util.Log.e("ShizukuProxy", "请求权限失败: ${e.message}", e)
        }
    }

    /**
     * 执行shell命令 - 使用.rish文件执行 [核心方法]
     * 通过ProcessBuilder参数列表避免命令注入
     * @param command 要执行的命令
     * @return 命令输出结果
     */
    fun execCommand(command: String): String {
        val tag = "ShizukuProxy"
        android.util.Log.v(tag, "execCommand: $command")
        return try {
            if (!isShizukuAvailable()) {
                android.util.Log.w(tag, "Shizuku不可用")
                return "Error: Shizuku服务不可用，请确保Shizuku App已启动"
            }

            if (!checkPermission()) {
                android.util.Log.w(tag, "无Shizuku权限")
                return "Error: 未获取Shizuku权限，请先授权"
            }

            val rishFile = File(rishDir, "rish")
            if (!rishFile.exists()) {
                // 触发懒加载初始化
                if (!rishReady) {
                    return "Error: rish文件初始化失败"
                }
                if (!rishFile.exists()) {
                    return "Error: rish文件不存在，请重新安装应用"
                }
            }

            // Android 14+ 需要 dex 不可写
            val rishDex = File(rishDir, "rish_shizuku.dex")
            if (rishDex.canWrite()) {
                runCatching {
                    ProcessBuilder("chmod", "400", rishDex.absolutePath).start().waitFor()
                }.onFailure { android.util.Log.w(tag, "chmod 400 失败", it) }
            }

            val pb = ProcessBuilder("sh", rishFile.absolutePath, "-c", command)
            pb.directory(rishDir)
            pb.environment()["RISH_APPLICATION_ID"] = context.packageName

            val process = pb.start()

            // 并发读取 stdout / stderr，防止一方缓冲区满导致死锁
            val stdoutFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            val stdout = stdoutFuture.get().trim()
            val stderr = stderrFuture.get().trim()
            val exitCode = process.waitFor()

            // rish 把命令输出写到 stderr、stdout 为空，因此不能按 stderr 是否为空判定失败，
            // 只能依据退出码。
            val result = when {
                exitCode == 0 -> stdout.ifBlank { stderr }
                stdout.isBlank() && stderr.isBlank() -> "ExitCode: $exitCode"
                stdout.isBlank() -> "Error:\n$stderr\nExitCode: $exitCode"
                stderr.isBlank() -> "$stdout\nExitCode: $exitCode"
                else -> "Output:\n$stdout\nError:\n$stderr\nExitCode: $exitCode"
            }
            android.util.Log.v(tag, "execCommand 完成, exitCode=$exitCode, 返回长度=${result.length}")
            result
        } catch (e: Exception) {
            android.util.Log.e(tag, "execCommand 异常: ${e.message}", e)
            "Error: 执行命令异常: ${e.message}"
        }
    }

    // ---- 参数校验辅助 ----

    /** 包名合法字符：[a-zA-Z0-9._-] */
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

    /** 属性名合法字符：[a-zA-Z0-9._-] */
    private val PROP_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

    /** settings namespace 白名单 */
    private val SETTINGS_NAMESPACES = setOf("system", "secure", "global")

    /** settings key 合法字符 */
    private val SETTINGS_KEY_REGEX = Regex("^[a-zA-Z0-9._-]+$")

    /** Activity 组件名合法字符：[a-zA-Z0-9._-]（含完整类名） */
    private val ACTIVITY_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

    /** APK 路径白名单：仅允许字母、数字、点、斜杠、下划线、连字符 */
    private val APK_PATH_REGEX = Regex("^[a-zA-Z0-9._/\\-]+$")

    /**
     * 静默安装APK
     * @param apkPath APK文件路径
     * @return 安装结果
     */
    fun installApk(apkPath: String): String {
        if (!APK_PATH_REGEX.matches(apkPath)) {
            return "Error: apkPath 包含非法字符"
        }
        return execCommand("pm install -r $apkPath")
    }

    /**
     * 卸载应用
     * @param packageName 包名
     * @return 卸载结果
     */
    fun uninstallApp(packageName: String): String {
        if (!PACKAGE_NAME_REGEX.matches(packageName)) {
            return "Error: 包名格式不合法"
        }
        return execCommand("pm uninstall $packageName")
    }

    /**
     * 启动应用
     * @param packageName 包名
     * @param activityName Activity名称（可选）
     * @return 启动结果
     */
    fun launchApp(packageName: String, activityName: String? = null): String {
        if (!PACKAGE_NAME_REGEX.matches(packageName)) {
            return "Error: 包名格式不合法"
        }
        val command = if (activityName != null) {
            if (!ACTIVITY_NAME_REGEX.matches(activityName)) {
                return "Error: Activity名称格式不合法"
            }
            "am start -n $packageName/$activityName"
        } else {
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        }
        return execCommand(command)
    }

    /**
     * 获取设备属性
     * @param prop 属性名
     * @return 属性值
     */
    fun getProp(prop: String): String {
        if (!PROP_NAME_REGEX.matches(prop)) {
            return "Error: 属性名格式不合法"
        }
        return execCommand("getprop $prop")
    }

    /**
     * 设置系统设置
     * @param namespace 命名空间（system/secure/global）
     * @param key 设置键
     * @param value 设置值
     * @return 设置结果
     */
    fun putSetting(namespace: String, key: String, value: String): String {
        if (namespace !in SETTINGS_NAMESPACES) {
            return "Error: namespace 必须是 system/secure/global 之一"
        }
        if (!SETTINGS_KEY_REGEX.matches(key)) {
            return "Error: 设置键格式不合法"
        }
        if (value.any { it == ';' || it == '&' || it == '|' || it == '$' || it == '`' || it == '\'' || it == '"' || it == '\n' || it == '\r' }) {
            return "Error: 设置值包含非法字符"
        }
        return execCommand("settings put $namespace $key $value")
    }

    /**
     * 获取系统设置
     * @param namespace 命名空间
     * @param key 设置键
     * @return 设置值
     */
    fun getSetting(namespace: String, key: String): String {
        if (namespace !in SETTINGS_NAMESPACES) {
            return "Error: namespace 必须是 system/secure/global 之一"
        }
        if (!SETTINGS_KEY_REGEX.matches(key)) {
            return "Error: 设置键格式不合法"
        }
        return execCommand("settings get $namespace $key")
    }

    /**
     * 销毁代理，释放资源
     */
    fun destroy() {
        // 清理资源（.ish文件保留在应用目录）
        android.util.Log.d("ShizukuProxy", "ShizukuProxy销毁")
    }
}