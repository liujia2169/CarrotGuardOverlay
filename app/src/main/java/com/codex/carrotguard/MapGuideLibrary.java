package com.codex.carrotguard;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MapGuideLibrary {
    private static final int SIGNATURE_SIZE = 8;
    public static final int MATCH_THRESHOLD = 34;

    private final Context context;

    public MapGuideLibrary(Context context) {
        this.context = context.getApplicationContext();
    }

    public MatchResult match(Bitmap bitmap) {
        int[] currentSignature = createSignature(bitmap);
        List<MapGuide> guides = loadGuides();
        List<GuideCandidate> candidates = new ArrayList<>();

        for (MapGuide guide : guides) {
            if (!guide.hasSignature()) {
                continue;
            }
            int distance = distance(currentSignature, guide.signature);
            candidates.add(new GuideCandidate(guide, distance));
        }

        if (candidates.isEmpty()) {
            return MatchResult.noMatch("没有找到可用攻略特征。\n请先在 App 中导入攻略 JSON。");
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.score));

        GuideCandidate best = candidates.get(0);
        if (best.score > MATCH_THRESHOLD) {
            return MatchResult.noCloseMatch(candidates);
        }
        return MatchResult.matched(candidates);
    }

    public int[] createSignature(Bitmap bitmap) {
        int[] signature = new int[SIGNATURE_SIZE * SIGNATURE_SIZE];
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width / 20;
        int right = width * 19 / 20;
        int top = height / 8;
        int bottom = height * 7 / 8;
        int cellWidth = Math.max(1, (right - left) / SIGNATURE_SIZE);
        int cellHeight = Math.max(1, (bottom - top) / SIGNATURE_SIZE);

        int index = 0;
        for (int row = 0; row < SIGNATURE_SIZE; row++) {
            for (int col = 0; col < SIGNATURE_SIZE; col++) {
                int x0 = left + col * cellWidth;
                int y0 = top + row * cellHeight;
                signature[index++] = sampleLuma(bitmap, x0, y0, cellWidth, cellHeight);
            }
        }
        return signature;
    }

    public String signatureAsJsonArray(Bitmap bitmap) {
        int[] signature = createSignature(bitmap);
        return intArrayAsJson(signature);
    }

    public String guideTemplateJson(Bitmap bitmap) {
        String signature = signatureAsJsonArray(bitmap);
        return "{\n"
            + "  \"id\": \"my_stage_" + System.currentTimeMillis() + "\",\n"
            + "  \"name\": \"我的关卡攻略\",\n"
            + "  \"season\": \"自定义\",\n"
            + "  \"note\": \"由导出攻略 JSON 功能生成。\",\n"
            + "  \"signature\": " + signature + ",\n"
            + "  \"tips\": [\n"
            + "    \"编辑这条提示：在关键拐角附近放范围伤害炮塔。\",\n"
            + "    \"编辑这条提示：优先清理能打开中央塔位的障碍。\",\n"
            + "    \"编辑这条提示：优先升级覆盖多段路线的炮塔。\"\n"
            + "  ]\n"
            + "}\n";
    }

    private String intArrayAsJson(int[] signature) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < signature.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(signature[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private int sampleLuma(Bitmap bitmap, int x0, int y0, int cellWidth, int cellHeight) {
        int samples = 0;
        int total = 0;
        int stepX = Math.max(1, cellWidth / 4);
        int stepY = Math.max(1, cellHeight / 4);
        int xMax = Math.min(bitmap.getWidth(), x0 + cellWidth);
        int yMax = Math.min(bitmap.getHeight(), y0 + cellHeight);

        for (int y = y0 + stepY / 2; y < yMax; y += stepY) {
            for (int x = x0 + stepX / 2; x < xMax; x += stepX) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                total += (r * 299 + g * 587 + b * 114) / 1000;
                samples++;
            }
        }
        return samples == 0 ? 0 : total / samples;
    }

    private int distance(int[] a, int[] b) {
        int count = Math.min(a.length, b.length);
        if (count == 0) {
            return Integer.MAX_VALUE;
        }
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += Math.abs(a[i] - b[i]);
        }
        return total / count;
    }

    private List<MapGuide> loadGuides() {
        List<MapGuide> guides = new ArrayList<>();
        loadAssetGuides(guides);
        loadImportedGuides(guides);
        return guides;
    }

    private void loadAssetGuides(List<MapGuide> guides) {
        try {
            AssetManager assets = context.getAssets();
            String[] files = assets.list("map_guides");
            if (files == null) {
                return;
            }
            for (String file : files) {
                if (file.endsWith(".json")) {
                    guides.add(readGuideFromText(readAsset("map_guides/" + file), "asset:" + file));
                }
            }
        } catch (Exception ignored) {
            // Keep the app usable even when one guide file is malformed.
        }
    }

    private void loadImportedGuides(List<MapGuide> guides) {
        File dir = getImportedGuidesDir(context);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                continue;
            }
            try {
                guides.add(readGuideFromText(readFile(file), "file:" + file.getName()));
            } catch (Exception ignored) {
                // Ignore malformed user guide files and keep matching available.
            }
        }
    }

    public MapGuide readImportedGuide(File file) throws Exception {
        return readGuideFromText(readFile(file), "file:" + file.getName());
    }

    public static String validateGuide(MapGuide guide) {
        StringBuilder builder = new StringBuilder();
        builder.append("名称：").append(emptyFallback(guide.name, "（缺失）"));
        builder.append("\nID：").append(emptyFallback(guide.id, "（缺失）"));
        if (guide.season != null && !guide.season.isEmpty()) {
            builder.append("\n主题：").append(guide.season);
        }
        if (guide.note != null && !guide.note.isEmpty()) {
            builder.append("\n备注：").append(guide.note);
        }
        int signatureLength = guide.signature == null ? 0 : guide.signature.length;
        builder.append("\n特征：").append(signatureLength).append(" 个数值");
        builder.append(signatureLength == SIGNATURE_SIZE * SIGNATURE_SIZE ? "（正常）" : "（应为 64）");
        builder.append("\n提示数量：").append(guide.tips.size());
        return builder.toString();
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private MapGuide readGuideFromText(String text, String fallbackId) throws Exception {
        JSONObject json = new JSONObject(text);
        MapGuide guide = new MapGuide();
        guide.id = json.optString("id", fallbackId);
        guide.name = json.optString("name", guide.id);
        guide.season = json.optString("season", "");
        guide.note = json.optString("note", "");

        JSONArray signatureJson = json.optJSONArray("signature");
        if (signatureJson != null) {
            guide.signature = new int[signatureJson.length()];
            for (int i = 0; i < signatureJson.length(); i++) {
                guide.signature[i] = signatureJson.optInt(i);
            }
        }

        JSONArray tipsJson = json.optJSONArray("tips");
        if (tipsJson != null) {
            for (int i = 0; i < tipsJson.length(); i++) {
                guide.tips.add(tipsJson.optString(i));
            }
        }
        return guide;
    }

    private String readAsset(String path) throws Exception {
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            return readStream(input, output);
        }
    }

    private String readFile(File file) throws Exception {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            return readStream(input, output);
        }
    }

    private String readStream(InputStream input, ByteArrayOutputStream output) throws Exception {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static File getImportedGuidesDir(Context context) {
        File dir = new File(context.getFilesDir(), "map_guides");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static class MatchResult {
        public final boolean matched;
        public final MapGuide guide;
        public final int score;
        public final String message;
        public final List<GuideCandidate> candidates;

        private MatchResult(boolean matched, MapGuide guide, int score, String message, List<GuideCandidate> candidates) {
            this.matched = matched;
            this.guide = guide;
            this.score = score;
            this.message = message;
            this.candidates = candidates;
        }

        static MatchResult matched(List<GuideCandidate> candidates) {
            GuideCandidate best = candidates.get(0);
            StringBuilder builder = new StringBuilder();
            builder.append("匹配到攻略：").append(best.guide.name).append("\n分数：").append(best.score);
            int maxTips = Math.min(3, best.guide.tips.size());
            for (int i = 0; i < maxTips; i++) {
                builder.append("\n").append(i + 1).append(". ").append(best.guide.tips.get(i));
            }
            appendTopCandidates(builder, candidates);
            return new MatchResult(true, best.guide, best.score, builder.toString(), candidates);
        }

        static MatchResult noCloseMatch(List<GuideCandidate> candidates) {
            StringBuilder builder = new StringBuilder("没有足够接近的攻略匹配。");
            appendTopCandidates(builder, candidates);
            GuideCandidate best = candidates.get(0);
            return new MatchResult(false, best.guide, best.score, builder.toString(), candidates);
        }

        static MatchResult noMatch(String message) {
            return new MatchResult(false, null, -1, message, new ArrayList<>());
        }

        private static void appendTopCandidates(StringBuilder builder, List<GuideCandidate> candidates) {
            builder.append("\n\n最接近的候选：");
            int max = Math.min(3, candidates.size());
            for (int i = 0; i < max; i++) {
                GuideCandidate candidate = candidates.get(i);
                builder.append("\n")
                    .append(i + 1)
                    .append(". ")
                    .append(candidate.guide.name)
                    .append(" 分数=")
                    .append(candidate.score);
            }
        }
    }

    public static class GuideCandidate {
        public final MapGuide guide;
        public final int score;

        GuideCandidate(MapGuide guide, int score) {
            this.guide = guide;
            this.score = score;
        }
    }
}
