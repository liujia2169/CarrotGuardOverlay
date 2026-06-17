package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;

public class GameCheater {
    private static final String PKG = "com.feiyu.luobo4.nearme.gamecenter";
    private final MemoryEditor editor = new MemoryEditor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback cb;

    public interface Callback {
        void onStatus(String s);
        void onDone(String s);
        void onErr(String s);
    }

    public void setCallback(Callback cb) { this.cb = cb; }

    public void modifyCoins(int current, int target) {
        if (cb == null) return;
        cb.onStatus("修改金币:" + current + "->" + target);
        editor.modify(PKG, current, target, new MemoryEditor.Callback() {
            public void onSuccess(String m) { cb.onDone(m); }
            public void onError(String e) { cb.onErr(e); }
            public void onProgress(String s) { cb.onStatus(s); }
        });
    }

    public void modifyDiamonds(int current, int target) {
        if (cb == null) return;
        cb.onStatus("修改钻石:" + current + "->" + target);
        editor.modify(PKG, current, target, new MemoryEditor.Callback() {
            public void onSuccess(String m) { cb.onDone(m); }
            public void onError(String e) { cb.onErr(e); }
            public void onProgress(String s) { cb.onStatus(s); }
        });
    }
}
