package com.java.myapplication

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 子插件调度器实现。
 *
 * 同一子插件 ID 串行执行；但同一调用链内的重入（插件在自己的 execute 里
 * 对同一 ID 再次 call）绕过锁直接执行，否则会自锁。
 */
class SubPluginDispatcherImpl(
    private val loader: PluginLoader,
    private val proxy: ShizukuProxy,
    private val pool: PluginInstancePool
) : SubPluginDispatcher {

    /** 标记当前调用链已持有的子插件 ID。 */
    private class CallChain : AbstractCoroutineContextElement(CallChain) {
        val ids: MutableSet<String> = ConcurrentHashMap.newKeySet()

        companion object Key : CoroutineContext.Key<CallChain>
    }

    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun call(subPluginId: String, args: Map<String, String>): String {
        val chain = currentCoroutineContext()[CallChain] ?: CallChain()
        return withContext(chain) {
            if (!chain.ids.add(subPluginId)) {
                return@withContext runSubPlugin(subPluginId, args)
            }
            try {
                locks.computeIfAbsent(subPluginId) { Mutex() }
                    .withLock { runSubPlugin(subPluginId, args) }
            } finally {
                chain.ids.remove(subPluginId)
            }
        }
    }

    private suspend fun runSubPlugin(subPluginId: String, args: Map<String, String>): String {
        for (plugin in loader.getLoadedPlugins()) {
            val meta = loader.getPluginMeta(plugin.getName())
            if (meta?.subPlugins?.contains(subPluginId) == true) {
                val instance = try {
                    pool.acquire(plugin.getName())
                } catch (e: PluginBusyException) {
                    return "Error: ${e.message}"
                }
                try {
                    return instance.execute(proxy, args + mapOf("subPluginId" to subPluginId))
                } finally {
                    pool.release(plugin.getName(), instance)
                }
            }
        }
        return "Error: 未找到子插件 $subPluginId"
    }
}