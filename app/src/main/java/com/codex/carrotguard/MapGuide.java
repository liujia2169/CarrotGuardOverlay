package com.codex.carrotguard;

import java.util.ArrayList;
import java.util.List;

public class MapGuide {
    public String id;
    public String name;
    public String season;
    public String note;
    public int[] signature;
    public final List<String> tips = new ArrayList<>();

    public boolean hasSignature() {
        return signature != null && signature.length > 0;
    }
}
