package com.java.myapplication

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPropertiesTest {

    private fun parse(text: String) =
        PluginProperties.parse(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `解析基本键值并去掉空白`() {
        val props = parse(
            """
            mainClass = com.example.MyPlugin
            uid=com.example
            """.trimIndent()
        )
        assertEquals("com.example.MyPlugin", props["mainClass"])
        assertEquals("com.example", props["uid"])
    }

    @Test
    fun `忽略空行 注释与无等号行`() {
        val props = parse(
            """
            # 注释
            mainClass=com.example.MyPlugin

            这不是键值对
            """.trimIndent()
        )
        assertEquals(1, props.size)
        assertEquals("com.example.MyPlugin", props["mainClass"])
    }

    @Test
    fun `空值不写入结果`() {
        val props = parse("mainClass=\nuid=  ")
        assertTrue(props.isEmpty())
    }

    @Test
    fun `值里可以带等号`() {
        val props = parse("description=a=b=c")
        assertEquals("a=b=c", props["description"])
    }

    @Test
    fun `subPlugins 按逗号切分并去空`() {
        assertEquals(listOf("a", "b", "c"), PluginProperties.parseSubPlugins("a, b ,c"))
        assertEquals(listOf("a"), PluginProperties.parseSubPlugins("a"))
        assertTrue(PluginProperties.parseSubPlugins("").isEmpty())
        assertTrue(PluginProperties.parseSubPlugins(null).isEmpty())
        assertTrue(PluginProperties.parseSubPlugins(" , , ").isEmpty())
    }
}