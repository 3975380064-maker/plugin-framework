package com.java.myapplication

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 插件实例池已满且等待超时。
 */
class PluginBusyException(pluginName: String, timeoutMs: Long) :
    Exception("插件 $pluginName 的实例池已满，等待 ${timeoutMs}ms 仍未释放")

/**
 * 插件实例池。
 * 并发调用同一插件时提供独立实例，空闲实例超时回收。
 * 所有临界区都不等待：池满时释放锁后重试，避免调用方互锁。
 */
class PluginInstancePool(
    private val loader: PluginLoader,
    private val maxClones: Int = 5,
    private val idleTimeoutMs: Long = 30_000L,
    private val pollIntervalMs: Long = 50L,
    private val acquireTimeoutMs: Long = 15_000L
) {

    private data class CloneEntry(
        val plugin: Plugin,
        var busy: Boolean = false,
        var lastUsed: Long = System.currentTimeMillis()
    )

    private val clones = mutableMapOf<String, MutableList<CloneEntry>>()
    private val mutex = Mutex()

    /**
     * 获取一个可用实例。
     * 池满时挂起等待；超过 [acquireTimeoutMs] 抛 [PluginBusyException]，
     * 避免插件卡死导致调用方无限等待。
     */
    suspend fun acquire(pluginName: String): Plugin {
        val deadline = System.currentTimeMillis() + acquireTimeoutMs
        while (true) {
            val acquired = mutex.withLock {
                val list = clones.getOrPut(pluginName) { mutableListOf() }
                val idle = list.firstOrNull { !it.busy }
                when {
                    idle != null -> {
                        idle.busy = true
                        idle.lastUsed = System.currentTimeMillis()
                        idle.plugin
                    }
                    list.size < maxClones -> {
                        val original = loader.getPlugin(pluginName)
                            ?: throw IllegalStateException("插件 $pluginName 未加载")
                        val cloned = original.javaClass.getDeclaredConstructor().newInstance() as Plugin
                        list.add(CloneEntry(plugin = cloned, busy = true))
                        cloned
                    }
                    else -> null
                }
            }
            if (acquired != null) return acquired
            if (System.currentTimeMillis() >= deadline) {
                throw PluginBusyException(pluginName, acquireTimeoutMs)
            }
            delay(pollIntervalMs)
        }
    }

    /** 归还实例。 */
    suspend fun release(pluginName: String, plugin: Plugin) {
        mutex.withLock {
            clones[pluginName]?.find { it.plugin === plugin }?.busy = false
        }
    }

    /** 回收超过空闲阈值的实例。 */
    suspend fun evictIdle() {
        val now = System.currentTimeMillis()
        mutex.withLock {
            clones.values.forEach { list ->
                list.removeAll { !it.busy && (now - it.lastUsed) > idleTimeoutMs }
            }
        }
    }

    /** 丢弃某插件的全部实例（卸载/重载时调用，避免旧 ClassLoader 的孤儿实例）。 */
    suspend fun discard(pluginName: String) {
        mutex.withLock {
            clones.remove(pluginName)
        }
    }

    /** 丢弃全部实例。 */
    suspend fun discardAll() {
        mutex.withLock {
            clones.clear()
        }
    }
}