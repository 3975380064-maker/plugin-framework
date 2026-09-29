package com.java.myapplication

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * 插件加载器。
 * 扫描插件目录，按 META-INF/plugin.properties 的 mainClass 加载 .jar（内含 classes.dex）。
 */
class PluginLoader(private val context: Context) {

    companion object {
        const val PLUGIN_DIR = "plugins"
        const val PLUGIN_EXTENSION = ".jar"
        private const val TAG = "PluginLoader"
    }

    private val loadedPlugins = ConcurrentHashMap<String, Plugin>()
    private val pluginSourceFiles = ConcurrentHashMap<String, String>()
    private val pluginMetas = ConcurrentHashMap<String, PluginMeta>()

    private fun getPluginDir(): File = File(context.filesDir, PLUGIN_DIR).apply { mkdirs() }

    /** 扫描并加载插件目录中的全部插件。 */
    fun loadAllPlugins(): List<Plugin> {
        val pluginFiles = getPluginDir().listFiles { _, name ->
            name.endsWith(PLUGIN_EXTENSION, ignoreCase = true)
        }
        if (pluginFiles.isNullOrEmpty()) {
            android.util.Log.w(TAG, "插件目录中没有找到 $PLUGIN_EXTENSION 文件")
            return emptyList()
        }

        pluginFiles.forEach { file ->
            try {
                val plugin = loadPlugin(file, readPluginProperties(file))
                if (plugin == null) {
                    android.util.Log.w(TAG, "插件加载失败: ${file.name}")
                    return@forEach
                }
                val name = plugin.getName()
                val props = readPluginProperties(file)
                loadedPlugins[name] = plugin
                pluginSourceFiles[name] = file.name
                pluginMetas[name] = buildMeta(props, plugin)
                android.util.Log.i(TAG, "成功加载插件: $name (文件: ${file.name})")
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "加载插件 ${file.name} 时发生异常", e)
            }
        }
        android.util.Log.i(TAG, "插件加载完成，共加载 ${loadedPlugins.size} 个插件")
        return loadedPlugins.values.toList()
    }

    private fun buildMeta(props: Map<String, String>, plugin: Plugin) = PluginMeta(
        mainClass = props["mainClass"]?.trim().orEmpty(),
        uid = props["uid"]?.trim().orEmpty(),
        version = props["version"]?.trim() ?: plugin.getVersion(),
        description = props["description"]?.trim() ?: plugin.getDescription(),
        subPlugins = PluginProperties.parseSubPlugins(props["subPlugins"])
    )

    /**
     * 加载单个插件文件。
     * 插件类必须 public 且有无参构造函数（这里用 getDeclaredConstructor().newInstance()）。
     */
    private fun loadPlugin(file: File, props: Map<String, String>): Plugin? {
        val mainClass = props["mainClass"]?.trim()
        if (mainClass.isNullOrBlank()) {
            android.util.Log.w(TAG, "插件 ${file.name} 缺少 mainClass 声明，跳过")
            return null
        }
        return try {
            val optimizedDir = File(
                context.cacheDir,
                "optimized_plugins/${file.nameWithoutExtension}_${System.currentTimeMillis()}"
            ).apply { mkdirs() }

            val classLoader = DexClassLoader(
                file.absolutePath,
                optimizedDir.absolutePath,
                null,
                Plugin::class.java.classLoader
            )
            val instance = classLoader.loadClass(mainClass)
                .getDeclaredConstructor()
                .newInstance()

            val plugin = instance as? Plugin
            if (plugin == null) {
                android.util.Log.w(TAG, "类 $mainClass 未实现 Plugin 接口")
            }
            plugin
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "加载插件异常: ${file.name}", e)
            null
        }
    }

    private fun readPluginProperties(file: File): Map<String, String> {
        if (!file.name.endsWith(PLUGIN_EXTENSION, ignoreCase = true)) return emptyMap()
        return try {
            JarFile(file).use { jar ->
                val entry = jar.getJarEntry(PluginProperties.ENTRY_NAME) ?: return emptyMap()
                PluginProperties.parse(jar.getInputStream(entry))
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun getLoadedPlugins(): List<Plugin> = loadedPlugins.values.toList()

    fun getPlugin(name: String): Plugin? = loadedPlugins[name]

    fun unloadPlugin(name: String): Boolean {
        pluginMetas.remove(name)
        pluginSourceFiles.remove(name)
        return loadedPlugins.remove(name) != null
    }

    fun reloadPlugins(): List<Plugin> {
        loadedPlugins.clear()
        pluginSourceFiles.clear()
        pluginMetas.clear()
        return loadAllPlugins()
    }

    fun getPluginSourceFile(name: String): String? = pluginSourceFiles[name]

    fun getPluginMeta(name: String): PluginMeta? = pluginMetas[name]
}