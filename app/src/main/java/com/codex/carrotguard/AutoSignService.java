package com.codex.carrotguard;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class AutoSignService {
    private static final String TAG = "AutoSignService";
    private static final String GAME_PACKAGE = "com.feiyu.luobo4.nearme.gamecenter";
    
    private final AutoClicker clicker;
    private final Handler handler;
    private AutoSignCallback callback;
    private boolean isRunning = false;
    private int screenWidth;
    private int screenHeight;
    
    private int signButtonXPercent = 85;
    private int signButtonYPercent = 15;
    private int confirmButtonXPercent = 50;
    private int confirmButtonYPercent = 60;

    public interface AutoSignCallback {
        void onStatusUpdate(String status);
        void onComplete(String message);
        void onError(String error);
    }

    public AutoSignService() {
        this.clicker = new AutoClicker();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void setSignButtonPosition(int xPercent, int yPercent) {
        this.signButtonXPercent = xPercent;
        this.signButtonYPercent = yPercent;
    }

    public void setConfirmButtonPosition(int xPercent, int yPercent) {
        this.confirmButtonXPercent = xPercent;
        this.confirmButtonYPercent = yPercent;
    }

    public void startAutoSign(AutoSignCallback callback) {
        if (isRunning) {
            callback.onError("签到程序已在运行");
            return;
        }

        if (!clicker.isRootAvailable()) {
            callback.onError("未获取Root权限");
            return;
        }

        this.callback = callback;
        this.isRunning = true;

        new Thread(() -> {
            try {
                executeSignFlow();
            } catch (Exception e) {
                Log.e(TAG, "Auto sign failed", e);
                notifyError("签到失败: " + e.getMessage());
            } finally {
                isRunning = false;
            }
        }).start();
    }

    public void stopAutoSign() {
        isRunning = false;
    }

    private void executeSignFlow() {
        notifyStatus("正在启动游戏...");
        clicker.launchApp(GAME_PACKAGE, new AutoClicker.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyStatus("游戏启动成功，等待加载...");
                waitForGameLoad();
            }

            @Override
            public void onError(String error) {
                notifyError("启动游戏失败: " + error);
            }
        });
    }

    private void waitForGameLoad() {
        handler.postDelayed(() -> {
            notifyStatus("正在查找签到按钮...");
            findAndClickSignButton();
        }, 5000);
    }

    private void findAndClickSignButton() {
        if (!isRunning) return;

        if (screenWidth == 0 || screenHeight == 0) {
            notifyError("屏幕尺寸未设置");
            return;
        }

        notifyStatus("使用配置的签到按钮位置...");
        
        int signX = screenWidth * signButtonXPercent / 100;
        int signY = screenHeight * signButtonYPercent / 100;

        notifyStatus("点击签到按钮: (" + signX + ", " + signY + ")");
        clicker.click(signX, signY, new AutoClicker.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyStatus("签到按钮已点击，等待确认...");
                handleSignConfirm();
            }

            @Override
            public void onError(String error) {
                notifyError("点击签到按钮失败: " + error);
            }
        });
    }

    private void handleSignConfirm() {
        handler.postDelayed(() -> {
            if (!isRunning) return;

            int confirmX = screenWidth * confirmButtonXPercent / 100;
            int confirmY = screenHeight * confirmButtonYPercent / 100;

            notifyStatus("点击确认按钮...");
            clicker.click(confirmX, confirmY, new AutoClicker.Callback() {
                @Override
                public void onSuccess(String message) {
                    notifyStatus("签到完成！");
                    notifyComplete("自动签到成功完成");
                }

                @Override
                public void onError(String error) {
                    notifyError("确认按钮点击失败: " + error);
                }
            });
        }, 2000);
    }

    public void clickAt(int x, int y) {
        if (!clicker.isRootAvailable()) {
            notifyError("未获取Root权限");
            return;
        }

        clicker.click(x, y, new AutoClicker.Callback() {
            @Override
            public void onSuccess(String message) {
                notifyStatus("点击成功: (" + x + ", " + y + ")");
            }

            @Override
            public void onError(String error) {
                notifyError("点击失败: " + error);
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
