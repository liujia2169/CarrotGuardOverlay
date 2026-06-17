package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;

public class AutoSignService {
    private static final String PKG = "com.feiyu.luobo4.nearme.gamecenter";
    private final AutoClicker clicker = new AutoClicker();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback cb;
    private boolean running;
    private int w, h;
    private int signX = 85, signY = 15;
    private int confirmX = 50, confirmY = 60;

    public interface Callback {
        void onStatus(String s);
        void onDone(String s);
        void onErr(String s);
    }

    public void setSize(int w, int h) { this.w = w; this.h = h; }
    public void setSignPos(int x, int y) { signX = x; signY = y; }
    public void setConfirmPos(int x, int y) { confirmX = x; confirmY = y; }

    public void start(Callback cb) {
        if (running) { cb.onErr("已在运行"); return; }
        if (!clicker.hasRoot()) { cb.onErr("无Root"); return; }
        this.cb = cb;
        running = true;
        cb.onStatus("启动游戏...");
        clicker.launch(PKG, new AutoClicker.Callback() {
            public void onSuccess(String m) {
                cb.onStatus("等待加载...");
                handler.postDelayed(() -> doSign(), 5000);
            }
            public void onError(String e) { cb.onErr(e); running = false; }
        });
    }

    private void doSign() {
        if (!running) return;
        int x = w * signX / 100, y = h * signY / 100;
        cb.onStatus("点击签到:" + x + "," + y);
        clicker.click(x, y, new AutoClicker.Callback() {
            public void onSuccess(String m) {
                handler.postDelayed(() -> doConfirm(), 2000);
            }
            public void onError(String e) { cb.onErr(e); running = false; }
        });
    }

    private void doConfirm() {
        if (!running) return;
        int x = w * confirmX / 100, y = h * confirmY / 100;
        cb.onStatus("点击确认...");
        clicker.click(x, y, new AutoClicker.Callback() {
            public void onSuccess(String m) { cb.onDone("签到完成"); running = false; }
            public void onError(String e) { cb.onErr(e); running = false; }
        });
    }
}
