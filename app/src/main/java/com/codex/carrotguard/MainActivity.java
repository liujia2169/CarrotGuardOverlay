package com.codex.carrotguard;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 100;
    private static final int REQ_CAPTURE = 101;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        Button btn = findViewById(R.id.start);
        btn.setOnClickListener(v -> start());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void start() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY) {
            updateStatus();
        } else if (req == REQ_CAPTURE && res == RESULT_OK && data != null) {
            Intent i = new Intent(this, OverlayService.class);
            i.putExtra(OverlayService.EXTRA_CODE, res);
            i.putExtra(OverlayService.EXTRA_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            status.setText("运行中");
        }
    }

    private void updateStatus() {
        if (Settings.canDrawOverlays(this)) {
            status.setText("已授权，点击启动");
        } else {
            status.setText("需要悬浮窗权限");
        }
    }
}
