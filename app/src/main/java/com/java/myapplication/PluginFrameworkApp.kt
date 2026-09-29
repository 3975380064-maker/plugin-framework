package com.java.myapplication

import android.app.Application

/**
 * 进程入口：尽早建立应用级 [PluginHost]，
 * 使插件列表与后台插件不依赖 Activity 生命周期。
 */
class PluginFrameworkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PluginHost.get(this).start()
    }
}