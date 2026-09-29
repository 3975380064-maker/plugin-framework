package com.java.myapplication

/**
 * ShizukuProxy 的参数校验规则。
 * 独立成对象，不依赖 Android 运行时，便于单元测试。
 */
object ShellArgs {

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")
    private val PROP_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")
    private val SETTINGS_KEY_REGEX = Regex("^[a-zA-Z0-9._-]+$")
    private val ACTIVITY_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")
    private val APK_PATH_REGEX = Regex("^[a-zA-Z0-9._/\\-]+$")

    private val SHELL_METACHARS =
        charArrayOf(';', '&', '|', '$', '`', '\'', '"', '\n', '\r', '<', '>', '(', ')', '{', '}')

    val SETTINGS_NAMESPACES = setOf("system", "secure", "global")

    fun isValidPackageName(value: String): Boolean = PACKAGE_NAME_REGEX.matches(value)

    fun isValidPropName(value: String): Boolean = PROP_NAME_REGEX.matches(value)

    fun isValidSettingsKey(value: String): Boolean = SETTINGS_KEY_REGEX.matches(value)

    fun isValidActivityName(value: String): Boolean = ACTIVITY_NAME_REGEX.matches(value)

    fun isValidApkPath(value: String): Boolean = APK_PATH_REGEX.matches(value)

    fun isValidNamespace(value: String): Boolean = value in SETTINGS_NAMESPACES

    fun isValidSettingValue(value: String): Boolean = value.none { it in SHELL_METACHARS }
}