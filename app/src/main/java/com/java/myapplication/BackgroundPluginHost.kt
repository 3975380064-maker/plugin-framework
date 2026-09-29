package com.java.myapplication

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 后台常驻插件（BackgroundPlugin）的生命周期管理。
 */
class BackgroundPluginHost(
    private val scope: CoroutineScope,
    private val loader: PluginLoader,
    private val proxy: ShizukuProxy,
    private val dispatcherFactory: () -> SubPluginDispatcher
) {

    private val running = ConcurrentHashMap<String, Job>()

    var onStarted: ((String) -> Unit)? = null
    var onStopped: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start(pluginName: String): Boolean {
        if (running.containsKey(pluginName)) return false
        val plugin = loader.getPlugin(pluginName) ?: return false
        if (plugin !is BackgroundPlugin) return false

        val job = scope.launch {
            try {
                onStarted?.invoke(pluginName)
                plugin.runInBackground(proxy, this, dispatcherFactory())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BackgroundPluginHost", "后台插件 $pluginName 异常", e)
                onError?.invoke("后台插件 $pluginName 异常: ${e.message}")
            } finally {
                running.remove(pluginName)
                onStopped?.invoke(pluginName)
            }
        }
        running[pluginName] = job
        return true
    }

    fun stop(pluginName: String): Boolean {
        val job = running.remove(pluginName) ?: return false
        job.cancel()
        return true
    }

    fun isRunning(pluginName: String): Boolean = running.containsKey(pluginName)

    fun runningPlugins(): Set<String> = running.keys.toSet()
}