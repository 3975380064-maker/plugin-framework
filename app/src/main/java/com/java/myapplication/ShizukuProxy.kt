package com.java.myapplication

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

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
        android.util.Log.d(tag, "[TRACE] execCommand 入口, command=$command")
        return try {
            android.util.Log.d(tag, "[TRACE] 检查 Shizuku 可用性...")
            if (!isShizukuAvailable()) {
                android.util.Log.w(tag, "[TRACE] Shizuku不可用, 返回错误")
                return "Error: Shizuku服务不可用，请确保Shizuku App已启动"
            }
            android.util.Log.d(tag, "[TRACE] Shizuku可用=true")

            android.util.Log.d(tag, "[TRACE] 检查 Shizuku 权限...")
            if (!checkPermission()) {
                android.util.Log.w(tag, "[TRACE] 无Shizuku权限, 返回错误")
                return "Error: 未获取Shizuku权限，请先授权"
            }
            android.util.Log.d(tag, "[TRACE] Shizuku权限=true")

            val rishFile = File(rishDir, "rish")
            android.util.Log.d(tag, "[TRACE] rish路径=${rishFile.absolutePath}, exists=${rishFile.exists()}")
            if (!rishFile.exists()) {
                // 触发懒加载初始化
                if (!rishReady) {
                    return "Error: rish文件初始化失败"
                }
                if (!rishFile.exists()) {
                    return "Error: rish文件不存在，请重新安装应用"
                }
            }

            val rishDex = File(rishDir, "rish_shizuku.dex")
            android.util.Log.d(tag, "[TRACE] rish_shizuku.dex exists=${rishDex.exists()}, size=${rishDex.length()}, writable=${rishDex.canWrite()}")

            // Android 14+ 需要 dex 不可写
            if (rishDex.canWrite()) {
                android.util.Log.w(tag, "[TRACE] dex 可写, Android14+ 会失败, 尝试 chmod 400")
                val chmodResult = runCatching {
                    val p = ProcessBuilder("chmod", "400", rishDex.absolutePath).start()
                    p.waitFor()
                    p.exitValue()
                }.getOrElse { -1 }
                android.util.Log.d(tag, "[TRACE] chmod 400 结果: exitCode=$chmodResult, writable=${rishDex.canWrite()}")
            }

            android.util.Log.d(tag, "[TRACE] 构建ProcessBuilder: sh ${rishFile.absolutePath} -c $command")
            val pb = ProcessBuilder("sh", rishFile.absolutePath, "-c", command)
            pb.directory(rishDir)
            pb.environment()["RISH_APPLICATION_ID"] = context.packageName
            android.util.Log.d(tag, "[TRACE] RISH_APPLICATION_ID=${context.packageName}, workDir=${rishDir.absolutePath}")

            android.util.Log.d(tag, "[TRACE] 启动进程...")
            val process = pb.start()
            android.util.Log.d(tag, "[TRACE] 进程已启动")

            // 并发读取 stdout / stderr，防止一方缓冲区满导致死锁
            val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            val stdout = stdoutFuture.get()
            val stderr = stderrFuture.get()
            android.util.Log.d(tag, "[TRACE] stdout 长度=${stdout.length}, 内容前200字: ${stdout.take(200)}")
            android.util.Log.d(tag, "[TRACE] stderr 长度=${stderr.length}, 内容: ${stderr.take(500)}")

            android.util.Log.d(tag, "[TRACE] waitFor...")
            val exitCode = process.waitFor()
            android.util.Log.d(tag, "[TRACE] exitCode=$exitCode")

            val result = if (stderr.isNotBlank()) {
                "Output:\n${stdout.trim()}\nError:\n${stderr.trim()}\nExitCode: $exitCode"
            } else {
                stdout.trim()
            }
            android.util.Log.d(tag, "[TRACE] execCommand 完成, 返回长度=${result.length}")
            result
        } catch (e: Exception) {
            android.util.Log.e(tag, "[TRACE] execCommand 异常: ${e.message}", e)
            android.util.Log.e(tag, "[TRACE] 异常类型: ${e.javaClass.name}")
            android.util.Log.e(tag, "[TRACE] 异常堆栈: ${e.stackTraceToString()}")
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
    
    /**
     * 静默安装APK
     * @param apkPath APK文件路径
     * @return 安装结果
     */
    fun installApk(apkPath: String): String {
        if (apkPath.any { it == ';' || it == '&' || it == '|' || it == '$' || it == '`' || it == '\'' || it == '"' }) {
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