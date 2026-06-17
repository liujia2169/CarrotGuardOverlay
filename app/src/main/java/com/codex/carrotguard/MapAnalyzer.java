package com.codex.carrotguard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

public class MapAnalyzer {
    private static final int GRID_COLUMNS = 12;
    private static final int GRID_ROWS = 18;

    public AnalysisResult analyze(Bitmap source) {
        Bitmap annotated = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(annotated);

        int width = annotated.getWidth();
        int height = annotated.getHeight();
        Rect playArea = new Rect(width / 20, height / 8, width * 19 / 20, height * 7 / 8);

        Paint playAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playAreaPaint.setStyle(Paint.Style.STROKE);
        playAreaPaint.setStrokeWidth(4);
        playAreaPaint.setColor(Color.argb(210, 255, 255, 255));
        canvas.drawRect(playArea, playAreaPaint);

        Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        routePaint.setStyle(Paint.Style.FILL);
        routePaint.setColor(Color.argb(95, 0, 160, 255));

        Paint towerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        towerPaint.setStyle(Paint.Style.STROKE);
        towerPaint.setStrokeWidth(3);
        towerPaint.setColor(Color.argb(220, 50, 255, 90));

        Paint blockerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blockerPaint.setStyle(Paint.Style.STROKE);
        blockerPaint.setStrokeWidth(3);
        blockerPaint.setColor(Color.argb(220, 255, 190, 0));

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(Math.max(22, width / 42));
        labelPaint.setShadowLayer(4, 1, 1, Color.BLACK);

        int cellWidth = Math.max(1, playArea.width() / GRID_COLUMNS);
        int cellHeight = Math.max(1, playArea.height() / GRID_ROWS);
        int routeCells = 0;
        int towerCandidates = 0;
        int blockers = 0;

        List<TowerPosition> routePositions = new ArrayList<>();
        List<TowerPosition> towerSlotPositions = new ArrayList<>();
        List<TowerPosition> blockerPositions = new ArrayList<>();

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                Rect cell = new Rect(
                    playArea.left + col * cellWidth,
                    playArea.top + row * cellHeight,
                    playArea.left + (col + 1) * cellWidth,
                    playArea.top + (row + 1) * cellHeight
                );
                CellStats stats = sampleCell(source, cell);

                if (isRouteLike(stats)) {
                    canvas.drawRect(cell, routePaint);
                    routeCells++;
                    routePositions.add(new TowerPosition(row, col, cell.centerX(), cell.centerY()));
                } else if (isTowerSlotLike(stats)) {
                    canvas.drawRect(inset(cell, 4), towerPaint);
                    towerCandidates++;
                    towerSlotPositions.add(new TowerPosition(row, col, cell.centerX(), cell.centerY()));
                } else if (isBlockerLike(stats)) {
                    canvas.drawRect(inset(cell, 5), blockerPaint);
                    blockers++;
                    blockerPositions.add(new TowerPosition(row, col, cell.centerX(), cell.centerY()));
                }
            }
        }

        Paint legendBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendBg.setColor(Color.argb(170, 0, 0, 0));
        Rect legend = new Rect(playArea.left, Math.max(0, playArea.top - 96), playArea.right, playArea.top - 8);
        canvas.drawRect(legend, legendBg);
        canvas.drawText("蓝色: 疑似路线  绿色: 疑似塔位  黄色: 疑似障碍", legend.left + 12, legend.top + 34, labelPaint);
        canvas.drawText("路线=" + routeCells + " 塔位=" + towerCandidates + " 障碍=" + blockers, legend.left + 12, legend.top + 70, labelPaint);

        String summary = "地图分析完成。\n路线格：" + routeCells + "  塔位：" + towerCandidates + "  障碍：" + blockers;
        return new AnalysisResult(annotated, summary, routePositions, towerSlotPositions, blockerPositions);
    }

    private CellStats sampleCell(Bitmap bitmap, Rect cell) {
        int stepX = Math.max(1, cell.width() / 6);
        int stepY = Math.max(1, cell.height() / 6);
        int samples = 0;
        int lumaSum = 0;
        int saturationSum = 0;
        int greenDominant = 0;
        int brownDominant = 0;

        for (int y = cell.top + stepY / 2; y < cell.bottom; y += stepY) {
            for (int x = cell.left + stepX / 2; x < cell.right; x += stepX) {
                int color = bitmap.getPixel(clamp(x, 0, bitmap.getWidth() - 1), clamp(y, 0, bitmap.getHeight() - 1));
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int luma = (r * 299 + g * 587 + b * 114) / 1000;

                samples++;
                lumaSum += luma;
                saturationSum += max - min;
                if (g > r + 18 && g > b + 18) {
                    greenDominant++;
                }
                if (r > 95 && g > 55 && b < 95 && r > b + 30) {
                    brownDominant++;
                }
            }
        }

        CellStats stats = new CellStats();
        if (samples == 0) {
            return stats;
        }
        stats.luma = lumaSum / samples;
        stats.saturation = saturationSum / samples;
        stats.greenRatio = greenDominant / (float) samples;
        stats.brownRatio = brownDominant / (float) samples;
        return stats;
    }

    private boolean isRouteLike(CellStats stats) {
        return stats.brownRatio > 0.35f && stats.luma > 70 && stats.luma < 190;
    }

    private boolean isTowerSlotLike(CellStats stats) {
        return stats.greenRatio > 0.28f && stats.saturation > 28 && stats.luma > 55;
    }

    private boolean isBlockerLike(CellStats stats) {
        return stats.saturation > 58 && stats.luma > 105 && stats.brownRatio < 0.25f && stats.greenRatio < 0.35f;
    }

    private Rect inset(Rect rect, int amount) {
        return new Rect(rect.left + amount, rect.top + amount, rect.right - amount, rect.bottom - amount);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static class TowerPosition {
        public final int row;
        public final int col;
        public final int x;
        public final int y;

        public TowerPosition(int row, int col, int x, int y) {
            this.row = row;
            this.col = col;
            this.x = x;
            this.y = y;
        }
    }

    public static class AnalysisResult {
        public final Bitmap annotatedBitmap;
        public final String summary;
        public final List<TowerPosition> routePositions;
        public final List<TowerPosition> towerSlotPositions;
        public final List<TowerPosition> blockerPositions;

        AnalysisResult(Bitmap annotatedBitmap, String summary, 
                      List<TowerPosition> routePositions, 
                      List<TowerPosition> towerSlotPositions,
                      List<TowerPosition> blockerPositions) {
            this.annotatedBitmap = annotatedBitmap;
            this.summary = summary;
            this.routePositions = routePositions;
            this.towerSlotPositions = towerSlotPositions;
            this.blockerPositions = blockerPositions;
        }
    }

    private static class CellStats {
        int luma;
        int saturation;
        float greenRatio;
        float brownRatio;
    }
}
