package com.java.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellArgsTest {

    @Test
    fun `包名只接受字母数字点下划线短横线`() {
        assertTrue(ShellArgs.isValidPackageName("com.example.app"))
        assertTrue(ShellArgs.isValidPackageName("a"))
        assertFalse(ShellArgs.isValidPackageName(""))
        assertFalse(ShellArgs.isValidPackageName("com.example.app; rm -rf /"))
        assertFalse(ShellArgs.isValidPackageName("com.example/app"))
        assertFalse(ShellArgs.isValidPackageName("com.example app"))
    }

    @Test
    fun `属性名禁止 shell 元字符`() {
        assertTrue(ShellArgs.isValidPropName("ro.product.model"))
        assertFalse(ShellArgs.isValidPropName("ro.product.model;id"))
        assertFalse(ShellArgs.isValidPropName("a b"))
        assertFalse(ShellArgs.isValidPropName(""))
    }

    @Test
    fun `namespace 只接受三个白名单值`() {
        assertTrue(ShellArgs.isValidNamespace("system"))
        assertTrue(ShellArgs.isValidNamespace("secure"))
        assertTrue(ShellArgs.isValidNamespace("global"))
        assertFalse(ShellArgs.isValidNamespace("System"))
        assertFalse(ShellArgs.isValidNamespace("settings"))
        assertFalse(ShellArgs.isValidNamespace(""))
    }

    @Test
    fun `设置值拒绝元字符与换行`() {
        assertTrue(ShellArgs.isValidSettingValue("128"))
        assertTrue(ShellArgs.isValidSettingValue("hello-world_1.2"))
        assertFalse(ShellArgs.isValidSettingValue("1; rm -rf /"))
        assertFalse(ShellArgs.isValidSettingValue("a`id`"))
        assertFalse(ShellArgs.isValidSettingValue("a\nb"))
        assertFalse(ShellArgs.isValidSettingValue("\$(id)"))
        assertFalse(ShellArgs.isValidSettingValue("a|b"))
    }

    @Test
    fun `apk 路径白名单`() {
        assertTrue(ShellArgs.isValidApkPath("/sdcard/Download/app-debug.apk"))
        assertTrue(ShellArgs.isValidApkPath("/data/local/tmp/a.apk"))
        assertFalse(ShellArgs.isValidApkPath("/sdcard/Download/a b.apk"))
        assertFalse(ShellArgs.isValidApkPath("/sdcard/a;rm"))
        assertFalse(ShellArgs.isValidApkPath(""))
    }

    @Test
    fun `activity 名白名单`() {
        assertTrue(ShellArgs.isValidActivityName("com.example.MainActivity"))
        assertFalse(ShellArgs.isValidActivityName("com.example.MainActivity;id"))
        assertFalse(ShellArgs.isValidActivityName(""))
    }
}