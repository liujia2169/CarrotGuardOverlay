package com.codex.carrotguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
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
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "carrot_guard_overlay";
    private static final int NOTIFICATION_ID = 42;

    private final Object frameLock = new Object();
    private final SuggestionEngine suggestionEngine = new SuggestionEngine();
    private final MapAnalyzer mapAnalyzer = new MapAnalyzer();
    private MapGuideLibrary mapGuideLibrary;

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
        mapGuideLibrary = new MapGuideLibrary(this);
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

        Button saveButton = new Button(this);
        saveButton.setText("保存截图");
        saveButton.setAllCaps(false);
        saveButton.setOnClickListener(v -> saveLatestFrame());
        overlayView.addView(saveButton);

        Button analyzeButton = new Button(this);
        analyzeButton.setText("分析地图");
        analyzeButton.setAllCaps(false);
        analyzeButton.setOnClickListener(v -> analyzeLatestFrame());
        overlayView.addView(analyzeButton);

        Button matchButton = new Button(this);
        matchButton.setText("匹配攻略");
        matchButton.setAllCaps(false);
        matchButton.setOnClickListener(v -> matchGuide());
        overlayView.addView(matchButton);

        Button signatureButton = new Button(this);
        signatureButton.setText("导出攻略 JSON");
        signatureButton.setAllCaps(false);
        signatureButton.setOnClickListener(v -> exportSignature());
        overlayView.addView(signatureButton);

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
        hintView.setText("屏幕分析已启动。\n打开游戏后可保存截图或匹配攻略。");
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

    private void saveLatestFrame() {
        processLatestFrame("carrot_guard_", false);
    }

    private void analyzeLatestFrame() {
        processLatestFrame("carrot_analysis_", true);
    }

    private void matchGuide() {
        processLatestFrameForGuide(false);
    }

    private void exportSignature() {
        processLatestFrameForGuide(true);
    }

    private void processLatestFrame(String prefix, boolean analyzeMap) {
        Handler handler = captureHandler;
        if (handler == null) {
            hintView.setText("屏幕录制未运行。\n请回到 App 重新启动。");
            return;
        }

        Bitmap frame;
        synchronized (frameLock) {
            frame = latestFrame == null ? null : latestFrame.copy(Bitmap.Config.ARGB_8888, false);
        }
        if (frame == null) {
            hintView.setText("还没有画面。\n请先启动录屏并打开游戏。");
            return;
        }

        handler.post(() -> {
            Bitmap outputBitmap = frame;
            try {
                String summary = "截图已保存。";
                if (analyzeMap) {
                    MapAnalyzer.AnalysisResult result = mapAnalyzer.analyze(frame);
                    outputBitmap = result.annotatedBitmap;
                    summary = result.summary;
                }

                String name = prefix + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
                saveBitmapToPictures(outputBitmap, name);
                String message = summary + "\n保存位置：Pictures/CarrotGuardOverlay/" + name;
                hintView.post(() -> hintView.setText(message));
            } catch (Exception e) {
                hintView.post(() -> hintView.setText("保存失败：\n" + e.getMessage()));
            } finally {
                frame.recycle();
                if (outputBitmap != frame) {
                    outputBitmap.recycle();
                }
            }
        });
    }

    private void processLatestFrameForGuide(boolean exportSignature) {
        Handler handler = captureHandler;
        if (handler == null) {
            hintView.setText("屏幕录制未运行。\n请回到 App 重新启动。");
            return;
        }

        Bitmap frame;
        synchronized (frameLock) {
            frame = latestFrame == null ? null : latestFrame.copy(Bitmap.Config.ARGB_8888, false);
        }
        if (frame == null) {
            hintView.setText("还没有画面。\n请先启动录屏并打开游戏。");
            return;
        }

        handler.post(() -> {
            try {
                if (exportSignature) {
                    String guideJson = mapGuideLibrary.guideTemplateJson(frame);
                    String name = "carrot_guide_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
                    saveTextToDocuments(guideJson, name);
                    hintView.post(() -> hintView.setText("攻略 JSON 已导出：\nDocuments/CarrotGuardOverlay/" + name));
                } else {
                    MapGuideLibrary.MatchResult result = mapGuideLibrary.match(frame);
                    hintView.post(() -> hintView.setText(result.message));
                }
            } catch (Exception e) {
                hintView.post(() -> hintView.setText("攻略操作失败：\n" + e.getMessage()));
            } finally {
                frame.recycle();
            }
        });
    }

    private void saveBitmapToPictures(Bitmap bitmap, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CarrotGuardOverlay");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("系统媒体库写入失败");
            }
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IllegalStateException("无法写入 PNG 图片");
                }
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            return;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CarrotGuardOverlay");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录 " + dir.getAbsolutePath());
        }
        File file = new File(dir, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("无法写入 PNG 图片");
            }
        }
    }

    private void saveTextToDocuments(String text, String name) throws Exception {
        byte[] bytes = text.getBytes("UTF-8");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, name);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/CarrotGuardOverlay");
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) {
                throw new IllegalStateException("系统媒体库写入失败");
            }
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IllegalStateException("无法打开输出流");
                }
                output.write(bytes);
            }
            values.clear();
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            return;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CarrotGuardOverlay");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录 " + dir.getAbsolutePath());
        }
        File file = new File(dir, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
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
