package com.codex.carrotguard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 1001;
    private static final int REQUEST_OVERLAY = 1002;
    private static final int REQUEST_NOTIFICATIONS = 1003;

    private TextView statusView;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(createContentView());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("萝卜战术提示器");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        TextView desc = new TextView(this);
        desc.setText("悬浮窗会读取当前屏幕画面，只显示策略建议，不自动点击、不修改游戏数据。");
        desc.setTextSize(15);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(12), 0, dp(20));
        root.addView(desc, fullWidth());

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, 0, 0, dp(20));
        root.addView(statusView, fullWidth());

        Button overlayButton = new Button(this);
        overlayButton.setText("1. 开启悬浮窗权限");
        overlayButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlayButton, fullWidth());

        Button captureButton = new Button(this);
        captureButton.setText("2. 启动屏幕分析悬浮窗");
        captureButton.setOnClickListener(v -> startCapture());
        root.addView(captureButton, fullWidth());

        Button stopButton = new Button(this);
        stopButton.setText("停止悬浮窗");
        stopButton.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stopButton, fullWidth());

        return root;
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            refreshStatus();
            return;
        }
        Intent intent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())
        );
        startActivityForResult(intent, REQUEST_OVERLAY);
    }

    private void startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, REQUEST_NOTIFICATIONS);
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, OverlayService.class);
            service.putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(OverlayService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            finish();
        }
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        String overlay = Settings.canDrawOverlays(this) ? "已开启" : "未开启";
        statusView.setText("悬浮窗权限：" + overlay + "\n屏幕录制权限：启动时由系统弹窗授权");
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
