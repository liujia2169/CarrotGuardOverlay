package com.codex.carrotguard;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AutoTowerPlacer {
    private static final String TAG = "AutoTowerPlacer";
    private static final String GAME_PACKAGE = "com.feiyu.luobo4.nearme.gamecenter";
    
    private final MapAnalyzer mapAnalyzer;
    private final AutoClicker clicker;
    private final Handler handler;
    private TowerPlaceCallback callback;
    private boolean isRunning = false;
    private int screenWidth;
    private int screenHeight;
    private List<MapAnalyzer.TowerPosition> placedPositions = new ArrayList<>();

    public interface TowerPlaceCallback {
        void onStatusUpdate(String status);
        void onTowerPlaced(int x, int y, String reason);
        void onComplete(String message);
        void onError(String error);
    }

    public AutoTowerPlacer() {
        this.mapAnalyzer = new MapAnalyzer();
        this.clicker = new AutoClicker();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void startAutoPlace(Bitmap screenBitmap, TowerPlaceCallback callback) {
        if (isRunning) {
            callback.onError("自动放塔程序已在运行");
            return;
        }

        if (!clicker.isRootAvailable()) {
            callback.onError("未获取Root权限");
            return;
        }

        this.callback = callback;
        this.isRunning = true;

        new Thread(() -> {
            try {
                analyzeAndPlace(screenBitmap);
            } catch (Exception e) {
                Log.e(TAG, "Auto place failed", e);
                notifyError("自动放塔失败: " + e.getMessage());
            } finally {
                isRunning = false;
            }
        }).start();
    }

    public void stopAutoPlace() {
        isRunning = false;
    }

    public void clearPlacedPositions() {
        placedPositions.clear();
    }

    private void analyzeAndPlace(Bitmap screenBitmap) {
        notifyStatus("正在分析地图...");
        
        MapAnalyzer.AnalysisResult result = mapAnalyzer.analyze(screenBitmap);
        notifyStatus("地图分析完成，正在寻找最优位置...");
        
        List<MapAnalyzer.TowerPosition> towerSlots = result.towerSlotPositions;
        List<MapAnalyzer.TowerPosition> routes = result.routePositions;
        
        if (towerSlots.isEmpty()) {
            notifyError("未找到可放塔位置");
            return;
        }

        if (routes.isEmpty()) {
            notifyError("未找到路线");
            return;
        }

        notifyStatus("找到 " + towerSlots.size() + " 个可放塔位置，" + routes.size() + " 个路线格子");
        
        List<ScoredPosition> scoredPositions = scorePositions(towerSlots, routes);
        Collections.sort(scoredPositions, (a, b) -> Integer.compare(b.score, a.score));
        
        if (scoredPositions.isEmpty()) {
            notifyError("无法计算最优位置");
            return;
        }

        ScoredPosition bestPosition = scoredPositions.get(0);
        notifyStatus("最优位置: (" + bestPosition.position.x + ", " + bestPosition.position.y + ") 评分: " + bestPosition.score);
        notifyStatus("原因: " + bestPosition.reason);
        
        clicker.click(bestPosition.position.x, bestPosition.position.y, new AutoClicker.Callback() {
            @Override
            public void onSuccess(String message) {
                placedPositions.add(bestPosition.position);
                notifyTowerPlaced(bestPosition.position.x, bestPosition.position.y, bestPosition.reason);
                notifyComplete("自动放塔完成");
            }

            @Override
            public void onError(String error) {
                notifyError("点击失败: " + error);
            }
        });
    }

    public void startContinuousPlace(int count, ContinuousPlaceCallback callback) {
        if (isRunning) {
            callback.onError("自动放塔程序已在运行");
            return;
        }

        if (!clicker.isRootAvailable()) {
            callback.onError("未获取Root权限");
            return;
        }

        this.isRunning = true;
        placedPositions.clear();

        new Thread(() -> {
            try {
                for (int i = 0; i < count && isRunning; i++) {
                    callback.onProgress("正在放第 " + (i + 1) + " 个塔...");
                    
                    Bitmap screenBitmap = callback.onRequestScreenshot();
                    if (screenBitmap == null) {
                        callback.onError("无法获取屏幕截图");
                        break;
                    }
                    
                    boolean placed = placeSingleTower(screenBitmap, callback);
                    screenBitmap.recycle();
                    
                    if (!placed) {
                        callback.onError("放塔失败");
                        break;
                    }
                    
                    if (i < count - 1) {
                        Thread.sleep(2000);
                    }
                }
                
                if (isRunning) {
                    callback.onComplete("连续放塔完成，共放了 " + placedPositions.size() + " 个塔");
                }
            } catch (Exception e) {
                Log.e(TAG, "Continuous place failed", e);
                callback.onError("连续放塔失败: " + e.getMessage());
            } finally {
                isRunning = false;
            }
        }).start();
    }

    private boolean placeSingleTower(Bitmap screenBitmap, ContinuousPlaceCallback callback) {
        MapAnalyzer.AnalysisResult result = mapAnalyzer.analyze(screenBitmap);
        
        List<MapAnalyzer.TowerPosition> towerSlots = result.towerSlotPositions;
        List<MapAnalyzer.TowerPosition> routes = result.routePositions;
        
        if (towerSlots.isEmpty() || routes.isEmpty()) {
            return false;
        }

        List<ScoredPosition> scoredPositions = scorePositions(towerSlots, routes);
        Collections.sort(scoredPositions, (a, b) -> Integer.compare(b.score, a.score));
        
        if (scoredPositions.isEmpty()) {
            return false;
        }

        ScoredPosition bestPosition = scoredPositions.get(0);
        
        clicker.click(bestPosition.position.x, bestPosition.position.y, new AutoClicker.Callback() {
            @Override
            public void onSuccess(String message) {
                placedPositions.add(bestPosition.position);
                callback.onTowerPlaced(bestPosition.position.x, bestPosition.position.y, bestPosition.reason);
            }

            @Override
            public void onError(String error) {
                callback.onError("点击失败: " + error);
            }
        });
        
        return true;
    }

    public interface ContinuousPlaceCallback {
        void onProgress(String status);
        void onTowerPlaced(int x, int y, String reason);
        void onComplete(String message);
        void onError(String error);
        Bitmap onRequestScreenshot();
    }

    private List<ScoredPosition> scorePositions(List<MapAnalyzer.TowerPosition> towerSlots, 
                                                List<MapAnalyzer.TowerPosition> routes) {
        List<ScoredPosition> scoredPositions = new ArrayList<>();
        
        for (MapAnalyzer.TowerPosition slot : towerSlots) {
            int score = 0;
            StringBuilder reason = new StringBuilder();
            
            int adjacentRoutes = countAdjacentRoutes(slot, routes);
            score += adjacentRoutes * 20;
            if (adjacentRoutes > 0) {
                reason.append("相邻路线:").append(adjacentRoutes).append(" ");
            }
            
            boolean nearCorner = isNearCorner(slot, routes);
            if (nearCorner) {
                score += 50;
                reason.append("靠近拐角 ");
            }
            
            boolean nearCenter = isNearCenter(slot);
            if (nearCenter) {
                score += 15;
                reason.append("靠近中心 ");
            }
            
            boolean nearEntrance = isNearEntrance(slot);
            if (nearEntrance) {
                score += 25;
                reason.append("靠近入口 ");
            }
            
            int routeDensity = calculateRouteDensity(slot, routes);
            score += routeDensity * 5;
            if (routeDensity > 3) {
                reason.append("路线密集 ");
            }
            
            if (reason.length() == 0) {
                reason.append("默认位置");
            }
            
            scoredPositions.add(new ScoredPosition(slot, score, reason.toString()));
        }
        
        return scoredPositions;
    }

    private int countAdjacentRoutes(MapAnalyzer.TowerPosition slot, List<MapAnalyzer.TowerPosition> routes) {
        int count = 0;
        for (MapAnalyzer.TowerPosition route : routes) {
            if (Math.abs(slot.row - route.row) <= 1 && Math.abs(slot.col - route.col) <= 1) {
                count++;
            }
        }
        return count;
    }

    private boolean isNearCorner(MapAnalyzer.TowerPosition slot, List<MapAnalyzer.TowerPosition> routes) {
        for (MapAnalyzer.TowerPosition route : routes) {
            if (Math.abs(slot.row - route.row) <= 2 && Math.abs(slot.col - route.col) <= 2) {
                boolean hasRouteAbove = false, hasRouteBelow = false;
                boolean hasRouteLeft = false, hasRouteRight = false;
                
                for (MapAnalyzer.TowerPosition r : routes) {
                    if (r.row == route.row - 1 && r.col == route.col) hasRouteAbove = true;
                    if (r.row == route.row + 1 && r.col == route.col) hasRouteBelow = true;
                    if (r.row == route.row && r.col == route.col - 1) hasRouteLeft = true;
                    if (r.row == route.row && r.col == route.col + 1) hasRouteRight = true;
                }
                
                if ((hasRouteAbove && hasRouteRight) || (hasRouteAbove && hasRouteLeft) ||
                    (hasRouteBelow && hasRouteRight) || (hasRouteBelow && hasRouteLeft)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearCenter(MapAnalyzer.TowerPosition slot) {
        int centerRow = 9;
        int centerCol = 6;
        return Math.abs(slot.row - centerRow) <= 3 && Math.abs(slot.col - centerCol) <= 3;
    }

    private boolean isNearEntrance(MapAnalyzer.TowerPosition slot) {
        return slot.row <= 3 || slot.row >= 15;
    }

    private int calculateRouteDensity(MapAnalyzer.TowerPosition slot, List<MapAnalyzer.TowerPosition> routes) {
        int density = 0;
        for (MapAnalyzer.TowerPosition route : routes) {
            int distance = Math.abs(slot.row - route.row) + Math.abs(slot.col - route.col);
            if (distance <= 3) {
                density++;
            }
        }
        return density;
    }

    private void notifyStatus(String status) {
        handler.post(() -> {
            if (callback != null) callback.onStatusUpdate(status);
        });
    }

    private void notifyTowerPlaced(int x, int y, String reason) {
        handler.post(() -> {
            if (callback != null) callback.onTowerPlaced(x, y, reason);
        });
    }

    private void notifyComplete(String message) {
        handler.post(() -> {
            if (callback != null) callback.onComplete(message);
        });
    }

    private void notifyError(String error) {
        handler.post(() -> {
            if (callback != null) callback.onError(error);
        });
    }

    private static class ScoredPosition {
        final MapAnalyzer.TowerPosition position;
        final int score;
        final String reason;

        ScoredPosition(MapAnalyzer.TowerPosition position, int score, String reason) {
            this.position = position;
            this.score = score;
            this.reason = reason;
        }
    }
}
