package com.java.myapplication

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 插件的文件级安装与卸载。
 */
class PluginInstaller(private val context: Context, private val loader: PluginLoader) {

    private val pluginDir: File
        get() = File(context.filesDir, PluginLoader.PLUGIN_DIR).apply { mkdirs() }

    /** 将所选文件安装到插件目录。成功返回 null，失败返回错误信息。 */
    fun install(uri: Uri): String? {
        return try {
            val fileName = PluginUriResolver.safeFileName(context, uri)
                ?: return "无法获取文件名或文件名非法"

            if (!fileName.endsWith(PluginLoader.PLUGIN_EXTENSION, ignoreCase = true)) {
                return "不支持的文件格式，请上传${PluginLoader.PLUGIN_EXTENSION}文件"
            }

            val target = File(pluginDir, fileName)
            val input = context.contentResolver.openInputStream(uri)
                ?: return "无法读取所选文件"

            input.use { source ->
                FileOutputStream(target).use { sink -> source.copyTo(sink) }
            }
            null
        } catch (e: Exception) {
            "安装插件失败: ${e.message}"
        }
    }

    /** 从注册表移除插件并删除源文件。成功返回 null，失败返回错误信息。 */
    fun uninstall(pluginName: String): String? {
        return try {
            val sourceFile = loader.getPluginSourceFile(pluginName)
            if (!loader.unloadPlugin(pluginName)) {
                return "插件 $pluginName 未找到"
            }

            val target = File(pluginDir, sourceFile ?: "$pluginName${PluginLoader.PLUGIN_EXTENSION}")
            if (target.exists()) target.delete()
            null
        } catch (e: Exception) {
            "卸载插件失败: ${e.message}"
        }
    }
}