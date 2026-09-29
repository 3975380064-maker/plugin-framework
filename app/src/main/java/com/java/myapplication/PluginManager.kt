package com.java.myapplication

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 插件管理器：对外唯一入口。
 * 编排插件加载、安装卸载、执行、实例池与后台常驻插件。
 */
class PluginManager(private val context: Context) {

    private val pluginLoader = PluginLoader(context)
    private val shizukuProxy = ShizukuProxy(context)
    private val pool = PluginInstancePool(pluginLoader)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val backgroundHost = BackgroundPluginHost(
        scope = scope,
        loader = pluginLoader,
        proxy = shizukuProxy,
        dispatcherFactory = { SubPluginDispatcherImpl(pluginLoader, shizukuProxy, pool) }
    )

    private val installer = PluginInstaller(context, pluginLoader)

    var onPluginLoaded: ((Plugin) -> Unit)? = null
    var onPluginUnloaded: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onBackgroundPluginStarted: ((String) -> Unit)? = null
    var onBackgroundPluginStopped: ((String) -> Unit)? = null

    init {
        backgroundHost.onStarted = { onBackgroundPluginStarted?.invoke(it) }
        backgroundHost.onStopped = { onBackgroundPluginStopped?.invoke(it) }
        backgroundHost.onError = { onError?.invoke(it) }

        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                pool.evictIdle()
            }
        }
    }

    fun initialize() {
        if (!shizukuProxy.isShizukuAvailable()) {
            onError?.invoke("Shizuku服务不可用，部分功能可能受限")
        }
        loadPlugins()
    }

    fun loadPlugins(): List<Plugin> {
        return try {
            val plugins = pluginLoader.loadAllPlugins()
            plugins.forEach { onPluginLoaded?.invoke(it) }
            plugins
        } catch (e: Exception) {
            onError?.invoke("加载插件失败: ${e.message}")
            emptyList()
        }
    }

    fun executePlugin(pluginName: String, args: Map<String, Any>? = null): String {
        val plugin = pluginLoader.getPlugin(pluginName)
        if (plugin == null) {
            val errorMsg = "插件 $pluginName 未找到。可能原因：\n" +
                          "1. 插件文件缺少 META-INF/plugin.properties\n" +
                          "2. mainClass 声明有误\n" +
                          "3. 插件未正确实现 Plugin 接口"
            onError?.invoke(errorMsg)
            return "Error: Plugin not found\n\n$errorMsg"
        }

        return try {
            plugin.execute(shizukuProxy, args)
        } catch (e: Exception) {
            val errorMsg = "执行插件失败: ${e.message}\n堆栈跟踪: ${e.stackTraceToString()}"
            onError?.invoke(errorMsg)
            "Error: $errorMsg"
        }
    }

    fun installPluginFromUri(uri: Uri): Boolean {
        val error = installer.install(uri)
        if (error != null) {
            onError?.invoke(error)
            return false
        }
        loadPlugins()
        return true
    }

    fun uninstallPlugin(pluginName: String): Boolean {
        val error = installer.uninstall(pluginName)
        if (error != null) {
            onError?.invoke(error)
            return false
        }
        onPluginUnloaded?.invoke(pluginName)
        return true
    }

    fun reloadPlugins(): List<Plugin> = pluginLoader.reloadPlugins()

    fun getLoadedPlugins(): List<Plugin> = pluginLoader.getLoadedPlugins()

    fun getPluginMeta(name: String): PluginMeta? = pluginLoader.getPluginMeta(name)

    fun checkShizukuPermission(): Boolean = shizukuProxy.checkPermission()

    fun requestShizukuPermission() = shizukuProxy.requestPermission()

    fun startBackgroundPlugin(pluginName: String): Boolean = backgroundHost.start(pluginName)

    fun stopBackgroundPlugin(pluginName: String): Boolean = backgroundHost.stop(pluginName)

    fun isBackgroundPluginRunning(pluginName: String): Boolean = backgroundHost.isRunning(pluginName)

    fun getRunningBackgroundPlugins(): Set<String> = backgroundHost.runningPlugins()

    fun destroy() {
        scope.cancel()
    }

    private companion object {
        const val CLEANUP_INTERVAL_MS = 30_000L
    }
}