package com.java.myapplication

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.util.concurrent.CompletableFuture
import rikka.shizuku.Shizuku

/**
 * Shizuku 代理：通过 rish 执行高权限命令。
 * 所有对外方法都先做参数白名单校验（见 [ShellArgs]）。
 */
class ShizukuProxy(private val context: Context) {

    private val rishDir: File = File(context.filesDir, "rish")

    // 懒加载，不在构造里做 IO
    private val rishReady: Boolean by lazy { setupRishFiles() }

    private fun setupRishFiles(): Boolean {
        return try {
            rishDir.mkdirs()

            val rishFile = File(rishDir, "rish")
            if (!rishFile.exists()) {
                context.assets.open("rish").use { input ->
                    rishFile.outputStream().use { output -> input.copyTo(output) }
                }
                rishFile.setExecutable(true)
            }

            val dexFile = File(rishDir, "rish_shizuku.dex")
            if (!dexFile.exists()) {
                context.assets.open("rish_shizuku.dex").use { input ->
                    dexFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "初始化 rish 文件失败", e)
            false
        }
    }

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun checkPermission(): Boolean = try {
        isShizukuAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int = 0) {
        if (!isShizukuAvailable()) {
            android.util.Log.w(TAG, "Shizuku 服务不可用，无法请求权限")
            return
        }
        try {
            if (checkPermission()) return
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "请求权限失败", e)
        }
    }

    /**
     * 执行 shell 命令。
     *
     * 注意：rish 会把命令输出写到 stderr、stdout 为空，因此不能按
     * "stderr 非空即失败" 判断，只能用退出码。
     */
    fun execCommand(command: String): String {
        android.util.Log.v(TAG, "execCommand: $command")
        return try {
            if (!isShizukuAvailable()) {
                return "Error: Shizuku服务不可用，请确保Shizuku App已启动"
            }
            if (!checkPermission()) {
                return "Error: 未获取Shizuku权限，请先授权"
            }

            val rishFile = File(rishDir, "rish")
            if (!rishFile.exists()) {
                if (!rishReady) return "Error: rish文件初始化失败"
                if (!rishFile.exists()) return "Error: rish文件不存在，请重新安装应用"
            }

            // Android 14+ 要求 dex 不可写
            val rishDex = File(rishDir, "rish_shizuku.dex")
            if (rishDex.canWrite()) {
                runCatching {
                    ProcessBuilder("chmod", "400", rishDex.absolutePath).start().waitFor()
                }.onFailure { android.util.Log.w(TAG, "chmod 400 失败", it) }
            }

            val pb = ProcessBuilder("sh", rishFile.absolutePath, "-c", command)
            pb.directory(rishDir)
            pb.environment()["RISH_APPLICATION_ID"] = context.packageName

            val process = pb.start()

            // 并发读取 stdout / stderr，避免一方缓冲满导致死锁
            val stdoutFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            val stdout = stdoutFuture.get().trim()
            val stderr = stderrFuture.get().trim()
            val exitCode = process.waitFor()

            val result = when {
                exitCode == 0 -> stdout.ifBlank { stderr }
                stdout.isBlank() && stderr.isBlank() -> "ExitCode: $exitCode"
                stdout.isBlank() -> "Error:\n$stderr\nExitCode: $exitCode"
                stderr.isBlank() -> "$stdout\nExitCode: $exitCode"
                else -> "Output:\n$stdout\nError:\n$stderr\nExitCode: $exitCode"
            }
            android.util.Log.v(TAG, "execCommand 完成, exitCode=$exitCode, 返回长度=${result.length}")
            result
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "execCommand 异常", e)
            "Error: 执行命令异常: ${e.message}"
        }
    }

    /** 静默安装 APK。 */
    fun installApk(apkPath: String): String {
        if (!ShellArgs.isValidApkPath(apkPath)) return "Error: apkPath 包含非法字符"
        return execCommand("pm install -r $apkPath")
    }

    fun uninstallApp(packageName: String): String {
        if (!ShellArgs.isValidPackageName(packageName)) return "Error: 包名格式不合法"
        return execCommand("pm uninstall $packageName")
    }

    fun launchApp(packageName: String, activityName: String? = null): String {
        if (!ShellArgs.isValidPackageName(packageName)) return "Error: 包名格式不合法"
        val command = if (activityName != null) {
            if (!ShellArgs.isValidActivityName(activityName)) {
                return "Error: Activity名称格式不合法"
            }
            "am start -n $packageName/$activityName"
        } else {
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        }
        return execCommand(command)
    }

    fun getProp(prop: String): String {
        if (!ShellArgs.isValidPropName(prop)) return "Error: 属性名格式不合法"
        return execCommand("getprop $prop")
    }

    fun putSetting(namespace: String, key: String, value: String): String {
        if (!ShellArgs.isValidNamespace(namespace)) {
            return "Error: namespace 必须是 system/secure/global 之一"
        }
        if (!ShellArgs.isValidSettingsKey(key)) return "Error: 设置键格式不合法"
        if (!ShellArgs.isValidSettingValue(value)) return "Error: 设置值包含非法字符"
        return execCommand("settings put $namespace $key $value")
    }

    fun getSetting(namespace: String, key: String): String {
        if (!ShellArgs.isValidNamespace(namespace)) {
            return "Error: namespace 必须是 system/secure/global 之一"
        }
        if (!ShellArgs.isValidSettingsKey(key)) return "Error: 设置键格式不合法"
        return execCommand("settings get $namespace $key")
    }

    private companion object {
        const val TAG = "ShizukuProxy"
    }
}