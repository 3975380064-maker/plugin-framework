package com.java.myapplication

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 解析插件文件的展示名与安全文件名。
 */
object PluginUriResolver {

    /** 从 content/file URI 取展示用文件名，取不到返回 null。 */
    fun displayName(context: Context, uri: Uri): String? {
        var fileName: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = cursor.getString(idx)
                }
            }
        }

        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }

        return fileName
    }

    /** 取可安全落盘的文件名；含路径分隔符或 .. 时返回 null。 */
    fun safeFileName(context: Context, uri: Uri): String? {
        val name = displayName(context, uri) ?: return null
        if (name.contains("/") || name.contains("..")) return null
        return name
    }
}