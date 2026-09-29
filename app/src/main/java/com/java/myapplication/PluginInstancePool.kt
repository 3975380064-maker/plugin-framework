package com.java.myapplication

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 插件实例池。
 * 并发调用同一插件时提供独立实例，空闲实例超时自动回收。
 * 所有临界区都不等待：池满时释放锁后重试，避免调用方互锁。
 */
class PluginInstancePool(
    private val loader: PluginLoader,
    private val maxClones: Int = 5,
    private val idleTimeoutMs: Long = 30_000L,
    private val pollIntervalMs: Long = 50L
) {

    private data class CloneEntry(
        val plugin: Plugin,
        var busy: Boolean = false,
        var lastUsed: Long = System.currentTimeMillis()
    )

    private val clones = mutableMapOf<String, MutableList<CloneEntry>>()
    private val mutex = Mutex()

    /** 获取一个可用实例；池满时挂起等待，直到有空闲实例。 */
    suspend fun acquire(pluginName: String): Plugin {
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
}