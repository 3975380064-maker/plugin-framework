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
import kotlinx.coroutines.withContext

/**
 * 插件管理器：编排加载、安装卸载、执行、实例池与后台常驻插件。
 *
 * 本身不持有 UI 状态。所有会阻塞的方法都在内部切到 [Dispatchers.IO]，
 * 调用方（UI）用哪个调度器都不会卡主线程 —— 插件执行会起进程、耗时数秒。
 */
class PluginManager(context: Context) {

    private val appContext = context.applicationContext
    private val pluginLoader = PluginLoader(appContext)
    private val shizukuProxy = ShizukuProxy(appContext)
    private val pool = PluginInstancePool(pluginLoader)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val backgroundHost = BackgroundPluginHost(
        scope = scope,
        loader = pluginLoader,
        proxy = shizukuProxy,
        dispatcherFactory = { SubPluginDispatcherImpl(pluginLoader, shizukuProxy, pool) }
    )

    private val installer = PluginInstaller(appContext, pluginLoader)

    var onError: ((String) -> Unit)? = null
    var onBackgroundPluginsChanged: ((Set<String>) -> Unit)? = null

    init {
        backgroundHost.onError = { onError?.invoke(it) }
        backgroundHost.onRunningChanged = { onBackgroundPluginsChanged?.invoke(it) }
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                pool.evictIdle()
            }
        }
    }

    suspend fun loadPlugins(): List<Plugin> = withContext(Dispatchers.IO) {
        runCatching { pluginLoader.loadAllPlugins() }
            .getOrElse {
                onError?.invoke("加载插件失败: ${it.message}")
                emptyList()
            }
    }

    /**
     * 全部重新加载。
     * 会重建 ClassLoader，因此先停掉后台插件并丢弃实例池，避免旧类的孤儿任务。
     */
    suspend fun reloadPlugins(): List<Plugin> = withContext(Dispatchers.IO) {
        backgroundHost.stopAll()
        pool.discardAll()
        runCatching { pluginLoader.reloadPlugins() }
            .getOrElse {
                onError?.invoke("加载插件失败: ${it.message}")
                emptyList()
            }
    }

    suspend fun executePlugin(pluginName: String): String = withContext(Dispatchers.IO) {
        val plugin = pluginLoader.getPlugin(pluginName)
            ?: return@withContext "Error: Plugin not found\n\n" +
                    "插件 $pluginName 未找到。可能原因：\n" +
                    "1. 插件文件缺少 META-INF/plugin.properties\n" +
                    "2. mainClass 声明有误\n" +
                    "3. 插件未正确实现 Plugin 接口"

        try {
            plugin.execute(shizukuProxy, null)
        } catch (e: Throwable) {
            val msg = "执行插件失败: ${e.message}\n堆栈跟踪: ${e.stackTraceToString()}"
            onError?.invoke(msg)
            "Error: $msg"
        }
    }

    /** 安装成功返回 null，失败返回错误信息。 */
    suspend fun installPluginFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val error = installer.install(uri)
        if (error == null) pluginLoader.loadAllPlugins()
        error
    }

    /** 卸载成功返回 null，失败返回错误信息。 */
    suspend fun uninstallPlugin(pluginName: String): String? = withContext(Dispatchers.IO) {
        backgroundHost.stop(pluginName)
        try {
            val error = installer.uninstall(pluginName)
            if (error == null) pool.discard(pluginName)
            error
        } catch (e: Throwable) {
            "卸载插件失败: ${e.message}"
        }
    }

    fun getLoadedPlugins(): List<Plugin> = pluginLoader.getLoadedPlugins()

    fun getPluginMeta(name: String): PluginMeta? = pluginLoader.getPluginMeta(name)

    fun isShizukuAvailable(): Boolean = shizukuProxy.isShizukuAvailable()

    fun checkShizukuPermission(): Boolean = shizukuProxy.checkPermission()

    fun requestShizukuPermission() = shizukuProxy.requestPermission()

    fun startBackgroundPlugin(pluginName: String): Boolean = backgroundHost.start(pluginName)

    fun stopBackgroundPlugin(pluginName: String): Boolean = backgroundHost.stop(pluginName)

    fun isBackgroundPluginRunning(pluginName: String): Boolean = backgroundHost.isRunning(pluginName)

    fun getRunningBackgroundPlugins(): Set<String> = backgroundHost.runningPlugins()

    fun shutdown() {
        backgroundHost.stopAll()
        scope.cancel()
    }

    private companion object {
        const val CLEANUP_INTERVAL_MS = 30_000L
    }
}