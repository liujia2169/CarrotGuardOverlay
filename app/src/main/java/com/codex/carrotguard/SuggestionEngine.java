package com.codex.carrotguard;

import android.graphics.Bitmap;
import android.graphics.Color;

public class SuggestionEngine {
    private long lastUpdateAt;
    private int step;

    public String suggest(Bitmap bitmap, int visibleWidth, int visibleHeight) {
        long now = System.currentTimeMillis();
        if (now - lastUpdateAt < 1200) {
            return null;
        }
        lastUpdateAt = now;

        ScreenStats stats = readStats(bitmap, visibleWidth, visibleHeight);
        String focus = stats.brightRatio > 0.32f
            ? "空地和障碍较多，优先清理能打开关键塔位的障碍。"
            : "画面较密集，优先升级已经覆盖路线的核心炮塔。";
        String position = stats.centerActivity > stats.edgeActivity
            ? "中路拐角"
            : "入口和出口附近";

        step = (step + 1) % 4;
        switch (step) {
            case 0:
                return "建议：在" + position + "附近放范围伤害炮塔。\n" + focus;
            case 1:
                return "升级优先级：覆盖多段路线 > 减速/控制 > 单体输出。\n金币优先留给核心位置。";
            case 2:
                return "清障优先级：先清能打开新塔位的障碍。\n短直线路段不要堆太多单体输出。";
            default:
                return "重点观察拐角和路线重叠区域。\n这些位置通常收益最高。";
        }
    }

    private ScreenStats readStats(Bitmap bitmap, int width, int height) {
        int left = width / 10;
        int right = width * 9 / 10;
        int top = height / 5;
        int bottom = height * 4 / 5;

        int samples = 0;
        int bright = 0;
        int centerActive = 0;
        int edgeActive = 0;
        int stepX = Math.max(1, width / 80);
        int stepY = Math.max(1, height / 80);

        for (int y = top; y < bottom; y += stepY) {
            for (int x = left; x < right; x += stepX) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                boolean active = max - min > 40 || luma > 150;

                samples++;
                if (luma > 160) {
                    bright++;
                }
                if (x > width / 3 && x < width * 2 / 3 && y > height / 3 && y < height * 2 / 3) {
                    if (active) {
                        centerActive++;
                    }
                } else if (active) {
                    edgeActive++;
                }
            }
        }

        ScreenStats stats = new ScreenStats();
        stats.brightRatio = samples == 0 ? 0 : bright / (float) samples;
        stats.centerActivity = centerActive;
        stats.edgeActivity = edgeActive;
        return stats;
    }

    private static class ScreenStats {
        float brightRatio;
        int centerActivity;
        int edgeActivity;
    }
}
