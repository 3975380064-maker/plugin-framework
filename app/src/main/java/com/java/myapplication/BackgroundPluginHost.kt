package com.java.myapplication

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 后台常驻插件（BackgroundPlugin）的生命周期管理。
 *
 * 协程以 LAZY 方式创建并先登记再 start()，避免"协程先结束、随后才写入运行表"
 * 造成的僵尸条目。
 */
class BackgroundPluginHost(
    private val scope: CoroutineScope,
    private val loader: PluginLoader,
    private val proxy: ShizukuProxy,
    private val dispatcherFactory: () -> SubPluginDispatcher
) {

    private val running = ConcurrentHashMap<String, Job>()

    var onError: ((String) -> Unit)? = null

    /** 运行集合变化时回调，供宿主控制保活服务。 */
    var onRunningChanged: ((Set<String>) -> Unit)? = null

    fun start(pluginName: String): Boolean {
        if (running.containsKey(pluginName)) return false
        val plugin = loader.getPlugin(pluginName) ?: return false
        if (plugin !is BackgroundPlugin) return false

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                plugin.runInBackground(proxy, this, dispatcherFactory())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "后台插件 $pluginName 异常", e)
                onError?.invoke("后台插件 $pluginName 异常: ${e.message}")
            } finally {
                running.remove(pluginName)
                onRunningChanged?.invoke(running.keys.toSet())
            }
        }

        running[pluginName] = job
        job.start()
        onRunningChanged?.invoke(running.keys.toSet())
        return true
    }

    fun stop(pluginName: String): Boolean {
        val job = running.remove(pluginName) ?: return false
        job.cancel()
        onRunningChanged?.invoke(running.keys.toSet())
        return true
    }

    /** 停止全部后台插件（卸载/重载/退出时调用）。 */
    fun stopAll() {
        val names = running.keys.toList()
        names.forEach { running.remove(it)?.cancel() }
        if (names.isNotEmpty()) onRunningChanged?.invoke(running.keys.toSet())
    }

    fun isRunning(pluginName: String): Boolean = running.containsKey(pluginName)

    fun runningPlugins(): Set<String> = running.keys.toSet()

    private companion object {
        const val TAG = "BackgroundPluginHost"
    }
}