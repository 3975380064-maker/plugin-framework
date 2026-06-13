package com.plugin.test;

import com.java.myapplication.Plugin;
import com.java.myapplication.ShizukuProxy;
import java.util.Map;
import android.util.Log;

/**
 * 测试插件 - 设备信息查询（带逐行日志）
 *
 * 编译打包：
 *   javac -cp <宿主classes目录> -d build TestPlugin.java
 *   jar cf TestPlugin.jar -C build .
 *   再在 JAR 中加入 META-INF/plugin.properties (mainClass=com.plugin.test.TestPlugin)
 */
public class TestPlugin implements Plugin {

    private static final String TAG = "TestPlugin";

    @Override
    public String getName() {
        Log.d(TAG, "getName() called");
        return "设备信息查询插件";
    }

    @Override
    public String getDescription() {
        Log.d(TAG, "getDescription() called");
        return "查询设备基本信息，包括型号、系统版本等";
    }

    @Override
    public String getVersion() {
        Log.d(TAG, "getVersion() called");
        return "1.0";
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
        result.append("=== 设备信息查询插件 v").append(getVersion()).append(" ===\n\n");
        log.append("========== execute() 开始 ==========\n");

        try {
            log.append("[1/7] 调用 proxy.getProp(\"ro.product.model\") ...\n");
            String model = proxy.getProp("ro.product.model");
            log.append("[1/7] 返回: ").append(model).append("\n");
            result.append("\uD83D\uDCF1 设备型号: ").append(model).append("\n");

            log.append("[2/7] 调用 proxy.getProp(\"ro.product.brand\") ...\n");
            String brand = proxy.getProp("ro.product.brand");
            log.append("[2/7] 返回: ").append(brand).append("\n");
            result.append("\uD83C\uDFED 品牌: ").append(brand).append("\n");

            log.append("[3/7] 调用 proxy.getProp(\"ro.build.version.release\") ...\n");
            String androidVersion = proxy.getProp("ro.build.version.release");
            log.append("[3/7] 返回: ").append(androidVersion).append("\n");
            result.append("\uD83E\uDD16 Android版本: ").append(androidVersion).append("\n");

            log.append("[4/7] 调用 proxy.getProp(\"ro.build.version.sdk\") ...\n");
            String sdkVersion = proxy.getProp("ro.build.version.sdk");
            log.append("[4/7] 返回: ").append(sdkVersion).append("\n");
            result.append("\uD83D\uDCE6 SDK版本: ").append(sdkVersion).append("\n");

            log.append("[5/7] 调用 proxy.getProp(\"ro.build.display.id\") ...\n");
            String display = proxy.getProp("ro.build.display.id");
            log.append("[5/7] 返回: ").append(display).append("\n");
            result.append("\uD83D\uDD28 系统版本: ").append(display).append("\n\n");

            log.append("[6/7] 调用 proxy.execCommand(\"dumpsys battery | grep level\") ...\n");
            String batteryLevel = proxy.execCommand("dumpsys battery | grep level");
            log.append("[6/7] 返回: ").append(batteryLevel).append("\n");
            result.append("\uD83D\uDD0B 电池信息:\n").append(batteryLevel).append("\n\n");

            log.append("[7/7] 调用 proxy.execCommand(\"df -h /data\") ...\n");
            String storage = proxy.execCommand("df -h /data");
            log.append("[7/7] 返回: ").append(storage).append("\n");
            result.append("\uD83D\uDCBE 存储信息:\n").append(storage).append("\n\n");

            result.append("✅ 插件执行完成！\n\n[日志已写入 /sdcard/Documents/plugin.log]");
            log.append("========== execute() 成功结束 ==========\n");

        } catch (Exception e) {
            log.append("❌ 执行出错: ").append(e.getMessage()).append("\n");
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            log.append(sw.toString());
            result.append("❌ 执行出错: ").append(e.getMessage());
        }

        // 写文件
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/sdcard/Documents/plugin.log");
            fw.write(log.toString());
            fw.close();
        } catch (Exception ignored) {}

        return result.toString();
    }
}
