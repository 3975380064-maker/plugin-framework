package com.java.myapplication

import kotlinx.coroutines.CoroutineScope

/**
 * 插件接口
 * 所有插件都需要实现这个接口
 */
interface Plugin {
    fun getName(): String
    fun getDescription(): String
    fun getVersion(): String
    fun execute(proxy: ShizukuProxy, args: Map<String, Any>? = null): String
    fun needsShizuku(): Boolean = true
}

/**
 * 后台长期运行插件
 * 实现此接口的插件将出现在"长期运行"Tab 中
 */
interface BackgroundPlugin : Plugin {
    /**
     * 长期运行入口，在独立协程中执行
     * @param proxy Shizuku 代理
     * @param scope 宿主管理的协程作用域，宿主取消时会自动停止
     * @param dispatcher 子插件调度器，用于调用执行子插件
     */
    suspend fun runInBackground(
        proxy: ShizukuProxy,
        scope: CoroutineScope,
        dispatcher: SubPluginDispatcher
    )
}

/**
 * 子插件调度器 — 使 BackgroundPlugin 能调用执行子插件
 */
interface SubPluginDispatcher {
    /** 调用指定子插件 ID，返回执行结果 */
    suspend fun call(subPluginId: String, args: Map<String, String> = emptyMap()): String
}