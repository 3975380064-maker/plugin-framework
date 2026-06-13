package com.plugin.clipstack;

import com.java.myapplication.Plugin;
import com.java.myapplication.ShizukuProxy;
import java.util.Map;
import android.util.Log;

/**
 * ClipStack 管理插件
 * 自动执行权限授予 + 重启剪贴板服务
 * 
 * 目标应用: com.catchingnow.tinyclipboardmanager
 * 权限: SYSTEM_ALERT_WINDOW, READ_LOGS
 */
public class ClipStackPlugin implements Plugin {

    private static final String TAG = "ClipStackPlugin";
    private static final String PKG = "com.catchingnow.tinyclipboardmanager";

    @Override
    public String getName() { return "ClipStack 管理"; }

    @Override
    public String getDescription() { return "自动授权悬浮窗/日志权限并管理剪贴板服务"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public boolean needsShizuku() { return true; }

    @Override
    public String execute(ShizukuProxy proxy, Map<String, ?> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ClipStack 自动管理 ===\n\n");

        sb.append("[1/3] 授予悬浮窗权限...\n");
        String r1 = proxy.execCommand(
            "appops set " + PKG + " SYSTEM_ALERT_WINDOW allow"
        );
        sb.append("  → ").append(r1).append("\n\n");

        sb.append("[2/3] 授予日志读取权限...\n");
        String r2 = proxy.execCommand(
            "pm grant " + PKG + " android.permission.READ_LOGS"
        );
        sb.append("  → ").append(r2).append("\n\n");

        sb.append("[3/3] 重启剪贴板服务...\n");
        String r3 = proxy.execCommand(
            "am force-stop " + PKG + 
            " && sleep 1" +
            " && monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1"
        );
        sb.append("  → ").append(r3).append("\n\n");

        sb.append("✅ 完成！ClipStack 应该已恢复工作。");
        return sb.toString();
    }
}