package com.codex.carrotguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class GuideManagerActivity extends Activity {
    private LinearLayout listView;
    private TextView summaryView;
    private MapGuideLibrary guideLibrary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        guideLibrary = new MapGuideLibrary(this);
        setContentView(createContentView());
        refreshGuides();
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.guide_manager);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        summaryView = new TextView(this);
        summaryView.setTextSize(14);
        summaryView.setPadding(0, dp(10), 0, dp(10));
        root.addView(summaryView, fullWidth());

        Button refreshButton = new Button(this);
        refreshButton.setText(R.string.refresh);
        refreshButton.setOnClickListener(v -> refreshGuides());
        root.addView(refreshButton, fullWidth());

        Button closeButton = new Button(this);
        closeButton.setText(R.string.back);
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton, fullWidth());

        listView = new LinearLayout(this);
        listView.setOrientation(LinearLayout.VERTICAL);
        listView.setPadding(0, dp(12), 0, 0);
        root.addView(listView, fullWidth());

        return scrollView;
    }

    private void refreshGuides() {
        if (listView == null) {
            return;
        }
        listView.removeAllViews();

        File dir = MapGuideLibrary.getImportedGuidesDir(this);
        File[] files = dir.listFiles((file) -> file.isFile() && file.getName().endsWith(".json"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        summaryView.setText("已导入攻略：" + files.length + "\n位置：App 私有目录 / map_guides");
        if (files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有导入攻略。\n请回主界面点击“导入攻略 JSON”。");
            empty.setTextSize(15);
            empty.setPadding(0, dp(20), 0, dp(20));
            listView.addView(empty, fullWidth());
            return;
        }

        for (File file : files) {
            listView.addView(createGuideRow(file));
        }
    }

    private LinearLayout createGuideRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(0xFFEFEFEF);

        TextView name = new TextView(this);
        name.setText(file.getName());
        name.setTextSize(16);
        row.addView(name, fullWidth());

        TextView meta = new TextView(this);
        meta.setText(buildGuideSummary(file));
        meta.setTextSize(13);
        row.addView(meta, fullWidth());

        Button detailsButton = new Button(this);
        detailsButton.setText(R.string.details);
        detailsButton.setOnClickListener(v -> showDetails(file));
        row.addView(detailsButton, fullWidth());

        Button deleteButton = new Button(this);
        deleteButton.setText(R.string.delete);
        deleteButton.setOnClickListener(v -> confirmDelete(file));
        row.addView(deleteButton, fullWidth());

        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, dp(6), 0, dp(6));
        row.setLayoutParams(params);
        return row;
    }

    private String buildGuideSummary(File file) {
        try {
            MapGuide guide = guideLibrary.readImportedGuide(file);
            int signatureLength = guide.signature == null ? 0 : guide.signature.length;
            return String.format(
                Locale.US,
                "%.1f KB | 提示=%d | 特征=%d%s",
                file.length() / 1024f,
                guide.tips.size(),
                signatureLength,
                signatureLength == 64 ? "" : " 应为 64"
            );
        } catch (Exception e) {
            return String.format(Locale.US, "%.1f KB | JSON 无效", file.length() / 1024f);
        }
    }

    private void showDetails(File file) {
        try {
            MapGuide guide = guideLibrary.readImportedGuide(file);
            StringBuilder body = new StringBuilder(MapGuideLibrary.validateGuide(guide));
            if (!guide.tips.isEmpty()) {
                body.append("\n\n攻略提示：");
                for (int i = 0; i < guide.tips.size(); i++) {
                    body.append("\n").append(i + 1).append(". ").append(guide.tips.get(i));
                }
            }
            new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(body.toString())
                .setPositiveButton(R.string.ok, null)
                .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setTitle("攻略无效")
                .setMessage(file.getName() + "\n\n" + e.getMessage())
                .setPositiveButton(R.string.ok, null)
                .show();
        }
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
            .setTitle("删除攻略？")
            .setMessage(file.getName())
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                file.delete();
                refreshGuides();
            })
            .show();
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
