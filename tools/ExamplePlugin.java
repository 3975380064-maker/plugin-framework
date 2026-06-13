package com.plugin;

import com.java.myapplication.Plugin;
import com.java.myapplication.ShizukuProxy;
import java.util.Map;
import android.util.Log;

/**
 * 示例插件 - 显示设备信息（带逐行日志）
 */
public class ExamplePlugin implements Plugin {

    private static final String TAG = "ExamplePlugin";

    @Override
    public String getName() {
        Log.d(TAG, "getName() called");
        return "ExamplePlugin";
    }

    @Override
    public String getDescription() {
        Log.d(TAG, "getDescription() called");
        return "显示设备信息的示例插件";
    }

    @Override
    public String getVersion() {
        Log.d(TAG, "getVersion() called");
        return "1.0.0";
    }

    @Override
    public boolean needsShizuku() {
        Log.d(TAG, "needsShizuku() called -> true");
        return true;
    }

    @Override
    public String execute(ShizukuProxy proxy, Map<String, ?> args) {
        StringBuilder log = new StringBuilder();
        StringBuilder result = new StringBuilder();
        result.append("=== 设备信息 ===\n");
        log.append("========== execute() 开始 ==========\n");

        log.append("[1/7] 调用 proxy.getProp(\"ro.product.brand\") ...\n");
        String brand = proxy.getProp("ro.product.brand");
        log.append("[1/7] 返回: ").append(brand).append("\n");
        result.append("品牌: ").append(brand).append("\n");

        log.append("[2/7] 调用 proxy.getProp(\"ro.product.model\") ...\n");
        String model = proxy.getProp("ro.product.model");
        log.append("[2/7] 返回: ").append(model).append("\n");
        result.append("型号: ").append(model).append("\n");

        log.append("[3/7] 调用 proxy.getProp(\"ro.build.version.release\") ...\n");
        String release = proxy.getProp("ro.build.version.release");
        log.append("[3/7] 返回: ").append(release).append("\n");
        result.append("Android版本: ").append(release).append("\n");

        log.append("[4/7] 调用 proxy.getProp(\"ro.build.version.sdk\") ...\n");
        String sdk = proxy.getProp("ro.build.version.sdk");
        log.append("[4/7] 返回: ").append(sdk).append("\n");
        result.append("SDK版本: ").append(sdk).append("\n");

        log.append("[5/7] 调用 proxy.execCommand(\"wm size\") ...\n");
        String wmSize = proxy.execCommand("wm size");
        log.append("[5/7] 返回: ").append(wmSize).append("\n");
        result.append("\n=== 屏幕信息 ===\n");
        result.append("分辨率: ").append(wmSize).append("\n");

        log.append("[6/7] 调用 proxy.execCommand(\"dumpsys battery\") ...\n");
        String battery = proxy.execCommand("dumpsys battery");
        log.append("[6/7] 返回: ").append(battery).append("\n");
        result.append("\n=== 电池信息 ===\n");
        result.append(battery).append("\n");

        log.append("[7/7] 调用 proxy.execCommand(\"df -h /data\") ...\n");
        String storage = proxy.execCommand("df -h /data");
        log.append("[7/7] 返回: ").append(storage).append("\n");
        result.append("\n=== 存储信息 ===\n");
        result.append(storage).append("\n");

        result.append("\n[日志已写入 /sdcard/Documents/plugin.log]");
        log.append("========== execute() 结束 ==========\n");

        try {
            java.io.FileWriter fw = new java.io.FileWriter("/sdcard/Documents/plugin.log");
            fw.write(log.toString());
            fw.close();
        } catch (Exception ignored) {}

        return result.toString();
    }
}