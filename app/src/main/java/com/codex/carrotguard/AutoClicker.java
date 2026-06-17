package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;

public class AutoClicker {
    private static final String TAG = "AutoClicker";
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String message);
        void onError(String error);
    }

    public void click(int x, int y, Callback callback) {
        executeRootCommand("input tap " + x + " " + y, callback);
    }

    public void swipe(int x1, int y1, int x2, int y2, int duration, Callback callback) {
        executeRootCommand("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration, callback);
    }

    public void launchApp(String packageName, Callback callback) {
        executeRootCommand("monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1", callback);
    }

    public void killApp(String packageName, Callback callback) {
        executeRootCommand("am force-stop " + packageName, callback);
    }

    private void executeRootCommand(String command, Callback callback) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes(command + "\n");
                os.writeBytes("exit\n");
                os.flush();
                int exitCode = process.waitFor();

                handler.post(() -> {
                    if (exitCode == 0) {
                        if (callback != null) callback.onSuccess("执行成功: " + command);
                    } else {
                        if (callback != null) callback.onError("执行失败，退出码: " + exitCode);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Root command failed", e);
                handler.post(() -> {
                    if (callback != null) callback.onError("Root权限错误: " + e.getMessage());
                });
            }
        }).start();
    }

    public boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su -c id");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
