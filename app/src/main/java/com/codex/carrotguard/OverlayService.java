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
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;

public class OverlayService extends Service {
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";
    private static final String CH = "carrot";
    private static final int NID = 42;

    private final Object lock = new Object();
    private final GameCheater cheater = new GameCheater();
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView hint;
    private WindowManager.LayoutParams params;
    private MediaProjection proj;
    private ImageReader reader;
    private VirtualDisplay vd;
    private HandlerThread thread;
    private Handler handler;
    private Bitmap frame;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NID, buildNotif());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addOverlay();
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (i != null && i.hasExtra(EXTRA_DATA)) {
            startProj(i.getIntExtra(EXTRA_CODE, 0), i.getParcelableExtra(EXTRA_DATA));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopProj();
        if (overlay != null) wm.removeView(overlay);
        super.onDestroy();
    }

    private void addOverlay() {
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(10), dp(12), dp(10));
        overlay.setBackgroundColor(0xDD1B5E20);

        TextView title = new TextView(this);
        title.setText("萝卜助手");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        overlay.addView(title);

        hint = new TextView(this);
        hint.setText("等待授权...");
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(13);
        hint.setPadding(0, dp(4), 0, dp(6));
        overlay.addView(hint);

        Button coins = new Button(this);
        coins.setText("修改金币");
        coins.setAllCaps(false);
        coins.setOnClickListener(v -> modifyCoins());
        overlay.addView(coins);

        Button diamonds = new Button(this);
        diamonds.setText("修改钻石");
        diamonds.setAllCaps(false);
        diamonds.setOnClickListener(v -> modifyDiamonds());
        overlay.addView(diamonds);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(260), WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(96);

        title.setOnTouchListener(new Touch());
        wm.addView(overlay, params);
    }

    private void modifyCoins() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("修改金币");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(10), dp(20), dp(10));
        TextView cl = new TextView(this);
        cl.setText("当前金币:");
        l.addView(cl);
        EditText ci = new EditText(this);
        ci.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        l.addView(ci);
        TextView nl = new TextView(this);
        nl.setText("修改为:");
        l.addView(nl);
        EditText ni = new EditText(this);
        ni.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ni.setText("999999");
        l.addView(ni);
        b.setView(l);
        b.setPositiveButton("修改", (d, w) -> {
            try {
                int c = Integer.parseInt(ci.getText().toString());
                int n = Integer.parseInt(ni.getText().toString());
                cheater.setCallback(cb());
                cheater.modifyCoins(c, n);
            } catch (NumberFormatException e) { hint.setText("请输入数字"); }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private void modifyDiamonds() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("修改钻石");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(10), dp(20), dp(10));
        TextView cl = new TextView(this);
        cl.setText("当前钻石:");
        l.addView(cl);
        EditText ci = new EditText(this);
        ci.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        l.addView(ci);
        TextView nl = new TextView(this);
        nl.setText("修改为:");
        l.addView(nl);
        EditText ni = new EditText(this);
        ni.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ni.setText("99999");
        l.addView(ni);
        b.setView(l);
        b.setPositiveButton("修改", (d, w) -> {
            try {
                int c = Integer.parseInt(ci.getText().toString());
                int n = Integer.parseInt(ni.getText().toString());
                cheater.setCallback(cb());
                cheater.modifyDiamonds(c, n);
            } catch (NumberFormatException e) { hint.setText("请输入数字"); }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private GameCheater.Callback cb() {
        return new GameCheater.Callback() {
            public void onStatus(String s) { hint.post(() -> hint.setText(s)); }
            public void onDone(String s) { hint.post(() -> hint.setText(s)); }
            public void onErr(String s) { hint.post(() -> hint.setText("错误:" + s)); }
        };
    }

    private void startProj(int code, Intent data) {
        stopProj();
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        thread = new HandlerThread("Cap");
        thread.start();
        handler = new Handler(thread.getLooper());
        proj = m.getMediaProjection(code, data);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        int d = getResources().getDisplayMetrics().densityDpi;
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        vd = proj.createVirtualDisplay("CG", w, h, d, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
        reader.setOnImageAvailableListener(r -> onFrame(r, w), handler);
        hint.setText("已启动");
    }

    private void onFrame(ImageReader r, int w) {
        Image img = r.acquireLatestImage();
        if (img == null) return;
        try {
            Image.Plane p = img.getPlanes()[0];
            ByteBuffer buf = p.getBuffer();
            int ps = p.getPixelStride(), rs = p.getRowStride();
            int bw = w + (rs - ps * w) / ps;
            Bitmap raw = Bitmap.createBitmap(bw, img.getHeight(), Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(buf);
            Bitmap vis = Bitmap.createBitmap(raw, 0, 0, w, img.getHeight());
            raw.recycle();
            synchronized (lock) {
                if (frame != null) frame.recycle();
                frame = vis.copy(Bitmap.Config.ARGB_8888, false);
            }
            vis.recycle();
        } finally { img.close(); }
    }

    private void stopProj() {
        if (vd != null) { vd.release(); vd = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (proj != null) { proj.stop(); proj = null; }
        if (thread != null) { thread.quitSafely(); thread = null; }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "萝卜助手", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        return b.setContentTitle("运行中").setSmallIcon(android.R.drawable.ic_menu_view).build();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private class Touch implements View.OnTouchListener {
        int sx, sy; float dx, dy;
        public boolean onTouch(android.view.View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: sx=params.x; sy=params.y; dx=e.getRawX(); dy=e.getRawY(); return true;
                case MotionEvent.ACTION_MOVE: params.x=sx+(int)(e.getRawX()-dx); params.y=sy+(int)(e.getRawY()-dy); wm.updateViewLayout(overlay,params); return true;
                default: return true;
            }
        }
    }
}
