package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class GameCheater {
    private static final String TAG = "GameCheater";
    private static final String GAME_PACKAGE = "com.feiyu.luobo4.nearme.gamecenter";
    
    private final MemoryEditor memoryEditor;
    private final Handler handler;
    private CheatCallback callback;

    public interface CheatCallback {
        void onStatusUpdate(String status);
        void onComplete(String message);
        void onError(String error);
    }

    public GameCheater() {
        this.memoryEditor = new MemoryEditor();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setCallback(CheatCallback callback) {
        this.callback = callback;
    }

    public void findGameProcess() {
        memoryEditor.getGamePid(GAME_PACKAGE, new MemoryEditor.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyStatus(message);
            }

            @Override
            public void onError(String error) {
                notifyError(error);
            }

            @Override
            public void onProgress(String status) {
                notifyStatus(status);
            }
        });
    }

    public void searchGoldCoins(int currentCoins) {
        notifyStatus("正在搜索金币数值: " + currentCoins);
        memoryEditor.searchValue(GAME_PACKAGE, currentCoins, new MemoryEditor.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyStatus(message);
                notifyComplete("搜索完成，请查看状态");
            }

            @Override
            public void onError(String error) {
                notifyError(error);
            }

            @Override
            public void onProgress(String status) {
                notifyStatus(status);
            }
        });
    }

    public void modifyGoldCoins(int currentCoins, int newCoins) {
        notifyStatus("正在修改金币: " + currentCoins + " -> " + newCoins);
        memoryEditor.modifyValue(GAME_PACKAGE, currentCoins, newCoins, new MemoryEditor.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyComplete(message);
            }

            @Override
            public void onError(String error) {
                notifyError(error);
            }

            @Override
            public void onProgress(String status) {
                notifyStatus(status);
            }
        });
    }

    public void searchDiamonds(int currentDiamonds) {
        notifyStatus("正在搜索钻石数值: " + currentDiamonds);
        memoryEditor.searchValue(GAME_PACKAGE, currentDiamonds, new MemoryEditor.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyStatus(message);
                notifyComplete("搜索完成，请查看状态");
            }

            @Override
            public void onError(String error) {
                notifyError(error);
            }

            @Override
            public void onProgress(String status) {
                notifyStatus(status);
            }
        });
    }

    public void modifyDiamonds(int currentDiamonds, int newDiamonds) {
        notifyStatus("正在修改钻石: " + currentDiamonds + " -> " + newDiamonds);
        memoryEditor.modifyValue(GAME_PACKAGE, currentDiamonds, newDiamonds, new MemoryEditor.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyComplete(message);
            }

            @Override
            public void onError(String error) {
                notifyError(error);
            }

            @Override
            public void onProgress(String status) {
                notifyStatus(status);
            }
        });
    }

    public void autoDetectAndModify(int targetValue) {
        notifyStatus("自动检测并修改数值...");
        memoryEditor.modifyValue(GAME_PACKAGE, 0, targetValue, new MemoryEditor.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyComplete(message);
            }

            @Override
            public void onError(String error) {
                notifyError(error);
            }

            @Override
            public void onProgress(String status) {
                notifyStatus(status);
            }
        });
    }

    private void notifyStatus(String status) {
        handler.post(() -> {
            if (callback != null) callback.onStatusUpdate(status);
        });
    }

    private void notifyComplete(String message) {
        handler.post(() -> {
            if (callback != null) callback.onComplete(message);
        });
    }

    private void notifyError(String error) {
        handler.post(() -> {
            if (callback != null) callback.onError(error);
        });
    }
}
