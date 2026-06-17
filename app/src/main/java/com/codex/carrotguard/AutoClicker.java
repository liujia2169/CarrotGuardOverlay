package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;
import java.io.DataOutputStream;

public class AutoClicker {
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String msg);
        void onError(String err);
    }

    public void click(int x, int y, Callback cb) {
        exec("input tap " + x + " " + y, cb);
    }

    public void launch(String pkg, Callback cb) {
        exec("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1", cb);
    }

    public boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void exec(String cmd, Callback cb) {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes(cmd + "\nexit\n");
                os.flush();
                int code = p.waitFor();
                handler.post(() -> {
                    if (code == 0) cb.onSuccess("OK");
                    else cb.onError("失败:" + code);
                });
            } catch (Exception e) {
                handler.post(() -> cb.onError(e.getMessage()));
            }
        }).start();
    }
}
