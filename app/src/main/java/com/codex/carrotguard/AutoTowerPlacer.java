package com.codex.carrotguard;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoTowerPlacer {
    private final MapAnalyzer analyzer = new MapAnalyzer();
    private final AutoClicker clicker = new AutoClicker();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onStatus(String s);
        void onPlaced(int x, int y, String reason);
        void onDone(String s);
        void onErr(String s);
    }

    public void place(Bitmap screen, Callback cb) {
        if (!clicker.hasRoot()) { cb.onErr("无Root"); return; }
        new Thread(() -> {
            try {
                cb.onStatus("分析地图...");
                MapAnalyzer.Result r = analyzer.analyze(screen);
                if (r.towers.isEmpty()) { cb.onErr("无塔位"); return; }
                if (r.routes.isEmpty()) { cb.onErr("无路线"); return; }
                List<Scored> scored = score(r.towers, r.routes);
                Collections.sort(scored, (a,b)->Integer.compare(b.score,a.score));
                Scored best = scored.get(0);
                cb.onStatus("放塔:"+best.pos.x+","+best.pos.y+" 原因:"+best.reason);
                clicker.click(best.pos.x, best.pos.y, new AutoClicker.Callback() {
                    public void onSuccess(String m) { cb.onPlaced(best.pos.x, best.pos.y, best.reason); cb.onDone("完成"); }
                    public void onError(String e) { cb.onErr(e); }
                });
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    private List<Scored> score(List<MapAnalyzer.Pos> towers, List<MapAnalyzer.Pos> routes) {
        List<Scored> list = new ArrayList<>();
        for (MapAnalyzer.Pos t : towers) {
            int s=0; StringBuilder reason=new StringBuilder();
            int adj=countAdj(t,routes); s+=adj*20;
            if (adj>0) reason.append("相邻路线:").append(adj).append(" ");
            if (isCorner(t,routes)) { s+=50; reason.append("拐角 "); }
            if (t.row<=3||t.row>=15) { s+=25; reason.append("入口 "); }
            if (Math.abs(t.row-9)<=3&&Math.abs(t.col-6)<=3) { s+=15; reason.append("中心 "); }
            if (reason.length()==0) reason.append("默认");
            list.add(new Scored(t,s,reason.toString()));
        }
        return list;
    }

    private int countAdj(MapAnalyzer.Pos t, List<MapAnalyzer.Pos> routes) {
        int c=0;
        for (MapAnalyzer.Pos r : routes) if (Math.abs(t.row-r.row)<=1&&Math.abs(t.col-r.col)<=1) c++;
        return c;
    }

    private boolean isCorner(MapAnalyzer.Pos t, List<MapAnalyzer.Pos> routes) {
        for (MapAnalyzer.Pos r : routes) {
            if (Math.abs(t.row-r.row)<=2&&Math.abs(t.col-r.col)<=2) {
                boolean up=false,down=false,left=false,right=false;
                for (MapAnalyzer.Pos p : routes) {
                    if (p.row==r.row-1&&p.col==r.col) up=true;
                    if (p.row==r.row+1&&p.col==r.col) down=true;
                    if (p.row==r.row&&p.col==r.col-1) left=true;
                    if (p.row==r.row&&p.col==r.col+1) right=true;
                }
                if ((up&&right)||(up&&left)||(down&&right)||(down&&left)) return true;
            }
        }
        return false;
    }

    private static class Scored {
        MapAnalyzer.Pos pos; int score; String reason;
        Scored(MapAnalyzer.Pos p, int s, String r) { pos=p; score=s; reason=r; }
    }
}
