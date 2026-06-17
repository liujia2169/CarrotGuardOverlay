package com.codex.carrotguard;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class OverlayService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "carrot_guard_overlay";
    private static final int NOTIFICATION_ID = 42;

    private final Object frameLock = new Object();
    private final SuggestionEngine suggestionEngine = new SuggestionEngine();
    private final MapAnalyzer mapAnalyzer = new MapAnalyzer();
    private AutoSignService autoSignService;
    private GameCheater gameCheater;
    private AutoTowerPlacer autoTowerPlacer;

    private WindowManager windowManager;
    private LinearLayout overlayView;
    private TextView hintView;
    private WindowManager.LayoutParams overlayParams;

    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private Bitmap latestFrame;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        autoSignService = new AutoSignService();
        gameCheater = new GameCheater();
        autoTowerPlacer = new AutoTowerPlacer();
        addOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_RESULT_DATA)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            startProjection(resultCode, resultData);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopProjection();
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        recycleLatestFrame();
        super.onDestroy();
    }

    private void addOverlay() {
        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.VERTICAL);
        overlayView.setPadding(dp(12), dp(10), dp(12), dp(10));
        overlayView.setBackgroundColor(0xDD1B5E20);

        TextView title = new TextView(this);
        title.setText("萝卜助手");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        overlayView.addView(title);

        hintView = new TextView(this);
        hintView.setText("等待屏幕授权...");
        hintView.setTextColor(0xFFFFFFFF);
        hintView.setTextSize(13);
        hintView.setPadding(0, dp(4), 0, dp(6));
        overlayView.addView(hintView);

        Button autoSignButton = new Button(this);
        autoSignButton.setText("自动签到");
        autoSignButton.setAllCaps(false);
        autoSignButton.setOnClickListener(v -> startAutoSign());
        overlayView.addView(autoSignButton);

        Button modifyCoinsButton = new Button(this);
        modifyCoinsButton.setText("修改金币");
        modifyCoinsButton.setAllCaps(false);
        modifyCoinsButton.setOnClickListener(v -> modifyCoins());
        overlayView.addView(modifyCoinsButton);

        Button modifyDiamondsButton = new Button(this);
        modifyDiamondsButton.setText("修改钻石");
        modifyDiamondsButton.setAllCaps(false);
        modifyDiamondsButton.setOnClickListener(v -> modifyDiamonds());
        overlayView.addView(modifyDiamondsButton);

        Button autoTowerButton = new Button(this);
        autoTowerButton.setText("智能放塔");
        autoTowerButton.setAllCaps(false);
        autoTowerButton.setOnClickListener(v -> startAutoTowerPlace());
        overlayView.addView(autoTowerButton);

        Button continuousTowerButton = new Button(this);
        continuousTowerButton.setText("连续放塔");
        continuousTowerButton.setAllCaps(false);
        continuousTowerButton.setOnClickListener(v -> startContinuousTowerPlace());
        overlayView.addView(continuousTowerButton);

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        overlayParams = new WindowManager.LayoutParams(
            dp(260),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = dp(12);
        overlayParams.y = dp(96);

        title.setOnTouchListener(new DragTouchListener());
        windowManager.addView(overlayView, overlayParams);
    }

    private void startProjection(int resultCode, Intent resultData) {
        stopProjection();
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        captureThread = new HandlerThread("CarrotCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopProjection(false);
                if (hintView != null) {
                    hintView.post(() -> hintView.setText("屏幕录制已停止。\n回到 App 可重新启动。"));
                }
            }
        };
        mediaProjection.registerCallback(projectionCallback, captureHandler);

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int density = getResources().getDisplayMetrics().densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "CarrotGuardScreen",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null,
            captureHandler
        );
        imageReader.setOnImageAvailableListener(reader -> analyzeLatestFrame(reader, width), captureHandler);
        hintView.setText("屏幕分析已启动。\n打开游戏后可使用自动功能。");
    }

    private void analyzeLatestFrame(ImageReader reader, int width) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            int bitmapWidth = width + rowPadding / pixelStride;

            Bitmap rawBitmap = Bitmap.createBitmap(bitmapWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            rawBitmap.copyPixelsFromBuffer(buffer);
            Bitmap visibleBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, image.getHeight());
            rawBitmap.recycle();

            String suggestion = suggestionEngine.suggest(visibleBitmap, width, image.getHeight());
            rememberFrame(visibleBitmap);

            if (suggestion != null) {
                hintView.post(() -> hintView.setText(suggestion));
            }
        } finally {
            image.close();
        }
    }

    private void rememberFrame(Bitmap bitmap) {
        synchronized (frameLock) {
            if (latestFrame != null) {
                latestFrame.recycle();
            }
            latestFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        bitmap.recycle();
    }

    private void startAutoSign() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("自动签到配置");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));
        
        TextView signLabel = new TextView(this);
        signLabel.setText("签到按钮位置（百分比）：");
        layout.addView(signLabel);
        
        LinearLayout signLayout = new LinearLayout(this);
        signLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView signXLabel = new TextView(this);
        signXLabel.setText("X%:");
        signLayout.addView(signXLabel);
        
        EditText signXInput = new EditText(this);
        signXInput.setText("85");
        signXInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        signLayout.addView(signXInput);
        
        TextView signYLabel = new TextView(this);
        signYLabel.setText("Y%:");
        signLayout.addView(signYLabel);
        
        EditText signYInput = new EditText(this);
        signYInput.setText("15");
        signYInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        signLayout.addView(signYInput);
        
        layout.addView(signLayout);
        
        TextView confirmLabel = new TextView(this);
        confirmLabel.setText("确认按钮位置（百分比）：");
        layout.addView(confirmLabel);
        
        LinearLayout confirmLayout = new LinearLayout(this);
        confirmLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView confirmXLabel = new TextView(this);
        confirmXLabel.setText("X%:");
        confirmLayout.addView(confirmXLabel);
        
        EditText confirmXInput = new EditText(this);
        confirmXInput.setText("50");
        confirmXInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        confirmLayout.addView(confirmXInput);
        
        TextView confirmYLabel = new TextView(this);
        confirmYLabel.setText("Y%:");
        confirmLayout.addView(confirmYLabel);
        
        EditText confirmYInput = new EditText(this);
        confirmYInput.setText("60");
        confirmYInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        confirmLayout.addView(confirmYInput);
        
        layout.addView(confirmLayout);
        
        builder.setView(layout);
        
        builder.setPositiveButton("开始签到", (dialog, which) -> {
            try {
                int signX = Integer.parseInt(signXInput.getText().toString());
                int signY = Integer.parseInt(signYInput.getText().toString());
                int confirmX = Integer.parseInt(confirmXInput.getText().toString());
                int confirmY = Integer.parseInt(confirmYInput.getText().toString());
                executeAutoSign(signX, signY, confirmX, confirmY);
            } catch (NumberFormatException e) {
                hintView.post(() -> hintView.setText("请输入有效数字"));
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeAutoSign(int signX, int signY, int confirmX, int confirmY) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        autoSignService.setScreenSize(width, height);
        autoSignService.setSignButtonPosition(signX, signY);
        autoSignService.setConfirmButtonPosition(confirmX, confirmY);

        autoSignService.startAutoSign(new AutoSignService.AutoSignCallback() {
            @Override
            public void onStatusUpdate(String status) {
                hintView.post(() -> hintView.setText(status));
            }

            @Override
            public void onComplete(String message) {
                hintView.post(() -> hintView.setText(message));
            }

            @Override
            public void onError(String error) {
                hintView.post(() -> hintView.setText("错误: " + error));
            }
        });
    }

    private void modifyCoins() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改金币");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));
        
        TextView currentLabel = new TextView(this);
        currentLabel.setText("当前金币数：");
        layout.addView(currentLabel);
        
        EditText currentInput = new EditText(this);
        currentInput.setHint("输入当前金币数");
        currentInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(currentInput);
        
        TextView newLabel = new TextView(this);
        newLabel.setText("修改为：");
        layout.addView(newLabel);
        
        EditText newInput = new EditText(this);
        newInput.setHint("输入目标金币数");
        newInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        newInput.setText("999999");
        layout.addView(newInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("修改", (dialog, which) -> {
            try {
                int current = Integer.parseInt(currentInput.getText().toString());
                int newVal = Integer.parseInt(newInput.getText().toString());
                executeModifyCoins(current, newVal);
            } catch (NumberFormatException e) {
                hintView.post(() -> hintView.setText("请输入有效数字"));
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeModifyCoins(int currentCoins, int newCoins) {
        gameCheater.setCallback(new GameCheater.CheatCallback() {
            @Override
            public void onStatusUpdate(String status) {
                hintView.post(() -> hintView.setText(status));
            }

            @Override
            public void onComplete(String message) {
                hintView.post(() -> hintView.setText(message));
            }

            @Override
            public void onError(String error) {
                hintView.post(() -> hintView.setText("错误: " + error));
            }
        });

        gameCheater.modifyGoldCoins(currentCoins, newCoins);
    }

    private void modifyDiamonds() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改钻石");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));
        
        TextView currentLabel = new TextView(this);
        currentLabel.setText("当前钻石数：");
        layout.addView(currentLabel);
        
        EditText currentInput = new EditText(this);
        currentInput.setHint("输入当前钻石数");
        currentInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(currentInput);
        
        TextView newLabel = new TextView(this);
        newLabel.setText("修改为：");
        layout.addView(newLabel);
        
        EditText newInput = new EditText(this);
        newInput.setHint("输入目标钻石数");
        newInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        newInput.setText("99999");
        layout.addView(newInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("修改", (dialog, which) -> {
            try {
                int current = Integer.parseInt(currentInput.getText().toString());
                int newVal = Integer.parseInt(newInput.getText().toString());
                executeModifyDiamonds(current, newVal);
            } catch (NumberFormatException e) {
                hintView.post(() -> hintView.setText("请输入有效数字"));
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeModifyDiamonds(int currentDiamonds, int newDiamonds) {
        gameCheater.setCallback(new GameCheater.CheatCallback() {
            @Override
            public void onStatusUpdate(String status) {
                hintView.post(() -> hintView.setText(status));
            }

            @Override
            public void onComplete(String message) {
                hintView.post(() -> hintView.setText(message));
            }

            @Override
            public void onError(String error) {
                hintView.post(() -> hintView.setText("错误: " + error));
            }
        });

        gameCheater.modifyDiamonds(currentDiamonds, newDiamonds);
    }

    private void startAutoTowerPlace() {
        Bitmap frame;
        synchronized (frameLock) {
            frame = latestFrame == null ? null : latestFrame.copy(Bitmap.Config.ARGB_8888, false);
        }
        
        if (frame == null) {
            hintView.setText("还没有画面。\n请先启动录屏并打开游戏。");
            return;
        }

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        autoTowerPlacer.setScreenSize(width, height);

        autoTowerPlacer.startAutoPlace(frame, new AutoTowerPlacer.TowerPlaceCallback() {
            @Override
            public void onStatusUpdate(String status) {
                hintView.post(() -> hintView.setText(status));
            }

            @Override
            public void onTowerPlaced(int x, int y, String reason) {
                hintView.post(() -> hintView.setText("已放塔: (" + x + ", " + y + ")\n原因: " + reason));
            }

            @Override
            public void onComplete(String message) {
                hintView.post(() -> hintView.setText(message));
            }

            @Override
            public void onError(String error) {
                hintView.post(() -> hintView.setText("错误: " + error));
            }
        });
    }

    private void startContinuousTowerPlace() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("连续放塔配置");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));
        
        TextView countLabel = new TextView(this);
        countLabel.setText("放塔数量：");
        layout.addView(countLabel);
        
        EditText countInput = new EditText(this);
        countInput.setHint("输入放塔数量");
        countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        countInput.setText("5");
        layout.addView(countInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("开始", (dialog, which) -> {
            try {
                int count = Integer.parseInt(countInput.getText().toString());
                executeContinuousTowerPlace(count);
            } catch (NumberFormatException e) {
                hintView.post(() -> hintView.setText("请输入有效数字"));
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeContinuousTowerPlace(int count) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        autoTowerPlacer.setScreenSize(width, height);

        autoTowerPlacer.startContinuousPlace(count, new AutoTowerPlacer.ContinuousPlaceCallback() {
            @Override
            public void onProgress(String status) {
                hintView.post(() -> hintView.setText(status));
            }

            @Override
            public void onTowerPlaced(int x, int y, String reason) {
                hintView.post(() -> hintView.setText("已放塔: (" + x + ", " + y + ")\n原因: " + reason));
            }

            @Override
            public void onComplete(String message) {
                hintView.post(() -> hintView.setText(message));
            }

            @Override
            public void onError(String error) {
                hintView.post(() -> hintView.setText("错误: " + error));
            }

            @Override
            public Bitmap onRequestScreenshot() {
                synchronized (frameLock) {
                    return latestFrame == null ? null : latestFrame.copy(Bitmap.Config.ARGB_8888, false);
                }
            }
        });
    }

    private void stopProjection() {
        stopProjection(true);
    }

    private void stopProjection(boolean stopMediaProjection) {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            if (projectionCallback != null) {
                mediaProjection.unregisterCallback(projectionCallback);
                projectionCallback = null;
            }
            if (stopMediaProjection) {
                mediaProjection.stop();
            }
            mediaProjection = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
    }

    private void recycleLatestFrame() {
        synchronized (frameLock) {
            if (latestFrame != null) {
                latestFrame.recycle();
                latestFrame = null;
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder
            .setContentTitle("萝卜战术助手运行中")
            .setContentText("正在读取屏幕并显示策略提示")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "萝卜战术助手",
            NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class DragTouchListener implements android.view.View.OnTouchListener {
        private int startX;
        private int startY;
        private float downX;
        private float downY;

        @Override
        public boolean onTouch(android.view.View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = overlayParams.x;
                    startY = overlayParams.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    overlayParams.x = startX + (int) (event.getRawX() - downX);
                    overlayParams.y = startY + (int) (event.getRawY() - downY);
                    windowManager.updateViewLayout(overlayView, overlayParams);
                    return true;
                default:
                    return true;
            }
        }
    }
}
