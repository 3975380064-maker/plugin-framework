package com.java.myapplication

import java.io.InputStream

/**
 * META-INF/plugin.properties 的解析。
 * 独立成对象，不依赖 Android 运行时，便于单元测试。
 */
object PluginProperties {

    const val ENTRY_NAME = "META-INF/plugin.properties"

    /**
     * 解析 `key=value` 行，忽略空行、注释与不含 '=' 的行。
     * 不关闭传入的流（由调用方负责）。
     */
    fun parse(input: InputStream): Map<String, String> {
        val result = mutableMapOf<String, String>()
        input.bufferedReader().lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    /** subPlugins 是逗号分隔的 ID 列表。 */
    fun parseSubPlugins(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}