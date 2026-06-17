package com.codex.carrotguard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 1001;
    private static final int REQUEST_IMPORT_GUIDE = 1002;
    private static final int REQUEST_NOTIFICATIONS = 1003;
    private static final int REQUEST_STORAGE = 1004;

    private TextView statusView;
    private MediaProjectionManager projectionManager;
    private MapGuideLibrary guideLibrary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        guideLibrary = new MapGuideLibrary(this);
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
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        TextView desc = new TextView(this);
        desc.setText(R.string.main_description);
        desc.setTextSize(15);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(12), 0, dp(20));
        root.addView(desc, fullWidth());

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, 0, 0, dp(20));
        root.addView(statusView, fullWidth());

        Button overlayButton = new Button(this);
        overlayButton.setText(R.string.enable_overlay);
        overlayButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlayButton, fullWidth());

        Button captureButton = new Button(this);
        captureButton.setText(R.string.start_overlay);
        captureButton.setOnClickListener(v -> startCapture());
        root.addView(captureButton, fullWidth());

        Button stopButton = new Button(this);
        stopButton.setText(R.string.stop_overlay);
        stopButton.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stopButton, fullWidth());

        Button importButton = new Button(this);
        importButton.setText(R.string.import_guide_json);
        importButton.setOnClickListener(v -> importGuideJson());
        root.addView(importButton, fullWidth());

        Button manageButton = new Button(this);
        manageButton.setText(R.string.manage_guides);
        manageButton.setOnClickListener(v -> startActivity(new Intent(this, GuideManagerActivity.class)));
        root.addView(manageButton, fullWidth());

        Button diagnosticsButton = new Button(this);
        diagnosticsButton.setText(R.string.diagnostics);
        diagnosticsButton.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticActivity.class)));
        root.addView(diagnosticsButton, fullWidth());

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
        startActivity(intent);
    }

    private void startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, REQUEST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= 28) {
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE);
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    private void importGuideJson() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "application/json",
            "text/json",
            "text/plain",
            "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_IMPORT_GUIDE);
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
        } else if (requestCode == REQUEST_IMPORT_GUIDE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importGuideFromUri(data.getData());
        }
    }

    private void importGuideFromUri(Uri uri) {
        try {
            String fileName = sanitizeFileName(readDisplayName(uri));
            if (!fileName.endsWith(".json")) {
                fileName = fileName + ".json";
            }
            File target = new File(MapGuideLibrary.getImportedGuidesDir(this), fileName);
            try (InputStream input = getContentResolver().openInputStream(uri);
                OutputStream output = new FileOutputStream(target)) {
                if (input == null) {
                    throw new IllegalStateException("无法打开选择的文件");
                }
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            MapGuide guide = guideLibrary.readImportedGuide(target);
            int signatureLength = guide.signature == null ? 0 : guide.signature.length;
            if (signatureLength != 64) {
                target.delete();
                throw new IllegalArgumentException("攻略特征必须包含 64 个数值，当前为：" + signatureLength);
            }
            if (guide.tips.isEmpty()) {
                target.delete();
                throw new IllegalArgumentException("攻略至少需要 1 条提示。");
            }
            statusView.setText("已导入攻略：\n" + guide.name + "\n文件：" + target.getName() + "\n现在启动悬浮窗，点击“匹配攻略”。");
        } catch (Exception e) {
            statusView.setText("导入失败：\n" + e.getMessage());
        }
    }

    private String readDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to a timestamped name.
        }
        return "guide_" + System.currentTimeMillis() + ".json";
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        String overlay = Settings.canDrawOverlays(this) ? "已开启" : "未开启";
        statusView.setText("悬浮窗权限：" + overlay + "\n屏幕录制权限：启动时会弹出系统授权窗口");
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
