package com.java.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 应用级插件宿主。
 *
 * 持有 PluginManager 与全部 UI 状态，生命周期跟随进程而非 Activity，
 * 因此旋转屏幕 / 重建界面不会中断后台插件。同时负责保活服务的启停。
 */
class PluginHost private constructor(private val appContext: Context) {

    data class ExecutionResult(val pluginName: String, val output: String)

    companion object {
        @Volatile
        private var instance: PluginHost? = null

        fun get(context: Context): PluginHost =
            instance ?: synchronized(this) {
                instance ?: PluginHost(context.applicationContext).also { instance = it }
            }
    }

    private val manager = PluginManager(appContext)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: StateFlow<List<Plugin>> = _plugins

    private val _runningBackground = MutableStateFlow<Set<String>>(emptySet())
    val runningBackground: StateFlow<Set<String>> = _runningBackground

    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    val pinned: StateFlow<Set<String>> = _pinned

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _executing = MutableStateFlow<String?>(null)
    val executing: StateFlow<String?> = _executing

    private val _result = MutableStateFlow<ExecutionResult?>(null)
    val result: StateFlow<ExecutionResult?> = _result

    private var started = false

    init {
        manager.onError = { message -> scope.launch { _error.value = message } }
        manager.onBackgroundPluginsChanged = { running ->
            _runningBackground.value = running
            updateKeepAliveService(running)
        }
    }

    /** 进程内只初始化一次。 */
    fun start() {
        if (started) return
        started = true
        refresh()
    }

    fun refresh() = scope.launch {
        _loading.value = true
        _error.value = null
        manager.reloadPlugins()
        _plugins.value = manager.getLoadedPlugins()
        _loading.value = false
    }

    fun install(uri: Uri) = scope.launch {
        _loading.value = true
        _error.value = null
        val error = manager.installPluginFromUri(uri)
        if (error != null) _error.value = error
        _plugins.value = manager.getLoadedPlugins()
        _loading.value = false
    }

    fun uninstall(name: String) = scope.launch {
        _error.value = null
        val error = manager.uninstallPlugin(name)
        if (error != null) _error.value = error
        _pinned.value = _pinned.value - name
        _plugins.value = manager.getLoadedPlugins()
    }

    fun execute(name: String) = scope.launch {
        if (_executing.value != null) return@launch
        _executing.value = name
        val output = manager.executePlugin(name)
        _executing.value = null
        _result.value = ExecutionResult(name, output)
    }

    fun consumeResult() {
        _result.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun startBackground(name: String) {
        manager.startBackgroundPlugin(name)
    }

    fun stopBackground(name: String) {
        manager.stopBackgroundPlugin(name)
    }

    fun togglePin(name: String) {
        _pinned.value =
            if (name in _pinned.value) _pinned.value - name else _pinned.value + name
    }

    fun isShizukuAvailable(): Boolean = manager.isShizukuAvailable()

    fun checkShizukuPermission(): Boolean = manager.checkShizukuPermission()

    fun requestShizukuPermission() = manager.requestShizukuPermission()

    fun shutdown() = manager.shutdown()

    /**
     * 有后台插件运行时拉起前台服务保活；全部停止后关闭服务。
     */
    private fun updateKeepAliveService(running: Set<String>) {
        val intent = Intent(appContext, PluginHostService::class.java)
        if (running.isEmpty()) {
            appContext.stopService(intent)
        } else {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }
}