package com.codex.carrotguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

public class DiagnosticActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 2001;

    private TextView outputView;
    private MapGuideLibrary guideLibrary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        guideLibrary = new MapGuideLibrary(this);
        setContentView(createContentView());
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.diagnostics);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        Button pickButton = new Button(this);
        pickButton.setText(R.string.pick_screenshot);
        pickButton.setOnClickListener(v -> pickScreenshot());
        root.addView(pickButton, fullWidth());

        Button closeButton = new Button(this);
        closeButton.setText(R.string.back);
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton, fullWidth());

        outputView = new TextView(this);
        outputView.setText("选择一张已保存的截图或分析图，用来查看特征数组和攻略匹配候选。");
        outputView.setTextSize(14);
        outputView.setPadding(0, dp(16), 0, 0);
        root.addView(outputView, fullWidth());

        return scrollView;
    }

    private void pickScreenshot() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            analyzeImage(data.getData());
        }
    }

    private void analyzeImage(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IllegalStateException("无法读取选择的图片");
            }
            String signature = guideLibrary.signatureAsJsonArray(bitmap);
            MapGuideLibrary.MatchResult match = guideLibrary.match(bitmap);
            bitmap.recycle();

            StringBuilder builder = new StringBuilder();
            builder.append("匹配阈值：").append(MapGuideLibrary.MATCH_THRESHOLD).append("（分数越低越像）\n\n");
            builder.append(match.message).append("\n\n");
            builder.append("当前截图特征：\n").append(signature);
            outputView.setText(builder.toString());
        } catch (Exception e) {
            outputView.setText("诊断失败：\n" + e.getMessage());
        }
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
