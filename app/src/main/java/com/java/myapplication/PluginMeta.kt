package com.java.myapplication

/**
 * 插件元数据，从 META-INF/plugin.properties 解析
 */
data class PluginMeta(
    val mainClass: String,
    val uid: String = "",
    val version: String = "",
    val description: String = "",
    val subPlugins: List<String> = emptyList()  // 逗号分隔的子插件 ID 列表
)