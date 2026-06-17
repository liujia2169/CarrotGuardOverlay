package com.codex.carrotguard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public class MapAnalyzer {
    private static final int COLS = 12, ROWS = 18;

    public static class Pos {
        public final int row, col, x, y;
        public Pos(int r, int c, int x, int y) { row=r; col=c; this.x=x; this.y=y; }
    }

    public static class Result {
        public final Bitmap bitmap;
        public final String summary;
        public final List<Pos> routes, towers, blockers;
        public Result(Bitmap b, String s, List<Pos> r, List<Pos> t, List<Pos> bl) {
            bitmap=b; summary=s; routes=r; towers=t; blockers=bl;
        }
    }

    public Result analyze(Bitmap src) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        int w = out.getWidth(), h = out.getHeight();
        Rect area = new Rect(w/20, h/8, w*19/20, h*7/8);
        int cw = area.width()/COLS, ch = area.height()/ROWS;
        List<Pos> routes = new ArrayList<>(), towers = new ArrayList<>(), blockers = new ArrayList<>();
        Paint rp = makePaint(95,0,160,255), tp = makePaint(220,50,255,90), bp = makePaint(220,255,190,0);
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                Rect cell = new Rect(area.left+col*cw, area.top+r*ch, area.left+(col+1)*cw, area.top+(r+1)*ch);
                int[] stats = sample(src, cell);
                if (isRoute(stats)) { c.drawRect(cell, rp); routes.add(new Pos(r,col,cell.centerX(),cell.centerY())); }
                else if (isTower(stats)) { c.drawRect(shrink(cell,4), tp); towers.add(new Pos(r,col,cell.centerX(),cell.centerY())); }
                else if (isBlocker(stats)) { c.drawRect(shrink(cell,5), bp); blockers.add(new Pos(r,col,cell.centerX(),cell.centerY())); }
            }
        }
        return new Result(out, "路线:"+routes.size()+" 塔位:"+towers.size()+" 障碍:"+blockers.size(), routes, towers, blockers);
    }

    private Paint makePaint(int a, int r, int g, int b) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(a,r,g,b));
        return p;
    }

    private Rect shrink(Rect r, int d) { return new Rect(r.left+d, r.top+d, r.right-d, r.bottom-d); }

    private int[] sample(Bitmap b, Rect cell) {
        int sx = Math.max(1, cell.width()/6), sy = Math.max(1, cell.height()/6);
        int n=0, lum=0, sat=0, gr=0, br=0;
        for (int y=cell.top+sy/2; y<cell.bottom; y+=sy) {
            for (int x=cell.left+sx/2; x<cell.right; x+=sx) {
                int c = b.getPixel(Math.max(0,Math.min(x,b.getWidth()-1)), Math.max(0,Math.min(y,b.getHeight()-1)));
                int r=Color.red(c), g=Color.green(c), bl=Color.blue(c);
                int mx=Math.max(r,Math.max(g,bl)), mn=Math.min(r,Math.min(g,bl));
                int l=(r*299+g*587+bl*114)/1000;
                n++; lum+=l; sat+=mx-mn;
                if (g>r+18 && g>bl+18) gr++;
                if (r>95 && g>55 && bl<95 && r>bl+30) br++;
            }
        }
        return new int[]{n==0?0:lum/n, n==0?0:sat/n, gr, br};
    }

    private boolean isRoute(int[] s) { return s[3]>0.35f*s[0] && s[0]>70 && s[0]<190; }
    private boolean isTower(int[] s) { return s[2]>0.28f*s[0] && s[1]>28 && s[0]>55; }
    private boolean isBlocker(int[] s) { return s[1]>58 && s[0]>105 && s[3]<0.25f*s[0] && s[2]<0.35f*s[0]; }
}
