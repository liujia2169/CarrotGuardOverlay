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
        String focus = stats.brightRatio > 0.32f ? "障碍和空地较多，先清能覆盖路线的障碍" : "画面较密集，优先补强已有火力";
        String position = stats.centerActivity > stats.edgeActivity ? "中路/拐角附近" : "入口和出口附近";

        step = (step + 1) % 4;
        switch (step) {
            case 0:
                return "建议：" + position + "先放范围塔\n" + focus;
            case 1:
                return "升级优先级：覆盖多段路线的塔 > 减速塔 > 单体塔\n金币不足时先升核心位置";
            case 2:
                return "清障顺序：能打开新塔位的障碍优先\n避免在短直线路段堆太多单体塔";
            default:
                return "观察重点：怪物转弯处和长路线重叠处\n这些位置通常收益最高";
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
