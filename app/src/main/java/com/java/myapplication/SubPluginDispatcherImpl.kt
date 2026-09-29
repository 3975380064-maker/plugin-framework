package com.java.myapplication

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 子插件调度器实现。
 * 同一子插件 ID 串行执行，避免并发触发同一子插件逻辑。
 */
class SubPluginDispatcherImpl(
    private val loader: PluginLoader,
    private val proxy: ShizukuProxy,
    private val pool: PluginInstancePool
) : SubPluginDispatcher {

    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun call(subPluginId: String, args: Map<String, String>): String {
        val lock = locks.computeIfAbsent(subPluginId) { Mutex() }
        return lock.withLock {
            for (plugin in loader.getLoadedPlugins()) {
                val meta = loader.getPluginMeta(plugin.getName())
                if (meta?.subPlugins?.contains(subPluginId) == true) {
                    val instance = pool.acquire(plugin.getName())
                    try {
                        return instance.execute(proxy, mapOf("subPluginId" to subPluginId))
                    } finally {
                        pool.release(plugin.getName(), instance)
                    }
                }
            }
            "Error: 未找到子插件 $subPluginId"
        }
    }
}