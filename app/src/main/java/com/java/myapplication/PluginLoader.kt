package com.java.myapplication

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * 插件加载器
 * 负责动态加载.jar文件中的插件
 * 强制要求 META-INF/plugin.properties 声明 mainClass
 */
class PluginLoader(private val context: Context) {
    
    companion object {
        private const val PLUGIN_DIR = "plugins"
        private val PLUGIN_EXTENSIONS = arrayOf(".jar", ".dex")
    }
    
    // 已加载的插件（线程安全）
    private val loadedPlugins = ConcurrentHashMap<String, Plugin>()
    
    // 插件名 → 源文件名 映射（线程安全），用于卸载时定位文件
    private val pluginSourceFiles = ConcurrentHashMap<String, String>()
    
    // 插件元数据缓存：插件名 → PluginMeta
    private val pluginMetas = ConcurrentHashMap<String, PluginMeta>()
    
    /**
     * 获取插件目录
     * 使用内部存储避免权限问题
     */
    private fun getPluginDir(): File {
        val dir = File(context.filesDir, PLUGIN_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 扫描并加载所有插件
     */
    fun loadAllPlugins(): List<Plugin> {
        android.util.Log.d("PluginLoader", "开始扫描插件目录...")
        val pluginDir = getPluginDir()
        val pluginFiles = pluginDir.listFiles { _, name ->
            PLUGIN_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
        }
        
        if (pluginFiles == null || pluginFiles.isEmpty()) {
            android.util.Log.w("PluginLoader", "插件目录中没有找到.jar或.dex文件")
            return emptyList()
        }
        
        android.util.Log.i("PluginLoader", "找到 ${pluginFiles.size} 个插件文件")
        
        pluginFiles.forEach { file ->
            android.util.Log.d("PluginLoader", "尝试加载插件: ${file.name}")
            try {
                val props = readPluginProperties(file)
                val plugin = loadPlugin(file, props)
                if (plugin != null) {
                    val name = plugin.getName()
                    loadedPlugins[name] = plugin
                    pluginSourceFiles[name] = file.name
                    // 构建并缓存元数据
                    pluginMetas[name] = PluginMeta(
                        mainClass = props["mainClass"]?.trim() ?: "",
                        uid = props["uid"]?.trim() ?: "",
                        version = props["version"]?.trim() ?: plugin.getVersion(),
                        description = props["description"]?.trim() ?: plugin.getDescription(),
                        subPlugins = props["subPlugins"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    )
                    android.util.Log.i("PluginLoader", "成功加载插件: $name (文件: ${file.name})")
                } else {
                    android.util.Log.w("PluginLoader", "插件加载失败: ${file.name}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PluginLoader", "加载插件 ${file.name} 时发生异常", e)
            }
        }
        
        android.util.Log.i("PluginLoader", "插件加载完成，共加载 ${loadedPlugins.size} 个插件")
        return loadedPlugins.values.toList()
    }
    
    /**
     * 加载单个插件文件
     * 强制要求 META-INF/plugin.properties 声明 mainClass，不再猜类名
     */
    private fun loadPlugin(file: File, props: Map<String, String>): Plugin? {
        val mainClass = props["mainClass"]?.trim()
        if (mainClass.isNullOrBlank()) {
            android.util.Log.w("PluginLoader", "插件 ${file.name} 缺少 META-INF/plugin.properties 中的 mainClass 声明，跳过")
            return null
        }
        return try {
            val optimizedDir = File(context.cacheDir, "optimized_plugins/${file.nameWithoutExtension}_${System.currentTimeMillis()}")
            optimizedDir.mkdirs()
            val classLoader = DexClassLoader(
                file.absolutePath,
                optimizedDir.absolutePath,
                null,
                Plugin::class.java.classLoader
            )
            val clazz = classLoader.loadClass(mainClass)
            val instance = clazz.getDeclaredConstructor().newInstance()
            if (instance is Plugin) {
                instance
            } else {
                android.util.Log.w("PluginLoader", "类 $mainClass 未实现 Plugin 接口")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PluginLoader", "加载插件异常: ${file.name}", e)
            null
        }
    }
    
    /**
     * 统一解析 META-INF/plugin.properties
     * 支持：mainClass, uid, version, description, subPlugins
     */
    private fun readPluginProperties(file: File): Map<String, String> {
        if (!file.name.endsWith(".jar", ignoreCase = true)) return emptyMap()
        return try {
            JarFile(file).use { jar ->
                val entry = jar.getJarEntry("META-INF/plugin.properties") ?: return emptyMap()
                val result = mutableMapOf<String, String>()
                jar.getInputStream(entry).bufferedReader().use { reader ->
                    reader.lines().forEach { line ->
                        val trimmed = line.trim()
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) {
                            val key = trimmed.substring(0, eq).trim()
                            val value = trimmed.substring(eq + 1).trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                result[key] = value
                            }
                        }
                    }
                }
                return result
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 获取所有已加载的插件
     */
    fun getLoadedPlugins(): List<Plugin> {
        return loadedPlugins.values.toList()
    }
    
    /**
     * 获取指定名称的插件
     */
    fun getPlugin(name: String): Plugin? {
        return loadedPlugins[name]
    }
    
    /**
     * 卸载插件
     */
    fun unloadPlugin(name: String): Boolean {
        pluginMetas.remove(name)
        pluginSourceFiles.remove(name)
        return loadedPlugins.remove(name) != null
    }
    
    /**
     * 重新加载插件
     */
    fun reloadPlugins(): List<Plugin> {
        loadedPlugins.clear()
        pluginSourceFiles.clear()
        pluginMetas.clear()
        return loadAllPlugins()
    }
    
    /**
     * 获取插件对应的源文件名
     */
    fun getPluginSourceFile(name: String): String? {
        return pluginSourceFiles[name]
    }
    
    /**
     * 获取插件元数据
     */
    fun getPluginMeta(name: String): PluginMeta? {
        return pluginMetas[name]
    }
}